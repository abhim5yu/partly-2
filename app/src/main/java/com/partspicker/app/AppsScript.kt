package com.partspicker.app

/** Pulls the spreadsheet id out of any Google Sheets link. */
fun sheetIdOf(url: String): String? =
    Regex("/spreadsheets/(?:u/\\d+/)?d/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)

/** Pulls the tab id (gid) out of a Google Sheets link, if there is one. */
fun sheetGidOf(url: String): String? =
    Regex("[#&?]gid=(\\d+)").find(url)?.groupValues?.get(1)

/** 1 -> "A", 2 -> "B", 27 -> "AA". */
fun colLetter(n: Int): String {
    var x = n
    var s = ""
    while (x > 0) {
        val r = (x - 1) % 26
        s = ('A' + r) + s
        x = (x - 1) / 26
    }
    return s
}

/** "A" -> 1, "B" -> 2, "AA" -> 27. Anything that isn't letters -> 0. */
fun colIndex(letters: String): Int {
    var n = 0
    for (ch in letters.uppercase()) {
        if (ch !in 'A'..'Z') return 0
        n = n * 26 + (ch - 'A' + 1)
    }
    return n
}

/** The Google Apps Script the person pastes into script.google.com (with their private token filled in). */
fun appsScriptSource(token: String): String = APPS_SCRIPT.replace("__TOKEN__", token)

private val APPS_SCRIPT = """
// Parts Picker - Google Sheets bridge.
// Paste this into script.google.com, then Deploy > New deployment > Web app
// (Execute as: Me, Who has access: Anyone).
var TOKEN = '__TOKEN__';

function doGet(e) {
  var p = e.parameter, res;
  try {
    if (p.token !== TOKEN) throw new Error('Wrong token - copy the script from the app again.');
    var sh = getSheet_(p.id, p.gid);
    if (p.action === 'add') res = addRow_(sh, JSON.parse(p.cols), JSON.parse(p.vals));
    else if (p.action === 'note') res = addNote_(sh, Number(p.col), String(p.text));
    else if (p.action === 'update') res = updateRow_(sh, Number(p.row), JSON.parse(p.cols), JSON.parse(p.vals));
    else res = readAll_(sh);
    res.ok = true;
  } catch (err) {
    res = { ok: false, error: String(err && err.message ? err.message : err) };
  }
  return ContentService.createTextOutput(JSON.stringify(res)).setMimeType(ContentService.MimeType.JSON);
}

function getSheet_(id, gid) {
  var ss;
  try {
    ss = SpreadsheetApp.openById(id);
  } catch (e) {
    throw new Error('Cannot open this file. If it is an Excel (.xlsx) file, open it in Google Sheets, use File > Save as Google Sheets, then add the new link in the app.');
  }
  var sheets = ss.getSheets();
  for (var i = 0; i < sheets.length; i++) {
    if (gid && String(sheets[i].getSheetId()) === String(gid)) return sheets[i];
  }
  return sheets[0];
}

function readAll_(sh) {
  var r = sh.getLastRow(), c = sh.getLastColumn();
  return {
    title: sh.getParent().getName(),
    tab: sh.getName(),
    values: (r > 0 && c > 0) ? sh.getRange(1, 1, r, c).getDisplayValues() : []
  };
}

// First row (after the header) that is blank all the way across — not just in whichever
// columns a particular box or note cares about. That's what keeps a date/note row and the
// next batch of boxed entries from ending up sharing one row: once anything is written
// anywhere in a row, this stops treating that row as free for the next add.
function firstBlankRow_(sh) {
  var n = sh.getMaxRows();
  if (n < 2) throw new Error('The sheet has no data rows.');
  var width = Math.max(sh.getLastColumn(), 26);
  var rng = sh.getRange(2, 1, n - 1, width);
  var values = rng.getValues(), bgs = rng.getBackgrounds();
  for (var i = 0; i < values.length; i++) {
    // skip coloured separator rows (black / blue bars)
    if (bgs[i][0] !== '#ffffff') continue;
    var blank = true;
    for (var j = 0; j < values[i].length; j++) {
      if (values[i][j] !== '') { blank = false; break; }
    }
    if (blank) return i + 2;
  }
  throw new Error('No empty row left in this sheet.');
}

function addRow_(sh, cols, vals) {
  var row = firstBlankRow_(sh);
  var written = 0;
  for (var k = 0; k < cols.length; k++) {
    var s = String(vals[k] === null || vals[k] === undefined ? '' : vals[k]).trim();
    if (s === '') continue;
    var cell = sh.getRange(row, cols[k]);
    if (cell.getFormula() !== '') continue; // never overwrite formulas
    var colVals = sh.getRange(2, cols[k], sh.getMaxRows() - 1, 1).getValues();
    var asNum = null;
    for (var i = 0; i < colVals.length; i++) {
      if (colVals[i][0] !== '') { asNum = typeof colVals[i][0] === 'number'; break; }
    }
    if (asNum === null) asNum = /^[-+]?\d+(\.\d+)?${'$'}/.test(s) && !/^[-+]?0\d/.test(s);
    if (asNum && !isNaN(Number(s))) {
      cell.setValue(Number(s));
    } else {
      cell.setNumberFormat('@');
      cell.setValue(s);
    }
    written++;
  }
  return { row: row, written: written };
}

// Rewrites the boxed cells of a row that already exists (editing an entry after the fact),
// instead of finding a free row like addRow_ does. Formulas are still never touched; an
// emptied box clears that cell.
function updateRow_(sh, row, cols, vals) {
  var written = 0;
  for (var k = 0; k < cols.length; k++) {
    var s = String(vals[k] === null || vals[k] === undefined ? '' : vals[k]).trim();
    var cell = sh.getRange(row, cols[k]);
    if (cell.getFormula() !== '') continue;
    if (s === '') { cell.clearContent(); written++; continue; }
    var colVals = sh.getRange(2, cols[k], sh.getMaxRows() - 1, 1).getValues();
    var asNum = null;
    for (var i = 0; i < colVals.length; i++) {
      if (colVals[i][0] !== '') { asNum = typeof colVals[i][0] === 'number'; break; }
    }
    if (asNum === null) asNum = /^[-+]?\d+(\.\d+)?${'$'}/.test(s) && !/^[-+]?0\d/.test(s);
    if (asNum && !isNaN(Number(s))) {
      cell.setValue(Number(s));
    } else {
      cell.setNumberFormat('@');
      cell.setValue(s);
    }
    written++;
  }
  return { row: row, written: written };
}

// One value, one cell, on its own fresh row — e.g. a date written as a heading between
// batches of stock-take entries. col is 1-based (A=1, B=2, ...).
function addNote_(sh, col, text) {
  var row = firstBlankRow_(sh);
  var cell = sh.getRange(row, col);
  if (cell.getFormula() !== '') throw new Error('That cell holds a formula - pick another column.');
  cell.setNumberFormat('@');
  cell.setValue(text);
  return { row: row, written: 1 };
}
"""
