package com.partspicker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppSheets(vm: PartsViewModel) {
    if (vm.showDupView) DupViewSheet(vm)
    vm.pendingDup?.let { DupAddSheet(vm, it) }
    vm.pendingConflict?.let { ConflictSheet(vm, it) }
    if (vm.locSheetKey != null) LocationSheet(vm)
}

@Composable
private fun DupAddSheet(vm: PartsViewModel, p: PendingDup) {
    val c = LocalAppColors.current
    AppSheet(onDismiss = vm::cancelDupAdd) {
        Text(
            "Part \"${p.partNumber}\" is already in ${if (p.elsewhere.size == 1) "this location" else "these locations"}. " +
                "Do you want to add it to \"${p.location}\" too?",
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 18.dp),
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        ChipRow(p.elsewhere, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Cancel", vm::cancelDupAdd, Modifier.weight(1f), container = c.surface2, content = c.text)
            PillButton("Add here", vm::confirmDupAdd, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConflictSheet(vm: PartsViewModel, p: PendingConflict) {
    val c = LocalAppColors.current
    AppSheet(onDismiss = vm::cancelConflict) {
        Text(
            "Location \"${p.location}\" already has part \"${p.existingPartNumber}\". " +
                "Add this as another part here, or replace it?",
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 18.dp),
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Cancel", vm::cancelConflict, Modifier.weight(1f), container = c.surface2, content = c.text, horizontalPadding = 8.dp)
            PillButton("Add anyway", vm::conflictAddAnyway, Modifier.weight(1f), container = c.surface2, content = c.text, horizontalPadding = 8.dp)
            PillButton("Replace", vm::conflictReplace, Modifier.weight(1f), container = c.error, content = Color.White, horizontalPadding = 8.dp)
        }
    }
}

@Composable
private fun DupViewSheet(vm: PartsViewModel) {
    val c = LocalAppColors.current
    val groups = PartsLogic.findDuplicates(vm.parts, vm.ignoredDups)
    val hidden = PartsLogic.findDuplicates(vm.parts, vm.ignoredDups, includeIgnored = true).size - groups.size

    AppSheet(onDismiss = { vm.showDupView = false }) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Duplicates",
                Modifier.padding(horizontal = 4.dp),
                color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp
            )
            IconBtn(Icons.Filled.Close, "Close", { vm.showDupView = false }, background = c.surface2)
        }

        if (groups.isEmpty()) {
            Text("No duplicates.", Modifier.padding(6.dp), color = c.muted, fontSize = 14.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { g ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .lift(Radius, 3.dp)
                        .clip(Radius)
                        .background(c.surface)
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            g.partNumber,
                            Modifier.weight(1f, fill = false),
                            color = c.text, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold, fontSize = 18.sp
                        )
                        Text("${g.locations.size} locations", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.weight(1f))
                        PillButton(
                            "Ignore",
                            onClick = { vm.ignoreDuplicate(g.partNumber.lowercase()) },
                            container = c.surface2, content = c.text,
                            height = 38.dp, fontSize = 13.sp, horizontalPadding = 16.dp
                        )
                    }
                    ChipRow(g.locations, Modifier.padding(top = 12.dp))
                }
            }
        }
        if (hidden > 0) {
            PillButton(
                "Restore $hidden ignored",
                onClick = vm::restoreIgnored,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                container = Color.Transparent, content = c.muted,
                height = 44.dp, fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun LocationSheet(vm: PartsViewModel) {
    val c = LocalAppColors.current
    val key = vm.locSheetKey ?: return
    val list = vm.parts.filter { it.location.lowercase() == key }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { vm.closeLocSheet() }
        return
    }
    val n = list.size
    val confirming = vm.locSheetConfirming
    val warn = when {
        confirming && n == 1 -> "Are you sure? This part will be permanently deleted."
        confirming -> "Are you sure? All $n parts at this location will be permanently deleted."
        n > 1 -> "Deleting this location removes all $n parts listed here."
        else -> ""
    }

    AppSheet(onDismiss = vm::closeLocSheet) {
        Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 14.dp)) {
            Eyebrow("Location", Modifier.padding(bottom = 6.dp))
            Text(
                list[0].location,
                color = c.text, fontSize = 30.sp, lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp
            )
            Text(
                "$n part${if (n == 1) "" else "s"}",
                Modifier.padding(top = 10.dp).clip(PillShape).background(c.surface2).padding(horizontal = 12.dp, vertical = 5.dp),
                color = c.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            list.asReversed().forEach { p ->
                val armed = vm.locArmedPartId == p.id
                val model = PartsLogic.modelFor(p.partNumber, vm.modelCodes)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .lift(Radius, 3.dp)
                        .clip(Radius)
                        .background(c.surface)
                        .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        p.partNumber,
                        Modifier.weight(1f),
                        color = c.text, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                    )
                    if (model.isNotEmpty()) {
                        Text(model, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                    Row(
                        Modifier
                            .heightIn(min = 44.dp)
                            .widthIn(min = 44.dp)
                            .clip(PillShape)
                            .background(if (armed) c.error else Color.Transparent)
                            .clickable { vm.onLocPartDeleteTap(p.id) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (armed) {
                            Text("Delete?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Delete, "Delete part ${p.partNumber}", tint = c.muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        if (warn.isNotEmpty()) {
            Text(
                warn,
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(Radius)
                    .background(c.surface2)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = c.error, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Cancel", vm::closeLocSheet, Modifier.weight(1f), container = c.surface2, content = c.text)
            PillButton(
                if (confirming) "Confirm delete" else "Delete location",
                vm::onLocDeleteTap,
                Modifier.weight(1.4f),
                container = c.error, content = Color.White
            )
        }
    }
}
