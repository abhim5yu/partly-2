package com.partspicker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: PartsViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    var showModelCodes by remember { mutableStateOf(false) }
    val openGroups = remember { mutableStateMapOf<String, Boolean>() }

    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var letterA by remember { mutableStateOf("") }
    var letterB by remember { mutableStateOf("") }
    var rangeStatus by remember { mutableStateOf("") }

    val rangeResult = remember(start, end, letterA, letterB) {
        if (start.isEmpty() && end.isEmpty()) null
        else PartsLogic.computeRangeCodes(start.trim(), end.trim(), letterA.trim(), letterB.trim())
    }
    val rangeCodes = rangeResult?.codes
    val rangePreview = rangeCodes?.let { codes ->
        val sample = if (codes.size <= 4) codes.joinToString(", ")
        else codes[0] + ", " + codes[1] + ", … , " + codes.last()
        "${codes.size} location${if (codes.size == 1) "" else "s"}: $sample"
    } ?: ""

    val groups = remember(vm.locations) {
        vm.locations.groupBy { it.group.ifEmpty { "Other locations" } }
    }

    Box(Modifier.fillMaxWidth().fillMaxHeight().background(c.bg)) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            item {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 6.dp)) {
                    Eyebrow("Preferences", Modifier.padding(bottom = 10.dp))
                    Text(
                        "Settings",
                        color = c.text, fontSize = 44.sp, lineHeight = 44.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp
                    )
                }
            }

            item { SettingsLabel("Appearance", first = true) }
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(PillShape)
                        .background(c.surface2)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeMode.values().forEach { m ->
                        val selected = m == vm.themeMode
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .then(if (selected) Modifier.lift(PillShape, 2.dp) else Modifier)
                                .clip(PillShape)
                                .background(if (selected) c.surface else Color.Transparent)
                                .clickable { vm.setTheme(m) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                m.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (selected) c.text else c.muted,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item { SettingsLabel("Part number decoding") }
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .lift(Radius)
                        .clip(Radius)
                        .background(c.surface)
                        .clickable { showModelCodes = true }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Model codes", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
            }

            item { SettingsLabel("Add a range") }
            item {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .lift(RadiusXl)
                        .clip(RadiusXl)
                        .background(c.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RangeGroup("From", "13A010101", start, { start = it; rangeStatus = "" }, letterA, { letterA = it; rangeStatus = "" }, letterB, { letterB = it; rangeStatus = "" })
                    RangeGroup("To", "13A010114", end, { end = it; rangeStatus = "" }, letterA, { letterA = it; rangeStatus = "" }, letterB, { letterB = it; rangeStatus = "" })

                    StatusText(rangePreview, c.muted, weight = FontWeight.Normal)
                    StatusText(rangeResult?.error ?: "", c.error)
                    StatusText(rangeStatus, c.success)

                    PillButton(
                        "Add range",
                        onClick = {
                            val codes = rangeCodes ?: return@PillButton
                            val la = letterA.trim()
                            val lb = letterB.trim()
                            val label = start.trim() + " – " + end.trim() +
                                if (la.isNotEmpty() && lb.isNotEmpty()) " ($la-$lb)" else ""
                            val (added, skipped) = vm.addLocations(codes, label)
                            start = ""; end = ""; letterA = ""; letterB = ""
                            rangeStatus = "$added location${if (added == 1) "" else "s"} added" +
                                (if (skipped > 0) ", $skipped already existed" else "") + "."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = rangeCodes != null
                    )
                }
            }

            item { SettingsLabel("Saved locations") }
            if (vm.locations.isEmpty()) {
                item { Text("No locations yet.", Modifier.padding(start = 22.dp), color = c.muted, fontSize = 14.sp) }
            }
            groups.forEach { (name, items) ->
                val open = openGroups[name] == true
                item(key = "group:$name") {
                    GroupHeader(name, items.size, open) { openGroups[name] = !open }
                }
                if (open) {
                    itemsIndexed(items, key = { _, l -> l.id }) { i, loc ->
                        LocationRow(loc, last = i == items.lastIndex) { vm.deleteLocation(loc.id) }
                    }
                }
            }
        }

        Dock(
            label = "Back",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showModelCodes) ModelCodesSheet(vm) { showModelCodes = false }
}

