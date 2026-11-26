package com.partspicker.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sheets section: a list of external Google Sheets used for stock taking (Sheet1,
 * Sheet2, ...), kept separate from the main parts list since there's more data in
 * Sheets than this app holds. Each entry just links out to its Google Sheet for now —
 * live read/write sync needs a Google OAuth client, which has to be created in Google
 * Cloud Console first.
 */
@Composable
fun SheetsScreen(vm: PartsViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tint = c.text)
            Column(Modifier.padding(start = 6.dp)) {
                Eyebrow("Stock taking")
                Text(
                    "Sheets",
                    color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "Links to your Google Sheets — there's more stock data in Sheets than this app holds, so keep taking stock there and just jump in from here.",
                    Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 16.dp),
                    color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp
                )
            }

            if (vm.sheetLinks.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Box(
                            Modifier.fillMaxWidth().clip(Radius).background(c.surface).padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No sheets added yet.", color = c.muted, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            items(vm.sheetLinks, key = { it.id }) { sheet ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    if (editingId == sheet.id) {
                        EditSheetCard(
                            initialName = sheet.name,
                            initialUrl = sheet.url,
                            onCancel = { editingId = null },
                            onSave = { n, u -> vm.updateSheetLink(sheet.id, n, u); editingId = null }
                        )
                    } else {
                        SheetCard(
                            sheet = sheet,
                            onOpen = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sheet.url))) },
                            onEdit = { editingId = sheet.id },
                            onDelete = { vm.deleteSheetLink(sheet.id) }
                        )
                    }
                }
            }

            item {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                    if (showAdd) {
                        EditSheetCard(
                            initialName = vm.suggestNextSheetName(),
                            initialUrl = "",
                            onCancel = { showAdd = false; vm.sheetError = "" },
                            onSave = { n, u -> if (vm.addSheetLink(n, u)) showAdd = false }
                        )
                    } else {
                        PillButton(
                            "+ Add sheet",
                            onClick = { showAdd = true },
                            modifier = Modifier.fillMaxWidth(),
                            container = c.surface2, content = c.text
                        )
                    }
                    if (showAdd) {
                        StatusText(vm.sheetError, c.error, Modifier.padding(top = 8.dp))
                    }
                }
            }

            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun SheetCard(sheet: SheetLink, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .lift(Radius, 3.dp)
            .clip(Radius)
            .background(c.surface)
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(sheet.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                sheet.url,
                color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        IconBtn(Icons.AutoMirrored.Filled.OpenInNew, "Open ${sheet.name}", onOpen, tint = c.accent, background = c.surface2, size = 40.dp)
        IconBtn(Icons.Filled.Edit, "Edit ${sheet.name}", onEdit, tint = c.muted, background = c.surface2, size = 40.dp)
        IconBtn(Icons.Filled.Delete, "Delete ${sheet.name}", onDelete, tint = c.muted, size = 40.dp)
    }
}

@Composable
private fun EditSheetCard(
    initialName: String,
    initialUrl: String,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val c = LocalAppColors.current
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }

    Column(
        Modifier
            .fillMaxWidth()
            .lift(Radius, 3.dp)
            .clip(Radius)
            .background(c.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PillField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Name (e.g. Sheet1)",
            modifier = Modifier.fillMaxWidth(),
            forceUppercase = false
        )
        PillField(
            value = url,
            onValueChange = { url = it },
            placeholder = "Paste the Google Sheets link",
            modifier = Modifier.fillMaxWidth(),
            forceUppercase = false
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Cancel", onCancel, Modifier.weight(1f), container = c.surface2, content = c.text)
            PillButton("Save", { onSave(name, url) }, Modifier.weight(1f))
        }
    }
}
