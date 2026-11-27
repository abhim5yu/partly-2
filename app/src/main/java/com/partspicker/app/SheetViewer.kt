package com.partspicker.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One filled-in sheet row, reduced to the columns the input boxes use. */
private data class SheetRowView(val rowNum: Int, val cells: List<String>)

// Items before the first table row: entry card, status line, table header.
private const val TABLE_START = 3

/**
 * A Google Sheet in the app's own UI: input boxes (named by you, each filling one sheet
 * column) that add an entry to the sheet, and the latest sheet contents as a table.
 */
@Composable
fun SheetViewerScreen(vm: PartsViewModel, sheet: SheetLink, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val inputs = remember { mutableStateMapOf<Int, String>() }
    var addTick by remember { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf(sheet.fields) }

    BackHandler(onBack = onBack)

    LaunchedEffect(sheet.id) { vm.loadSheet(sheet) }
    LaunchedEffect(addTick) {
        if (addTick > 0) {
            try { firstFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    val data = if (vm.sheetDataFor == sheet.id) vm.sheetData else null
    val fields = sheet.fields
    val connected = vm.scriptUrl.isNotBlank()

    val rows = remember(data, fields) {
        if (data == null) {
            emptyList()
        } else {
            data.values.drop(1).mapIndexedNotNull { i, row ->
                val cells = fields.map { f -> row.getOrNull(f.col - 1).orEmpty().trim() }
                if (cells.any { it.isNotEmpty() }) SheetRowView(i + 2, cells) else null
            }
        }
    }

    // After an entry is added, jump to the row it landed in.
    LaunchedEffect(vm.lastAddedRow, rows) {
        val idx = rows.indexOfFirst { it.rowNum == vm.lastAddedRow }
        if (idx >= 0) listState.animateScrollToItem(TABLE_START + idx)
    }

    val submit: () -> Unit = {
        val vals = fields.indices.map { (inputs[it] ?: "").trim() }
        vm.addToSheet(sheet, vals) { ok ->
            if (ok) {
                inputs.clear()
                addTick++
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tint = c.text)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Eyebrow(data?.tab?.takeIf { it.isNotBlank() } ?: "Google Sheet")
                Text(
                    sheet.name,
                    color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp, maxLines = 1
                )
            }
            IconBtn(Icons.Filled.Refresh, "Refresh", { vm.loadSheet(sheet) }, tint = c.muted, background = c.surface2)
        }
        if (vm.sheetLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surface2)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 0: entry card
            item {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 18.dp)) {
                    if (!connected) {
                        NotConnectedCard(onBack)
                    } else {
                        SheetEntryCard(
                            fields = fields,
                            inputs = inputs,
                            editing = editing,
                            draft = draft,
                            onDraft = { draft = it },
                            onToggleEdit = {
                                if (editing) {
                                    vm.updateSheetFields(sheet.id, draft.filter { it.label.isNotBlank() && it.col > 0 })
                                    inputs.clear()
                                    editing = false
                                } else {
                                    draft = sheet.fields
                                    editing = true
                                }
                            },
                            adding = vm.addingToSheet,
                            firstFocus = firstFocus,
                            onMoveNext = { focus.moveFocus(FocusDirection.Down) },
                            onSubmit = submit
                        )
                    }
                }
            }

            // 1: status + count
            item {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 10.dp)) {
                    StatusText(vm.sheetOpStatus, if (vm.sheetOpFailed) c.error else c.success)
                    if (connected && data != null) {
                        Text(
                            "${rows.size} row${if (rows.size == 1) "" else "s"} filled",
                            color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // 2: table header
            item { SheetTableHeader(fields.map { it.label }) }

            if (rows.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Box(
                            Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 18.dp, vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when {
                                    !connected -> "Not connected yet."
                                    data == null && vm.sheetLoading -> "Loading the sheet…"
                                    data == null -> "No data yet — tap refresh."
                                    else -> "Nothing filled in yet."
                                },
                                color = c.muted, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            itemsIndexed(rows, key = { _, r -> r.rowNum }) { index, r ->
                SheetRow(r, highlight = r.rowNum == vm.lastAddedRow, showDivider = index != rows.lastIndex)
            }

            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                            .background(c.surface)
                    )
                }
            }

            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun NotConnectedCard(onBack: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().lift(RadiusXl).clip(RadiusXl).background(c.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel("Not connected")
        Text(
            "Set up the Google connection first: go back to the Sheets list and follow the steps under \"Google connection\".",
            color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp
        )
        PillButton("Back to Sheets", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SheetEntryCard(
    fields: List<SheetField>,
    inputs: SnapshotStateMap<Int, String>,
    editing: Boolean,
    draft: List<SheetField>,
    onDraft: (List<SheetField>) -> Unit,
    onToggleEdit: () -> Unit,
    adding: Boolean,
    firstFocus: FocusRequester,
    onMoveNext: () -> Unit,
    onSubmit: () -> Unit
) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .lift(RadiusXl)
            .clip(RadiusXl)
            .background(c.surface)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel(if (editing) "Edit boxes" else "New entry", Modifier.padding(horizontal = 4.dp))
            IconBtn(
                if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                if (editing) "Save boxes" else "Edit boxes",
                onToggleEdit,
                tint = c.accent, background = c.surface2, size = 36.dp
            )
        }

        if (editing) {
            Column(Modifier.padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.forEachIndexed { i, f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillField(
                            value = f.label,
                            onValueChange = { v ->
                                onDraft(draft.mapIndexed { j, x -> if (j == i) x.copy(label = v) else x })
                            },
                            placeholder = "Name",
                            modifier = Modifier.weight(1f),
                            forceUppercase = false,
                            background = c.surface2,
                            horizontalPadding = 16.dp
                        )
                        PillField(
                            value = if (f.col > 0) colLetter(f.col) else "",
                            onValueChange = { v ->
                                onDraft(draft.mapIndexed { j, x -> if (j == i) x.copy(col = colIndex(v.take(2))) else x })
                            },
                            placeholder = "Col",
                            modifier = Modifier.width(72.dp),
                            lettersOnly = true,
                            centered = true,
                            background = c.surface2,
                            horizontalPadding = 0.dp
                        )
                        IconBtn(
                            Icons.Filled.Close, "Remove box",
                            { onDraft(draft.filterIndexed { j, _ -> j != i }) },
                            tint = c.error
                        )
                    }
                }
                PillButton(
                    "+ Add box",
                    onClick = {
                        onDraft(draft + SheetField("Box ${draft.size + 1}", (draft.maxOfOrNull { it.col } ?: 0) + 1))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    container = c.surface2, content = c.text
                )
                Text(
                    "Each box fills one column of the sheet (A, B, C…). Cells with formulas are never overwritten. Tap the tick to save.",
                    color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp
                )
            }
        } else {
            Column(Modifier.padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (fields.isEmpty()) {
                    Text(
                        "Loading the sheet's columns… or tap the pencil to add boxes yourself.",
                        color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
                fields.forEachIndexed { i, f ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(f.label, Modifier.padding(start = 4.dp))
                        PillField(
                            value = inputs[i] ?: "",
                            onValueChange = { inputs[i] = it },
                            placeholder = f.label,
                            modifier = Modifier.fillMaxWidth(),
                            imeAction = if (i == fields.lastIndex) ImeAction.Done else ImeAction.Next,
                            onImeAction = { if (i == fields.lastIndex) onSubmit() else onMoveNext() },
                            focusRequester = if (i == 0) firstFocus else null
                        )
                    }
                }
                if (fields.isNotEmpty()) {
                    PillButton(
                        if (adding) "Adding…" else "Add to sheet",
                        onClick = onSubmit,
                        enabled = !adding,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private const val W_FIRST = 1.4f

@Composable
private fun SheetTableHeader(labels: List<String>) {
    val c = LocalAppColors.current
    Box(Modifier.padding(horizontal = 16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(c.surface)
        ) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp)) {
                labels.forEachIndexed { i, l ->
                    SectionLabel(l, Modifier.weight(if (i == 0) W_FIRST else 1f).padding(end = 6.dp))
                }
            }
            HorizontalDivider(color = c.line)
        }
    }
}

@Composable
private fun SheetRow(row: SheetRowView, highlight: Boolean, showDivider: Boolean) {
    val c = LocalAppColors.current
    Box(Modifier.padding(horizontal = 16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .background(if (highlight) c.accent.copy(alpha = 0.16f) else Color.Transparent)
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.cells.forEachIndexed { i, v ->
                    Text(
                        if (v.isEmpty()) "—" else v,
                        Modifier.weight(if (i == 0) W_FIRST else 1f).padding(end = 6.dp),
                        color = if (v.isEmpty()) c.muted else c.text,
                        fontFamily = if (i == 0) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
            if (showDivider) HorizontalDivider(color = c.line)
        }
    }
}
