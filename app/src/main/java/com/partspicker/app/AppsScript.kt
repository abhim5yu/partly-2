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
    res = (p.action === 'add') ? addRow_(sh, JSON.parse(p.cols), JSON.parse(p.vals)) : readAll_(sh);
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

function addRow_(sh, cols, vals) {
  var n = sh.getMaxRows();
  if (n < 2) throw new Error('The sheet has no data rows.');
  var c0 = Math.min.apply(null, cols), c1 = Math.max.apply(null, cols);
  var rng = sh.getRange(2, c0, n - 1, c1 - c0 + 1);
  var values = rng.getValues(), formulas = rng.getFormulas(), bgs = rng.getBackgrounds();
  var k, i, j;
  for (i = 0; i < values.length; i++) {
    // skip coloured separator rows (black / blue bars)
    if (bgs[i][cols[0] - c0] !== '#ffffff') continue;
    var free = true;
    for (k = 0; k < cols.length; k++) {
      j = cols[k] - c0;
      if (formulas[i][j] === '' && values[i][j] !== '') { free = false; break; }
    }
    if (!free) continue;
    var row = i + 2, written = 0;
    for (k = 0; k < cols.length; k++) {
      var s = String(vals[k] === null || vals[k] === undefined ? '' : vals[k]).trim();
      j = cols[k] - c0;
      // never overwrite formulas, skip empty boxes
      if (s === '' || formulas[i][j] !== '') continue;
      var asNum = columnIsNumeric_(values, j);
      if (asNum === null) asNum = /^[-+]?\d+(\.\d+)?${'$'}/.test(s) && !/^[-+]?0\d/.test(s);
      var cell = sh.getRange(row, cols[k]);
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
  throw new Error('No empty row left in this sheet.');
}

function columnIsNumeric_(values, j) {
  for (var i = 0; i < values.length; i++) {
    if (values[i][j] !== '') return typeof values[i][j] === 'number';
  }
  return null;
}
"""
