package com.abhimanyu.kiapartslocator

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.abhimanyu.kiapartslocator.ui.theme.PartsPickerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PartsPickerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PartsWebView()
                }
            }
        }
    }
}

private const val APP_URL = "https://appassets.androidplatform.net/assets/index.html"

// Closes the top-most open sheet/screen inside the HTML UI.
// Returns '1' if something was closed, '0' if the app should exit.
private const val BACK_JS = """
(function () {
  function vis(id) { var e = document.getElementById(id); return e && !e.classList.contains('hidden'); }
  function tap(id) { document.getElementById(id).click(); return '1'; }
  if (vis('modelCodesOverlay')) return tap('closeModelCodesBtn');
  if (vis('dupViewOverlay')) return tap('dupViewCloseBtn');
  if (vis('dupAddOverlay')) return tap('dupAddCancelBtn');
  if (vis('locSheet')) return tap('locCancelBtn');
  if (vis('locationConflictOverlay')) return tap('conflictCancelBtn');
  if (vis('settingsScreen')) return tap('closeSettingsBtn');
  return '0';
})()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PartsWebView() {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val wv = webView
        if (wv == null) {
            (context as? Activity)?.finish()
        } else {
            wv.evaluateJavascript(BACK_JS) { result ->
                if (result != "\"1\"") (context as? Activity)?.finish()
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .build()

            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                }
                loadUrl(APP_URL)
                webView = this
            }
        }
    )
}
