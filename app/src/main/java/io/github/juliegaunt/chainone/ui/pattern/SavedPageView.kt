package io.github.juliegaunt.chainone.ui.pattern

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Shows the saved copy of a pattern's web page. It's fully offline: scripts are off, the network
 * is blocked, it can only load files from the page's own folder, and tapping a link opens it in
 * the browser instead.
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
                        // The page and its photos only: a page (from a tampered backup, say) can't
                        // show the app's other files, such as its database.
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            if (isInsideFolder(page.parentFile, request.url)) null else BLOCKED

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

/** True for a `file:` address inside [folder]. Anything else (another folder, `content:`, `data:`) is refused. */
internal fun isInsideFolder(folder: File?, url: Uri): Boolean {
    if (folder == null || url.scheme != "file") return false
    val path = url.path ?: return false
    return File(path).canonicalPath.startsWith(folder.canonicalPath + File.separator)
}

private val BLOCKED: WebResourceResponse
    get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
