package com.partspicker.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
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

private const val GRID_COLS = 2

/**
 * A Google Sheet in the app's own UI: input boxes (named by you, each linked to one sheet
 * column, arranged like phone widgets) that add an entry to the sheet, and the latest
 * sheet contents as a table.
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
        if (idx >= 0) listState.animateScrollToItem(TABLE_START + idx)
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
                            headers = headers,
                            inputs = inputs,
                            editing = editing,
                            draft = draft,
                            onDraft = { draft = it },
                            onToggleEdit = {
                                if (editing) {
                                    vm.updateSheetFields(sheet.id, draft.filter { it.label.isNotBlank() && it.col > 0 })
                                    editing = false
                                } else {
                                    draft = sheet.fields
                                    editing = true
                                }
                            },
                            adding = vm.addingToSheet,
                            firstFocus = firstFocus,
                            onMoveNext = { focus.moveFocus(FocusDirection.Next) },
                            onSubmit = submit,
                            onReorder = { vm.updateSheetFields(sheet.id, it) }
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
    headers: List<String>,
    inputs: SnapshotStateMap<String, String>,
    editing: Boolean,
    draft: List<SheetField>,
    onDraft: (List<SheetField>) -> Unit,
    onToggleEdit: () -> Unit,
    adding: Boolean,
    firstFocus: FocusRequester,
    onMoveNext: () -> Unit,
    onSubmit: () -> Unit,
    onReorder: (List<SheetField>) -> Unit
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Box(
                                Modifier
                                    .widthIn(max = 124.dp)
                                    .height(48.dp)
                                    .clip(PillShape)
                                    .background(if (pickerFor == i) c.accent.copy(alpha = 0.18f) else c.surface2)
                                    .clickable { pickerFor = if (pickerFor == i) null else i }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (f.col > 0) {
                                        colLetter(f.col) + " · " + (headers.getOrNull(f.col - 1)?.ifBlank { null } ?: "column")
                                    } else "Link",
                                    color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconBtn(
                                Icons.Filled.Close, "Remove box",
                                {
                                    pickerFor = null
                                    onDraft(draft.filterIndexed { j, _ -> j != i })
                                },
                                tint = c.error
                            )
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
                        onDraft(draft + SheetField(label, nextCol))
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
                BoxFieldGrid(
                    fields = fields,
                    inputs = inputs,
                    firstFocus = firstFocus,
                    onMoveNext = onMoveNext,
                    onSubmit = onSubmit,
                    onReorder = onReorder
                )
                if (fields.size > 1) {
                    Text(
                        "Press and hold a box's title to move it.",
                        Modifier.padding(horizontal = 4.dp),
                        color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
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

/** Slot geometry for the 2-column box grid (all in pixels). */
private data class GridMetrics(val w: Float, val h: Float, val g: Float) {
    fun slot(index: Int): Offset = Offset((index % GRID_COLS) * (w + g), (index / GRID_COLS) * (h + g))

    /** Which slot a point (the dragged tile's centre) is over. */
    fun indexAt(p: Offset, count: Int): Int {
        val col = (p.x / (w + g)).toInt().coerceIn(0, GRID_COLS - 1)
        val rowCount = (count + GRID_COLS - 1) / GRID_COLS
        val row = (p.y / (h + g)).toInt().coerceIn(0, (rowCount - 1).coerceAtLeast(0))
        return (row * GRID_COLS + col).coerceIn(0, (count - 1).coerceAtLeast(0))
    }
}

/**
 * The input boxes as a 2-column grid you can rearrange like phone widgets: press and hold a
 * box's title, drag it over another slot (the others slide out of the way live), let go to
 * drop it. The new order is saved.
 */
