package io.github.juliegaunt.chainone.data.importer

import android.content.ContentResolver
import android.net.Uri
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.files.FileTooLargeException
import io.github.juliegaunt.chainone.data.files.copyToLimited
import io.github.juliegaunt.chainone.data.files.displayName
import io.github.juliegaunt.chainone.data.pdf.PdfInspector
import io.github.juliegaunt.chainone.data.pdf.PdfOpenException
import io.github.juliegaunt.chainone.data.pdf.PdfProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Stages a PDF for the review screen, like [PatternImporter] does for web pages: the file is
 * copied into the import's folder, checked, and its first page saved as the cover.
 */
class PdfImporter(
    private val staging: ImportStaging,
    private val contentResolver: ContentResolver,
    private val maxBytes: Long = FileStore.MAX_IMPORT_BYTES,
    private val inspector: PdfInspector,
) {

    /**
     * A PDF picked in the file picker or shared from another app. Throws [PdfOpenException] if it
     * can't be shown, [IOException] or [SecurityException] if it can't be read.
     */
    suspend fun importFile(uri: Uri): StagedImport {
        val fileName = withContext(Dispatchers.IO) { contentResolver.displayName(uri) } ?: PdfNames.DEFAULT_FILE_NAME
        return stage(fileName, sourceUrl = "") {
            contentResolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
        }
    }

    /** Stages the PDF that [open] reads. [sourceUrl] is the link it came from, or blank. */
    suspend fun stage(fileName: String, sourceUrl: String, open: () -> InputStream): StagedImport {
        staging.discardStale()
        val folder = staging.newFolder()
        try {
            val pdf = File(folder, PDF_FILE)
            try {
                withContext(Dispatchers.IO) {
                    open().use { input -> pdf.outputStream().use { input.copyToLimited(it, maxBytes) } }
                }
            } catch (e: FileTooLargeException) {
                throw PdfOpenException(PdfProblem.TOO_LARGE, e)
            }
            val cover = File(folder, COVER_FILE)
            inspector.inspect(pdf, cover)?.let { throw PdfOpenException(it) }
            val staged = StagedImport(
                id = folder.name,
                sourceUrl = sourceUrl,
                title = PdfNames.title(fileName),
                coverFile = cover.takeIf { it.isFile },
                pdfFile = pdf,
                pdfName = PdfNames.displayName(fileName),
            )
            staging.save(staged)
            return staged
        } catch (e: Throwable) {
            // Includes cancellation (the person backed out): leave nothing behind.
            withContext(Dispatchers.IO + NonCancellable) { folder.deleteRecursively() }
            throw e
        }
    }

    private companion object {
        const val PDF_FILE = "pattern.pdf"
        const val COVER_FILE = "cover.jpg"
    }
}
