package io.github.juliegaunt.chainone.ui.navigation

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Something another app sent to Chain One with Share or "Open with". */
sealed interface SharedItem {
    /** Usually a link to a pattern page. */
    data class Text(val text: String) : SharedItem
    data class Pdf(val uri: Uri) : SharedItem

    companion object {
        private const val PDF_TYPE = "application/pdf"

        /**
         * A link from Share, or a PDF from Share or "Open with". Files are accepted only as
         * `content://` addresses, which the sending app grants access to; a `file://` address
         * could point Chain One at its own private files.
         */
        fun from(intent: Intent?): SharedItem? = when (intent?.action) {
            Intent.ACTION_SEND -> if (intent.type == PDF_TYPE) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.asSharedPdf()
            } else {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::Text)
            }
            Intent.ACTION_VIEW -> intent.data?.asSharedPdf()
            else -> null
        }

        private fun Uri.asSharedPdf(): Pdf? = takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }?.let(::Pdf)
    }
}
