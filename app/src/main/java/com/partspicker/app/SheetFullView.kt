package com.partspicker.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ROWNUM_W = 44.dp
private val COL_W = 120.dp
private val FIRST_COL_W = 140.dp

/**
 * The whole sheet you're working on, in the app's own style: every column and every row that
 * has something in it, refreshed from Google when it opens. Swipe sideways for more columns.
 */
@Composable
fun FullSheetScreen(vm: PartsViewModel, sheet: SheetLink, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val hScroll = rememberScrollState()

    BackHandler(onBack = onClose)
    LaunchedEffect(sheet.id) { vm.loadSheet(sheet) }

    val data = if (vm.sheetDataFor == sheet.id) vm.sheetData else null
    val values = data?.values.orEmpty()
    val colCount = values.maxOfOrNull { it.size } ?: 0
    val header = values.firstOrNull().orEmpty()
    // (row number in the sheet, cells) for every non-blank row below the header
    val body = remember(values) {
        values.drop(1).mapIndexedNotNull { i, r -> if (r.any { it.isNotBlank() }) (i + 2) to r else null }
    }
    fun colW(i: Int) = if (i == 0) FIRST_COL_W else COL_W

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClose, tint = c.text)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Eyebrow("Full sheet")
                Text(
                    sheet.name,
                    color = c.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp, maxLines = 1
                )
            }
            IconBtn(Icons.Filled.Refresh, "Refresh", { vm.loadSheet(sheet) }, tint = c.muted, background = c.surface2)
            Box(Modifier.width(8.dp))
            IconBtn(
                Icons.AutoMirrored.Filled.OpenInNew, "Open in Google Sheets",
                {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sheet.url)))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Couldn't open that link.", Toast.LENGTH_SHORT).show()
                    }
                },
                tint = c.muted, background = c.surface2
            )
        }
        if (vm.sheetLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surface2)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(c.surface)) {
                if (data == null || colCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                vm.scriptUrl.isBlank() -> "Not connected yet."
                                vm.sheetLoading -> "Loading the sheet…"
                                vm.sheetOpFailed -> vm.sheetOpStatus
                                else -> "No data yet — tap refresh."
                            },
                            Modifier.padding(24.dp),
                            color = c.muted, fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(hScroll).padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "#",
                            Modifier.width(ROWNUM_W).padding(start = 10.dp),
                            color = c.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                        )
                        for (i in 0 until colCount) {
                            Text(
                                header.getOrNull(i).orEmpty().uppercase(),
                                Modifier.width(colW(i)).padding(horizontal = 10.dp),
                                color = c.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = c.line)

                    if (body.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nothing filled in yet.", color = c.muted, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(body, key = { _, p -> p.first }) { _, pair ->
                                val rowNum = pair.first
                                val cells = pair.second
                                Column {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(hScroll)
                                            .background(
                                                if (rowNum == vm.lastAddedRow) c.accent.copy(alpha = 0.16f) else Color.Transparent
                                            )
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            rowNum.toString(),
                                            Modifier.width(ROWNUM_W).padding(start = 10.dp),
                                            color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                        )
                                        for (i in 0 until colCount) {
                                            val v = cells.getOrNull(i).orEmpty()
                                            Text(
                                                if (v.isEmpty()) "—" else v,
                                                Modifier.width(colW(i)).padding(horizontal = 10.dp),
                                                color = if (v.isEmpty()) c.muted else c.text,
                                                fontFamily = if (i == 0) FontFamily.Monospace else FontFamily.Default,
                                                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = c.line)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
