package com.partspicker.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// A desktop-Chrome user agent makes Google serve the full Sheets editor (phones normally get
// a view-only page) and avoids Google's "embedded webview" sign-in block.
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/** Turns any pasted Google Sheets link into the full editor URL (keeps the tab id if present). */
fun sheetEditUrl(raw: String): String {
    val id = Regex("/spreadsheets/(?:u/\\d+/)?d/([A-Za-z0-9_-]+)").find(raw)?.groupValues?.get(1)
        ?: return raw
    val gid = Regex("[#&?]gid=(\\d+)").find(raw)?.groupValues?.get(1)
    return "https://docs.google.com/spreadsheets/d/$id/edit" + if (gid != null) "#gid=$gid" else ""
}

/**
 * Opens a Google Sheet inside the app using the real Google Sheets editor, so every edit
 * is saved straight into the Google Sheet. Sign in once; the login is remembered.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SheetViewerScreen(sheet: SheetLink, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }

    // System back steps back through the sheet's own pages first, then leaves the viewer.
    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            web?.destroy()
            web = null
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tint = c.text)
            Text(
                sheet.name,
                Modifier.weight(1f).padding(horizontal = 8.dp),
                color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1
            )
            IconBtn(Icons.Filled.Refresh, "Reload", { web?.reload() }, tint = c.muted)
            IconBtn(
                Icons.AutoMirrored.Filled.OpenInNew, "Open in Google Sheets",
                {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sheet.url)))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Couldn't open that link.", Toast.LENGTH_SHORT).show()
                    }
                },
                tint = c.muted
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = DESKTOP_UA
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }
                        }
                        web = this
                        loadUrl(sheetEditUrl(sheet.url))
                    }
                }
            )
            if (loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = c.accent,
                    trackColor = c.surface2
                )
            }
        }
    }
}
