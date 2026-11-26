package com.partspicker.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Edit
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
 * Sheets section (opened from the Sheets button in the dock): a plain list of the
 * Google Sheets used for stock taking, shown by name only (Sheet1, Sheet2, ...).
 * Individual sheets are never added to the dock — the dock only has Sheets and Settings.
 * Tapping a sheet opens it (in the Google Sheets app if installed) so stock can be
 * updated there; the pencil edits the name/link and also holds the delete button.
 */
@Composable
fun SheetsScreen(vm: PartsViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 22.dp, top = 10.dp, bottom = 12.dp),
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
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (vm.sheetLinks.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                            onSave = { n, u -> vm.updateSheetLink(sheet.id, n, u); editingId = null },
                            onDelete = { vm.deleteSheetLink(sheet.id); editingId = null }
                        )
                    } else {
                        SheetCard(
                            name = sheet.name,
                            onOpen = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sheet.url)))
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, "Couldn't open that link.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEdit = { editingId = sheet.id }
                        )
                    }
                }
            }

            item {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                    Column {
                        if (showAdd) {
                            EditSheetCard(
                                initialName = vm.suggestNextSheetName(),
                                initialUrl = "",
                                onCancel = { showAdd = false; vm.sheetError = "" },
                                onSave = { n, u -> if (vm.addSheetLink(n, u)) showAdd = false }
                            )
                            StatusText(vm.sheetError, c.error, Modifier.padding(top = 8.dp))
                        } else {
                            PillButton(
                                "+ Add sheet",
                                onClick = { showAdd = true },
                                modifier = Modifier.fillMaxWidth(),
                                container = c.surface2, content = c.text
                            )
                        }
                    }
                }
            }

            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/** One row in the Sheets list: just the sheet's name. Tap to open; pencil to edit. */
@Composable
private fun SheetCard(name: String, onOpen: () -> Unit, onEdit: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .lift(Radius, 3.dp)
            .clip(Radius)
            .background(c.surface)
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            Modifier.weight(1f).padding(vertical = 14.dp),
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        IconBtn(Icons.Filled.Edit, "Edit $name", onEdit, tint = c.muted, background = c.surface2, size = 40.dp)
    }
}

@Composable
private fun EditSheetCard(
    initialName: String,
    initialUrl: String,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val c = LocalAppColors.current
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var confirmDelete by remember { mutableStateOf(false) }

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
        if (onDelete != null) {
            PillButton(
                if (confirmDelete) "Tap again to delete" else "Delete sheet",
                { if (confirmDelete) onDelete() else confirmDelete = true },
                Modifier.fillMaxWidth(),
                container = c.surface2, content = c.error
            )
        }
    }
}
