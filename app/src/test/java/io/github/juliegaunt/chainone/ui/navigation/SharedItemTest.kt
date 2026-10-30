package io.github.juliegaunt.chainone.ui.navigation

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedItemTest {

    private val sharedPdf = Uri.parse("content://com.example.files/document/hat.pdf")

    @Test
    fun aSharedLinkIsText() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Hat https://example.com/hat")
        assertEquals(SharedItem.Text("Hat https://example.com/hat"), SharedItem.from(intent))
    }

    @Test
    fun aPdfSharedOrOpenedThroughAnotherAppIsAccepted() {
        val shared = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, sharedPdf)
        val opened = Intent(Intent.ACTION_VIEW).setDataAndType(sharedPdf, "application/pdf")

        assertEquals(SharedItem.Pdf(sharedPdf), SharedItem.from(shared))
        assertEquals(SharedItem.Pdf(sharedPdf), SharedItem.from(opened))
    }

    @Test
    fun aFileAddressIsRefused() {
        // It could point at Chain One's own private files.
        val ownFile = Uri.parse("file:///data/data/io.github.juliegaunt.chainone/databases/chain-one.db")
        val shared = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, ownFile)
        val opened = Intent(Intent.ACTION_VIEW).setDataAndType(ownFile, "application/pdf")

        assertNull(SharedItem.from(shared))
        assertNull(SharedItem.from(opened))
        assertNull(SharedItem.from(Intent(Intent.ACTION_MAIN)))
    }
}
