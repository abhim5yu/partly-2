package com.partspicker.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** One filled-in sheet row, reduced to the columns the input boxes use. */
private data class SheetRowView(val rowNum: Int, val cells: List<String>)

// Items before the first table row: entry card, status line, table header.
private const val TABLE_START = 3

/**
 * A Google Sheet in the app's own UI: input boxes (named by you, each linked to one sheet
 * column, arranged on an 8 x 10 grid) that add an entry to the sheet, and the latest sheet
 * contents as a table. The table button at the top opens the whole sheet.
 */
@Composable
fun SheetViewerScreen(vm: PartsViewModel, sheet: SheetLink, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val inputs = remember { mutableStateMapOf<String, String>() }
    var addTick by remember { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showFull by rememberSaveable { mutableStateOf(false) }
    val fields = remember(sheet.fields) { sheet.fields.laidOut() }
    var draft by remember { mutableStateOf(fields) }

    BackHandler(onBack = onBack)

    LaunchedEffect(sheet.id) { vm.loadSheet(sheet) }
    LaunchedEffect(addTick) {
        if (addTick > 0) {
            try { firstFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    val data = if (vm.sheetDataFor == sheet.id) vm.sheetData else null
    val headers = data?.values?.firstOrNull().orEmpty()
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
        if (idx >= 0 && !showFull) listState.animateScrollToItem(TABLE_START + idx)
    }

    val submit: () -> Unit = {
        val vals = fields.map { (inputs[it.id] ?: "").trim() }
        vm.addToSheet(sheet, vals) { ok ->
            if (ok) {
                inputs.clear()
                addTick++
            }
        }
    }

    if (showFull) {
        FullSheetScreen(vm = vm, sheet = sheet, onClose = { showFull = false })
    } else {
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
                // View the whole sheet you're working on
                IconBtn(
                    Icons.Filled.TableChart, "View sheet",
                    { showFull = true },
                    tint = c.onAccent, background = c.accent
                )
                Box(Modifier.width(8.dp))
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
                                headers = headers,
                                inputs = inputs,
                                editing = editing,
                                draft = draft,
                                onDraft = { draft = it },
                                onToggleEdit = {
                                    if (editing) {
                                        vm.updateSheetFields(
                                            sheet.id,
                                            draft.filter { it.label.isNotBlank() && it.col > 0 }.laidOut()
                                        )
                                        editing = false
                                    } else {
                                        draft = fields
                                        editing = true
                                    }
                                },
                                adding = vm.addingToSheet,
                                firstFocus = firstFocus,
                                onMoveNext = { focus.moveFocus(FocusDirection.Next) },
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
            "Set up the Google connection first: go back to the Sheets list and tap the status dot in the top right.",
            color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp
        )
        PillButton("Back to Sheets", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SheetEntryCard(
    fields: List<SheetField>,
    headers: List<String>,
    inputs: SnapshotStateMap<String, String>,
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
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    // Every sheet column the boxes can be linked to (falls back to plain letters until the sheet loads).
    val columns = if (headers.isNotEmpty()) headers else List(8) { "" }

    Column(
        Modifier
            .fillMaxWidth()
            .lift(RadiusXl)
            .clip(RadiusXl)
            .background(c.surface)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Layout · $GRID_COLS × $GRID_ROWS grid", Modifier.padding(horizontal = 4.dp))
                LayoutGridEditor(fields = draft, onChange = onDraft)
                Text(
                    "Press and hold a box to move it. Drag its corner dot to resize.",
                    Modifier.padding(horizontal = 4.dp),
                    color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )

                SectionLabel("Boxes", Modifier.padding(start = 4.dp, top = 6.dp))
                draft.forEachIndexed { i, f ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                height = 48.dp,
                                forceUppercase = false,
                                background = c.surface2,
                                horizontalPadding = 16.dp
                            )
                            // Fixed width so every row lines up in a straight column.
                            Box(
                                Modifier
                                    .width(116.dp)
                                    .height(48.dp)
                                    .clip(PillShape)
                                    .background(if (pickerFor == i) c.accent.copy(alpha = 0.18f) else c.surface2)
                                    .clickable { pickerFor = if (pickerFor == i) null else i }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (f.col > 0) {
                                        colLetter(f.col) + " · " + (headers.getOrNull(f.col - 1)?.ifBlank { null } ?: "column")
                                    } else "Link",
                                    color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                IconBtn(
                                    Icons.Filled.Close, "Remove box",
                                    {
                                        pickerFor = null
                                        onDraft(draft.filterIndexed { j, _ -> j != i })
                                    },
                                    tint = c.error
                                )
                            }
                        }
                        if (pickerFor == i) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(Radius)
                                    .background(c.surface2)
                                    .verticalScroll(rememberScrollState())
                                    .padding(6.dp)
                            ) {
                                columns.forEachIndexed { idx, h ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 44.dp)
                                            .clip(RadiusS)
                                            .background(if (f.col == idx + 1) c.accent.copy(alpha = 0.18f) else Color.Transparent)
                                            .clickable {
                                                onDraft(draft.mapIndexed { j, x -> if (j == i) x.copy(col = idx + 1) else x })
                                                pickerFor = null
                                            }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            colLetter(idx + 1) + "  ·  " + h.ifBlank { "(no title)" },
                                            color = c.text, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                PillButton(
                    "+ Add box",
                    onClick = {
                        val used = draft.map { it.col }.toSet()
                        val nextCol = (1..columns.size).firstOrNull { it !in used }
                            ?: ((draft.maxOfOrNull { it.col } ?: 0) + 1)
                        val label = headers.getOrNull(nextCol - 1)?.trim()?.ifBlank { null } ?: "Box ${draft.size + 1}"
                        onDraft((draft + SheetField(label, nextCol)).laidOut())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    container = c.surface2, content = c.text
                )
                if (headers.isNotEmpty()) {
                    PillButton(
                        "One box per sheet column",
                        onClick = {
                            pickerFor = null
                            onDraft(
                                headers.withIndex()
                                    .filter { it.value.isNotBlank() }
                                    .map { SheetField(it.value.trim(), it.index + 1) }
                                    .laidOut()
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        container = c.surface2, content = c.text
                    )
                }
                Text(
                    "Name each box and link it to a sheet column. Cells with formulas are never overwritten. Tap the tick to save.",
                    color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp
                )
            }
        } else {
            if (fields.isEmpty()) {
                Text(
                    "Loading the sheet's columns… or tap the pencil to add boxes yourself.",
                    color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            } else {
                EntryBoxes(
                    fields = fields,
                    inputs = inputs,
                    firstFocus = firstFocus,
                    onMoveNext = onMoveNext,
                    onSubmit = onSubmit
                )
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

/** The input boxes as plain pills, placed on the grid exactly as arranged. */
@Composable
private fun EntryBoxes(
    fields: List<SheetField>,
    inputs: SnapshotStateMap<String, String>,
    firstFocus: FocusRequester,
    onMoveNext: () -> Unit,
    onSubmit: () -> Unit
) {
    // Row-major so the keyboard's Next button walks left-to-right, top-to-bottom.
    val ordered = fields.sortedWith(compareBy<SheetField>({ it.gy }, { it.gx }))
    val rowsUsed = fields.maxOfOrNull { it.gy + it.gh } ?: 0
    val gap = 8.dp
    val rowH = 52.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW = (maxWidth - gap * (GRID_COLS - 1)) / GRID_COLS
        Box(Modifier.fillMaxWidth().height(rowH * rowsUsed + gap * (rowsUsed - 1).coerceAtLeast(0))) {
            ordered.forEachIndexed { i, f ->
                key(f.id) {
                    val boxH = rowH * f.gh + gap * (f.gh - 1)
                    // narrower boxes get a slightly smaller font and tighter padding so the name still fits
                    val fontSize = when {
                        f.gw <= 2 -> 13.sp
                        f.gw <= 3 -> 15.sp
                        else -> 16.sp
                    }
                    val sidePad = when {
                        f.gw <= 2 -> 10.dp
                        f.gw <= 3 -> 14.dp
                        else -> 18.dp
                    }
                    PillField(
                        value = inputs[f.id] ?: "",
                        onValueChange = { inputs[f.id] = it },
                        placeholder = f.label,
                        modifier = Modifier
                            .offset(x = (cellW + gap) * f.gx, y = (rowH + gap) * f.gy)
                            .width(cellW * f.gw + gap * (f.gw - 1)),
                        height = boxH,
                        fontSize = fontSize,
                        horizontalPadding = sidePad,
                        imeAction = if (i == ordered.lastIndex) ImeAction.Done else ImeAction.Next,
                        onImeAction = { if (i == ordered.lastIndex) onSubmit() else onMoveNext() },
                        focusRequester = if (i == 0) firstFocus else null
                    )
                }
            }
        }
    }
}

/** Pixel geometry of the layout editor's grid. */
private data class EditorMetrics(val cw: Float, val rh: Float, val g: Float) {
    fun pos(x: Int, y: Int): Offset = Offset(x * (cw + g), y * (rh + g))
}

/**
 * The grid editor (8 columns x 10 rows). Press and hold a box to move it; drag the dot in its
 * corner to resize it. Boxes snap to cells and can't overlap — a move or resize that would hit
 * another box simply doesn't go through.
 */
@Composable
private fun LayoutGridEditor(fields: List<SheetField>, onChange: (List<SheetField>) -> Unit) {
    val c = LocalAppColors.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var live by remember { mutableStateOf<List<SheetField>?>(null) }
    var activeId by remember { mutableStateOf<String?>(null) }
    val fieldsNow by rememberUpdatedState(fields)
    val onChangeNow by rememberUpdatedState(onChange)

    val shown = live ?: fields
    val rowsShown = maxOf(GRID_ROWS, shown.maxOfOrNull { it.gy + it.gh } ?: 0)
    val gap = 5.dp
    val rowH = 34.dp
    val totalH = rowH * rowsShown + gap * (rowsShown - 1)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW = (maxWidth - gap * (GRID_COLS - 1)) / GRID_COLS
        val m by rememberUpdatedState(
            EditorMetrics(
                cw = with(density) { cellW.toPx() },
                rh = with(density) { rowH.toPx() },
                g = with(density) { gap.toPx() }
            )
        )

        val commit: () -> Unit = {
            val list = live
            activeId = null
            if (list != null && list != fieldsNow) onChangeNow(list)
            live = null
        }

        Box(Modifier.fillMaxWidth().height(totalH)) {
            // the empty cells
            for (r in 0 until rowsShown) {
                for (col in 0 until GRID_COLS) {
                    Box(
                        Modifier
                            .offset(x = (cellW + gap) * col, y = (rowH + gap) * r)
                            .size(cellW, rowH)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surface2.copy(alpha = 0.7f))
                    )
                }
            }

            shown.forEach { f ->
                key(f.id) {
                    val active = activeId == f.id
                    val pos by animateOffsetAsState(
                        m.pos(f.gx, f.gy),
                        spring(stiffness = Spring.StiffnessMedium),
                        label = "editorTile"
                    )
                    val wDp = cellW * f.gw + gap * (f.gw - 1)
                    val hDp = rowH * f.gh + gap * (f.gh - 1)

                    Box(
                        Modifier
                            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                            .size(wDp, hDp)
                            .zIndex(if (active) 2f else 1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) c.accent else c.accent.copy(alpha = 0.30f))
                            .pointerInput(f.id) {
                                var origin = Offset.Zero
                                var acc = Offset.Zero
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val base = live ?: fieldsNow
                                        val cur = base.firstOrNull { b -> b.id == f.id }
                                        if (cur != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            live = base
                                            origin = m.pos(cur.gx, cur.gy)
                                            acc = Offset.Zero
                                            activeId = f.id
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val list = live
                                        if (activeId == f.id && list != null) {
                                            acc += amount
                                            val cur = list.first { b -> b.id == f.id }
                                            val p = origin + acc
                                            val maxRows = maxOf(GRID_ROWS, list.maxOf { b -> b.gy + b.gh })
                                            val nx = (p.x / (m.cw + m.g)).roundToInt().coerceIn(0, GRID_COLS - cur.gw)
                                            val ny = (p.y / (m.rh + m.g)).roundToInt().coerceIn(0, maxRows - cur.gh)
                                            if ((nx != cur.gx || ny != cur.gy) &&
                                                isFree(list, f.id, nx, ny, cur.gw, cur.gh)
                                            ) {
                                                live = list.map { b -> if (b.id == f.id) b.copy(gx = nx, gy = ny) else b }
                                            }
                                        }
                                    },
                                    onDragEnd = { commit() },
                                    onDragCancel = { commit() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            f.label,
                            Modifier.padding(horizontal = 8.dp),
                            color = if (active) c.onAccent else c.text,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        // resize handle (bottom-right corner)
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .pointerInput(f.id) {
                                    var base = Offset.Zero
                                    var acc = Offset.Zero
                                    detectDragGestures(
                                        onDragStart = {
                                            val start = live ?: fieldsNow
                                            val cur = start.firstOrNull { b -> b.id == f.id }
                                            if (cur != null) {
                                                live = start
                                                base = Offset(
                                                    cur.gw * m.cw + (cur.gw - 1) * m.g,
                                                    cur.gh * m.rh + (cur.gh - 1) * m.g
                                                )
                                                acc = Offset.Zero
                                                activeId = f.id
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val list = live
                                            if (activeId == f.id && list != null) {
                                                acc += amount
                                                val cur = list.first { b -> b.id == f.id }
                                                val maxRows = maxOf(GRID_ROWS, list.maxOf { b -> b.gy + b.gh })
                                                val nw = ((base.x + acc.x + m.g) / (m.cw + m.g)).roundToInt()
                                                    .coerceIn(1, GRID_COLS - cur.gx)
                                                val nh = ((base.y + acc.y + m.g) / (m.rh + m.g)).roundToInt()
                                                    .coerceIn(1, maxRows - cur.gy)
                                                if ((nw != cur.gw || nh != cur.gh) &&
                                                    isFree(list, f.id, cur.gx, cur.gy, nw, nh)
                                                ) {
                                                    live = list.map { b -> if (b.id == f.id) b.copy(gw = nw, gh = nh) else b }
                                                }
                                            }
                                        },
                                        onDragEnd = { commit() },
                                        onDragCancel = { commit() }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (active) c.onAccent else c.text.copy(alpha = 0.7f))
                            )
                        }
                    }
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
                    Text(
                        l.uppercase(),
                        Modifier.weight(if (i == 0) W_FIRST else 1f).padding(end = 6.dp),
                        color = c.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
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