@Composable
private fun SettingsLabel(text: String, first: Boolean = false) {
    SectionLabel(text, Modifier.padding(start = 22.dp, end = 22.dp, top = if (first) 8.dp else 28.dp, bottom = 10.dp))
}

@Composable
private fun RangeGroup(
    label: String,
    placeholder: String,
    code: String,
    onCode: (String) -> Unit,
    letterA: String,
    onLetterA: (String) -> Unit,
    letterB: String,
    onLetterB: (String) -> Unit
) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().clip(Radius).background(c.surface2).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel(label, Modifier.padding(start = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillField(
                value = code, onValueChange = onCode, placeholder = placeholder,
                modifier = Modifier.weight(1f), mono = true, fontWeight = FontWeight.Bold,
                background = c.surface
            )
            PillField(
                value = letterA, onValueChange = { onLetterA(it.take(1)) }, placeholder = "A",
                modifier = Modifier.width52(), lettersOnly = true, centered = true,
                fontWeight = FontWeight.Bold, background = c.surface, horizontalPadding = 0.dp
            )
            Text("–", color = c.muted)
            PillField(
                value = letterB, onValueChange = { onLetterB(it.take(1)) }, placeholder = "E",
                modifier = Modifier.width52(), lettersOnly = true, centered = true,
                fontWeight = FontWeight.Bold, background = c.surface, horizontalPadding = 0.dp
            )
        }
    }
}

private fun Modifier.width52(): Modifier = this.then(Modifier.widthIn(min = 52.dp, max = 52.dp))

@Composable
private fun GroupHeader(name: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    val c = LocalAppColors.current
    val shape: Shape = if (open) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else Radius
    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = if (open) 0.dp else 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(c.surface)
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (open) c.accent else c.surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (open) "–" else "+",
                    color = if (open) c.onAccent else c.muted,
                    fontSize = 16.sp, lineHeight = 16.sp
                )
            }
            Text(
                name,
                Modifier.padding(start = 12.dp, end = 6.dp).weight(1f, fill = false),
                color = c.text, fontWeight = FontWeight.ExtraBold, maxLines = 1
            )
            Text("($count)", color = c.muted, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LocationRow(loc: LocationItem, last: Boolean, onDelete: () -> Unit) {
    val c = LocalAppColors.current
    val shape: Shape = if (last) RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp) else RoundedCornerShape(0.dp)
    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = if (last) 8.dp else 0.dp)) {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).padding(horizontal = 8.dp)) {
            HorizontalDivider(color = c.line)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(loc.value, Modifier.weight(1f), color = c.text, fontWeight = FontWeight.SemiBold)
                IconBtn(Icons.Filled.Close, "Delete location", onDelete, tint = c.error)
            }
        }
    }
}

