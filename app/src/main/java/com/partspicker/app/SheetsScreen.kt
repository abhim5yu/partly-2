package com.partspicker.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sheets section (opened from the Sheets button in the dock). Shows the Google Sheets used
 * for stock taking by name only (Sheet1, Sheet2, ...). Individual sheets are never added to
 * the dock — it only has Sheets and Settings. Tapping a sheet opens it in the app's own UI:
 * input boxes to add entries + the latest sheet contents.
 */
@Composable
fun SheetsScreen(vm: PartsViewModel, onBack: () -> Unit) {
    var viewingId by rememberSaveable { mutableStateOf<String?>(null) }
    val viewing = vm.sheetLinks.firstOrNull { it.id == viewingId }

    if (viewing != null) {
        SheetViewerScreen(vm = vm, sheet = viewing, onBack = { viewingId = null })
    } else {
        SheetListScreen(vm, onBack = onBack, onOpen = { viewingId = it.id })
    }
}

@Composable
private fun SheetListScreen(vm: PartsViewModel, onBack: () -> Unit, onOpen: (SheetLink) -> Unit) {
    val c = LocalAppColors.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // The connection settings stay tucked away behind the status dot (open by default only until set up).
    var showConn by rememberSaveable { mutableStateOf(vm.scriptUrl.isBlank()) }
    val problem = vm.scriptUrl.isBlank() || vm.scriptOk == false

    LaunchedEffect(Unit) {
        if (vm.scriptUrl.isNotBlank()) vm.testScript()
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tint = c.text)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Eyebrow("Stock taking")
                Text(
                    "Sheets",
                    color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp
                )
            }
            // Status dot: green = connected, red = needs attention. Tap to open the connection settings.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (showConn) c.surface2 else Color.Transparent)
                    .clickable { showConn = !showConn },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (problem) c.error else c.success)
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (showConn) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ConnectionCard(vm, onClose = { showConn = false })
                    }
                }
            }

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
                            onOpen = { onOpen(sheet) },
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

/**
 * The Google connection settings (opened from the status dot). Change the link any time:
 * only the link is replaced — your sheets, boxes and layouts stay exactly as they are.
 */
@Composable
private fun ConnectionCard(vm: PartsViewModel, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val connected = vm.scriptUrl.isNotBlank()
    var url by remember { mutableStateOf(vm.scriptUrl) }
    var error by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf(!connected) }

    val statusText = when {
        !connected -> "Not connected yet"
        vm.scriptOk == false -> vm.scriptNote.ifBlank { "There's a problem with the connection" }
        vm.scriptOk == true -> "Connected"
        else -> "Checking…"
    }
    val statusColor = when {
        !connected || vm.scriptOk == false -> c.error
        vm.scriptOk == true -> c.success
        else -> c.muted
    }

    Column(
        Modifier
            .fillMaxWidth()
            .lift(RadiusXl)
            .clip(RadiusXl)
            .background(c.surface)
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel("Google connection")
                Text(
                    statusText,
                    color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp
                )
            }
            IconBtn(Icons.Filled.Close, "Close", onClose, tint = c.muted, background = c.surface2, size = 36.dp)
        }

        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillField(
                value = url,
                onValueChange = { url = it; error = "" },
                placeholder = "Paste the Web app URL",
                modifier = Modifier.fillMaxWidth(),
                forceUppercase = false
            )
            StatusText(error, c.error)
            PillButton(
                if (connected) "Save changes" else "Save connection",
                onClick = {
                    val u = url.trim()
                    if (!u.startsWith("https://") || !u.contains("/exec")) {
                        error = "That should be the Web app URL ending in /exec."
                    } else {
                        error = ""
                        vm.saveScriptUrl(u)
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    "Test connection",
                    onClick = { vm.testScript() },
                    modifier = Modifier.weight(1f),
                    container = c.surface2, content = c.text
                )
                PillButton(
                    "Copy script",
                    onClick = {
                        val script = appsScriptSource(vm.ensureScriptToken())
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Parts Picker script", script))
                        Toast.makeText(context, "Script copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    container = c.surface2, content = c.text
                )
            }
            Text(
                if (steps) "Hide setup steps" else "Show setup steps",
                Modifier
                    .clip(PillShape)
                    .clickable { steps = !steps }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            if (steps) {
                Text(
                    "One-time setup so the app can read and fill your Google Sheets:\n" +
                        "1. Tap Copy script.\n" +
                        "2. Open script.google.com, tap New project, delete the sample code, paste, and Save.\n" +
                        "3. Deploy > New deployment > Web app. Execute as: Me. Who has access: Anyone. Deploy, then allow access.\n" +
                        "4. Paste the Web app URL above and tap Save.\n\n" +
                        "Your sheet must be a real Google Sheet. If it shows .XLSX, use File > Save as Google Sheets and add that new link here.",
                    color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp
                )
            }
            Text(
                "Changing the link keeps all your sheets, boxes and layouts.",
                color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
        }
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
