package io.github.juliegaunt.chainone.ui.pattern

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import android.os.Looper
import android.os.Handler
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Shows the saved copy of a pattern's web page. It's fully offline: scripts are off, the network
 * is blocked, it can only load files from the page's own folder, and tapping a link opens it in
 * the browser instead. Pressing and holding a picture calls [onPictureLongPress] with its file name.
 */
@Composable
fun SavedPageView(page: File, version: Int, onPictureLongPress: (fileName: String) -> Unit, modifier: Modifier = Modifier) {
    val longPress by rememberUpdatedState(onPictureLongPress)
    key(page) {
        val reload = remember { Reload(version) }
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
                        override fun onPageFinished(view: WebView, url: String?) {
                            val y = reload.scrollY ?: return
                            reload.scrollY = null
                            // After the reloaded page has been laid out, back to where the reader was.
                            view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                                override fun onComplete(requestId: Long) = view.scrollTo(0, y)
                            })
                        }

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
                    // Pressing and holding a picture offers to hide it.
                    setOnLongClickListener {
                        val type = hitTestResult.type
                        if (type != WebView.HitTestResult.IMAGE_TYPE && type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                            return@setOnLongClickListener false
                        }
                        val reply = Handler(Looper.getMainLooper()) { message ->
                            message.data.getString("url")?.let { Uri.parse(it).lastPathSegment }?.let(longPress)
                            true
                        }
                        requestImageRef(reply.obtainMessage())
                        true
                    }
                    loadUrl(Uri.fromFile(page).toString())
                }
            },
            // A new version (a picture hidden or shown) reloads the page in place, keeping the reader's spot.
            update = { view ->
                if (reload.version != version) {
                    reload.version = version
                    reload.scrollY = view.scrollY
                    view.reload()
                }
            },
            onRelease = { it.destroy() },
            modifier = modifier,
        )
    }
}

/** The page version a [SavedPageView] has loaded, and where to scroll once a reload of it finishes. */
private class Reload(var version: Int, var scrollY: Int? = null)

/** True for a `file:` address inside [folder]. Anything else (another folder, `content:`, `data:`) is refused. */
internal fun isInsideFolder(folder: File?, url: Uri): Boolean {
    if (folder == null || url.scheme != "file") return false
    val path = url.path ?: return false
    return File(path).canonicalPath.startsWith(folder.canonicalPath + File.separator)
}

private val BLOCKED: WebResourceResponse
    get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