@Composable
private fun ModelCodesSheet(vm: PartsViewModel, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    var code by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }
    var bulkStatus by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }

    val bulkResult = remember(bulk) { if (bulk.isBlank()) null else PartsLogic.parseBulkModelLines(bulk) }
    val bulkRows = bulkResult?.rows ?: emptyList()
    val bulkPreview = if (bulkResult != null && bulkRows.isNotEmpty()) {
        val sample = bulkRows.take(3).joinToString(", ") { it.first + "→" + it.second }
        "${bulkRows.size} line${if (bulkRows.size == 1) "" else "s"} ready: $sample" +
            (if (bulkRows.size > 3) ", …" else "") +
            (if (bulkResult.skipped > 0) " (${bulkResult.skipped} line${if (bulkResult.skipped == 1) "" else "s"} skipped)" else "")
    } else ""
    val bulkError = if (bulkResult != null && bulkRows.isEmpty()) "No valid \"code, model\" lines found." else ""

    AppSheet(onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Model codes", Modifier.padding(horizontal = 4.dp),
                color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp
            )
            IconBtn(Icons.Filled.Close, "Close", onDismiss, background = c.surface2)
        }

        Column(
            Modifier.fillMaxWidth().clip(Radius).background(c.surface2).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillField(
                    value = code, onValueChange = { code = it }, placeholder = "Code e.g. CC",
                    modifier = Modifier.weight(1f), mono = true, fontWeight = FontWeight.Bold,
                    background = c.surface, horizontalPadding = 16.dp, imeAction = ImeAction.Next
                )
                PillField(
                    value = model, onValueChange = { model = it }, placeholder = "Model e.g. SONET",
                    modifier = Modifier.weight(1.8f), background = c.surface, horizontalPadding = 16.dp,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        val err = vm.addModelCode(code, model)
                        error = err ?: ""
                        if (err == null) { code = ""; model = "" }
                    }
                )
            }
            StatusText(error, c.error)
            PillButton(
                "Add",
                onClick = {
                    val err = vm.addModelCode(code, model)
                    error = err ?: ""
                    if (err == null) { code = ""; model = "" }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsLabel("Bulk add (paste a list)")
        Column(
            Modifier.fillMaxWidth().clip(Radius).background(c.surface2).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PillField(
                value = bulk,
                onValueChange = { bulk = it; bulkStatus = "" },
                placeholder = "CC, SONET\nAB - ALPHA\nDX: DELTA-X",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false, minLines = 4, height = 110.dp,
                shape = Radius, background = c.surface, fontWeight = FontWeight.Normal
            )
            StatusText(bulkPreview, c.muted, weight = FontWeight.Normal)
            StatusText(bulkError, c.error)
            StatusText(bulkStatus, c.success)
            PillButton(
                "Add pasted codes",
                onClick = {
                    val (added, updated) = vm.addBulkModelCodes(bulkRows)
                    bulk = ""
                    bulkStatus = "$added added" + (if (updated > 0) ", $updated updated" else "") + "."
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = bulkRows.isNotEmpty()
            )
        }

        SettingsLabel("Saved model codes")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.modelCodes.isEmpty()) {
                Text("No model codes yet.", Modifier.padding(6.dp), color = c.muted, fontSize = 14.sp)
            }
            vm.modelCodes.forEach { m ->
                ModelCodeRow(
                    m = m,
                    editing = editingId == m.id,
                    onEdit = { editingId = m.id },
                    onCancel = { editingId = null },
                    onSave = { newCode, newModel ->
                        if (vm.updateModelCode(m.id, newCode, newModel)) editingId = null
                    },
                    onDelete = { vm.deleteModelCode(m.id) }
                )
            }
        }
    }
}

@Composable
private fun ModelCodeRow(
    m: ModelCode,
    editing: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalAppColors.current
    var editCode by remember(editing) { mutableStateOf(m.code) }
    var editModel by remember(editing) { mutableStateOf(m.model) }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .lift(Radius, 3.dp)
            .clip(Radius)
            .background(c.surface)
            .padding(start = if (editing) 8.dp else 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
            .then(if (editing) Modifier.padding(vertical = 6.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (editing) {
            PillField(
                value = editCode, onValueChange = { editCode = it }, placeholder = "Code",
                modifier = Modifier.weight(1f), height = 42.dp, mono = true,
                fontWeight = FontWeight.Bold, horizontalPadding = 16.dp
            )
            PillField(
                value = editModel, onValueChange = { editModel = it }, placeholder = "Model",
                modifier = Modifier.weight(1.6f), height = 42.dp, horizontalPadding = 16.dp
            )
            IconBtn(Icons.Filled.Check, "Save", { onSave(editCode, editModel) }, tint = c.accent)
            IconBtn(Icons.Filled.Close, "Cancel", onCancel, tint = c.error)
        } else {
            Row(Modifier.weight(1f)) {
                Text(
                    m.code, color = c.text, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(" — ${m.model}", color = c.text, fontWeight = FontWeight.Medium)
            }
            IconBtn(Icons.Filled.Edit, "Edit", onEdit, tint = c.accent)
            IconBtn(Icons.Filled.Close, "Delete", onDelete, tint = c.error)
        }
    }
}
