package com.partspicker.app

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ListRow(val part: Part, val extra: Int)

private fun buildRows(parts: List<Part>, query: String): Pair<List<ListRow>, Boolean> {
    val q = query.trim().lowercase()
    val filtered = parts.filter {
        q.isEmpty() || it.partNumber.lowercase().contains(q) || it.location.lowercase().contains(q)
    }.asReversed()

    if (q.isNotEmpty()) return filtered.map { ListRow(it, 0) } to false

    val counts = HashMap<String, Int>()
    val firsts = ArrayList<Part>()
    for (p in filtered) {
        val k = p.location.lowercase()
        val n = counts[k]
        if (n == null) {
            counts[k] = 0
            firsts.add(p)
        } else {
            counts[k] = n + 1
        }
    }
    return firsts.map { ListRow(it, counts[it.location.lowercase()] ?: 0) } to true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(vm: PartsViewModel, listState: LazyListState, onOpenSettings: () -> Unit) {
    val c = LocalAppColors.current
    val (rows, grouped) = remember(vm.parts, vm.searchQuery) { buildRows(vm.parts, vm.searchQuery) }
    val dupCount = remember(vm.parts, vm.ignoredDups) {
        PartsLogic.findDuplicates(vm.parts, vm.ignoredDups).size
    }
    val partFocus = remember { FocusRequester() }
    LaunchedEffect(vm.focusPartTick) {
        if (vm.focusPartTick > 0) {
            try { partFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    val countLabel = when {
        vm.parts.isEmpty() -> ""
        grouped -> "${rows.size} location${if (rows.size == 1) "" else "s"} · ${vm.parts.size} part${if (vm.parts.size == 1) "" else "s"}"
        else -> "${rows.size} of ${vm.parts.size} shown"
    }

    Box(Modifier.fillMaxWidth().fillMaxHeight().background(c.bg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            item {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 6.dp)) {
                    Eyebrow("Inventory", Modifier.padding(bottom = 10.dp))
                    Text(
                        "Parts Picker",
                        color = c.text,
                        fontSize = 44.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp
                    )
                }
            }

            if (dupCount > 0) {
                item { DupAlert(dupCount) { vm.showDupView = true } }
            }

            item { Bento(vm.parts.size, vm.locations.size, vm.modelCodes.size) }

            stickyHeader {
                Box(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    PillField(
                        value = vm.searchQuery,
                        onValueChange = { vm.searchQuery = it },
                        placeholder = "Search part number or location",
                        modifier = Modifier.fillMaxWidth().lift(PillShape, 3.dp),
                        height = 58.dp,
                        fontSize = 17.sp,
                        background = c.surface,
                        leading = {
                            Icon(Icons.Filled.Search, null, tint = c.muted, modifier = Modifier.size(22.dp))
                        }
                    )
                }
            }

            item { EntryCard(vm, partFocus) }

            item {
                Text(
                    countLabel,
                    Modifier.padding(start = 22.dp, end = 22.dp, bottom = 10.dp),
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            item { TableHeader() }

            if (rows.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Box(
                            Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 18.dp, vertical = 44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (vm.parts.isEmpty()) "No parts saved yet." else "No matches.",
                                color = c.muted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            itemsIndexed(rows, key = { _, r -> r.part.id }) { index, r ->
                PartRow(
                    row = r,
                    model = PartsLogic.modelFor(r.part.partNumber, vm.modelCodes),
                    showBadge = grouped,
                    showDivider = index != rows.lastIndex,
                    onClick = { vm.openLocSheet(r.part.location) }
                )
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
        }

        Dock(
            label = "Settings",
            icon = Icons.Filled.Settings,
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun Dock(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 18.dp)
            .lift(PillShape, 12.dp)
            .clip(PillShape)
            .background(c.ink)
            .padding(6.dp)
    ) {
        Row(
            Modifier
                .clip(PillShape)
                .clickable(onClick = onClick)
                .height(48.dp)
                .padding(start = 18.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = c.onInk, modifier = Modifier.size(20.dp))
            Text(label, color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun DupAlert(count: Int, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .lift(Radius)
                .clip(Radius)
                .background(c.surface)
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = c.onAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "$count duplicate${if (count == 1) "" else "s"} found",
                    color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Same part number in more than one location",
                    color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Bento(parts: Int, locations: Int, models: Int) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp).height(172.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .lift(RadiusXl)
                .clip(RadiusXl)
                .background(c.ink)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Parts on file", color = c.onInk.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                parts.toString(),
                color = c.onInk,
                fontSize = 84.sp,
                lineHeight = 84.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-5).sp,
                maxLines = 1
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTile(locations.toString(), "Locations", c.surface, true, Modifier.weight(1f))
            SmallTile(models.toString(), "Model codes", c.surface2, false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallTile(num: String, label: String, bg: androidx.compose.ui.graphics.Color, raised: Boolean, modifier: Modifier) {
    val c = LocalAppColors.current
    Column(
        modifier
            .fillMaxWidth()
            .then(if (raised) Modifier.lift(Radius) else Modifier)
            .clip(Radius)
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(num, color = c.text, fontSize = 34.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, maxLines = 1)
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EntryCard(vm: PartsViewModel, partFocus: FocusRequester) {
    val c = LocalAppColors.current
    val locFocus = remember { FocusRequester() }
    var locFocused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    val query = vm.locationInput.trim()
    val matches = remember(query, vm.locations) {
        if (query.isEmpty()) emptyList()
        else vm.locations.filter { it.value.contains(query, ignoreCase = true) }.take(50)
    }
    val showSuggestions = locFocused && query.isNotEmpty() && !dismissed

    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 22.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .lift(RadiusXl)
                .clip(RadiusXl)
                .background(c.surface)
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("New entry", Modifier.padding(horizontal = 4.dp))

            PillField(
                value = vm.partNumberInput,
                onValueChange = { vm.partNumberInput = it },
                placeholder = "Part number",
                modifier = Modifier.fillMaxWidth(),
                height = 58.dp,
                mono = true,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                imeAction = if (vm.locationInput.isNotBlank()) ImeAction.Done else ImeAction.Next,
                onImeAction = {
                    if (vm.locationInput.isNotBlank()) vm.addPart() else locFocus.requestFocus()
                },
                focusRequester = partFocus
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillField(
                    value = vm.locationInput,
                    onValueChange = { vm.locationInput = it; dismissed = false },
                    placeholder = "Location",
                    modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Done,
                    onImeAction = { vm.addPart() },
                    focusRequester = locFocus,
                    onFocusChange = { locFocused = it }
                )
                IconBtn(
                    icon = if (vm.locationLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    desc = if (vm.locationLocked) "Location locked — stays after adding" else "Location unlocked — advances after adding",
                    onClick = { vm.locationLocked = !vm.locationLocked },
                    tint = if (vm.locationLocked) c.onAccent else c.muted,
                    background = if (vm.locationLocked) c.accent else c.surface2,
                    size = 52.dp
                )
                PillButton("Add part", onClick = { vm.addPart() })
            }

            StatusText(vm.formError, c.error)

            if (showSuggestions) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(Radius)
                        .background(c.surface2)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp)
                ) {
                    if (matches.isEmpty()) {
                        Text("No matching locations", Modifier.padding(14.dp), color = c.muted, fontSize = 14.sp)
                    } else {
                        matches.forEach { l ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .clip(RadiusS)
                                    .clickable {
                                        vm.locationInput = l.value
                                        dismissed = true
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(l.value, color = c.text, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val W_PART = 1.35f
private const val W_LOC = 1.1f
private const val W_MODEL = 0.85f

@Composable
private fun TableHeader() {
    val c = LocalAppColors.current
    Box(Modifier.padding(horizontal = 16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(c.surface)
        ) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp)) {
                SectionLabel("Part", Modifier.weight(W_PART))
                SectionLabel("Location", Modifier.weight(W_LOC))
                SectionLabel("Model", Modifier.weight(W_MODEL))
            }
            HorizontalDivider(color = c.line)
        }
    }
}

@Composable
private fun PartRow(row: ListRow, model: String, showBadge: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().background(c.surface).clickable(onClick = onClick)) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(W_PART), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.part.partNumber,
                        Modifier.weight(1f, fill = false),
                        color = c.text,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (showBadge && row.extra > 0) {
                        Text(
                            "+${row.extra}",
                            Modifier
                                .padding(start = 8.dp)
                                .clip(PillShape)
                                .background(c.surface2)
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            color = c.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Text(
                    row.part.location,
                    Modifier.weight(W_LOC).padding(end = 6.dp),
                    color = c.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    if (model.isNotEmpty()) model else "—",
                    Modifier.weight(W_MODEL),
                    color = c.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
            if (showDivider) HorizontalDivider(color = c.line)
        }
    }
}
