package io.github.juliegaunt.chainone.ui.pattern

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Shows the saved copy of a pattern's web page. It's fully offline: scripts are off, the network
 * is blocked, and tapping a link opens it in the browser instead.
 */
@Composable
fun SavedPageView(page: File, modifier: Modifier = Modifier) {
    key(page) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.blockNetworkLoads = true
                    // The copy and its photos are files in the app's own storage.
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (request.url.scheme == "http" || request.url.scheme == "https") {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                } catch (e: ActivityNotFoundException) {
                                    // No browser installed; nothing sensible to do.
                                }
                            }
                            return true
                        }
                    }
                    loadUrl(Uri.fromFile(page).toString())
                }
            },
            onRelease = { it.destroy() },
            modifier = modifier,
        )
    }
}