@Composable
private fun BoxFieldGrid(
    fields: List<SheetField>,
    inputs: SnapshotStateMap<String, String>,
    firstFocus: FocusRequester,
    onMoveNext: () -> Unit,
    onSubmit: () -> Unit,
    onReorder: (List<SheetField>) -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var live by remember { mutableStateOf<List<SheetField>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var fingerPos by remember { mutableStateOf(Offset.Zero) }

    val fieldsNow by rememberUpdatedState(fields)
    val onReorderNow by rememberUpdatedState(onReorder)

    val shown = live ?: fields
    val gap = 10.dp
    val tileH = 100.dp
    val rowCount = (shown.size + GRID_COLS - 1) / GRID_COLS
    val totalH = tileH * rowCount + gap * (rowCount - 1).coerceAtLeast(0)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileW = (maxWidth - gap) / GRID_COLS
        val metrics by rememberUpdatedState(
            GridMetrics(
                w = with(density) { tileW.toPx() },
                h = with(density) { tileH.toPx() },
                g = with(density) { gap.toPx() }
            )
        )

        Box(Modifier.fillMaxWidth().height(totalH)) {
            shown.forEachIndexed { index, f ->
                key(f.id) {
                    val dragged = draggingId == f.id
                    val target = if (dragged) fingerPos else metrics.slot(index)
                    val spec: AnimationSpec<Offset> =
                        if (dragged) snap() else spring(stiffness = Spring.StiffnessMediumLow)
                    val pos by animateOffsetAsState(target, spec, label = "tile")
                    val raise by animateFloatAsState(if (dragged) 1f else 0f, label = "tileRaise")

                    FieldTile(
                        field = f,
                        value = inputs[f.id] ?: "",
                        onValue = { inputs[f.id] = it },
                        imeAction = if (index == shown.lastIndex) ImeAction.Done else ImeAction.Next,
                        onImeAction = { if (index == shown.lastIndex) onSubmit() else onMoveNext() },
                        focusRequester = if (index == 0) firstFocus else null,
                        modifier = Modifier
                            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                            .size(tileW, tileH)
                            .zIndex(if (dragged || raise > 0.01f) 1f else 0f)
                            .graphicsLayer {
                                val s = 1f + 0.05f * raise
                                scaleX = s
                                scaleY = s
                                shadowElevation = 24f * raise
                                shape = Radius
                                clip = false
                            },
                        handleModifier = Modifier.pointerInput(f.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val list = live ?: fieldsNow
                                    val i = list.indexOfFirst { it.id == f.id }
                                    if (i >= 0) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        live = list
                                        fingerPos = metrics.slot(i)
                                        draggingId = f.id
                                    }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val list = live
                                    if (draggingId == f.id && list != null) {
                                        fingerPos += amount
                                        val from = list.indexOfFirst { it.id == f.id }
                                        val to = metrics.indexAt(
                                            fingerPos + Offset(metrics.w / 2f, metrics.h / 2f),
                                            list.size
                                        )
                                        if (from >= 0 && to != from) {
                                            val next = list.toMutableList()
                                            next.add(to, next.removeAt(from))
                                            live = next
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val list = live
                                    draggingId = null
                                    if (list != null && list.map { it.id } != fieldsNow.map { it.id }) {
                                        onReorderNow(list)
                                    }
                                    live = null
                                },
                                onDragCancel = {
                                    draggingId = null
                                    live = null
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldTile(
    field: SheetField,
    value: String,
    onValue: (String) -> Unit,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier,
    handleModifier: Modifier
) {
    val c = LocalAppColors.current
    Column(
        modifier
            .clip(Radius)
            .background(c.surface2)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
    ) {
        // The title strip is the drag handle (the text field itself keeps normal typing/selection).
        Row(
            handleModifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                SectionLabel(field.label)
            }
            Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
        PillField(
            value = value,
            onValueChange = onValue,
            placeholder = "Type here",
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
            fontSize = 16.sp,
            horizontalPadding = 14.dp,
            background = c.surface,
            imeAction = imeAction,
            onImeAction = onImeAction,
            focusRequester = focusRequester
        )
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
