package io.github.juliegaunt.chainone.data.repository

import android.net.Uri
import io.github.juliegaunt.chainone.data.db.PatternAttachment
import io.github.juliegaunt.chainone.data.db.PdfDao
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.files.FileStore
import kotlinx.coroutines.flow.Flow
import java.io.File

/** A pattern's PDFs: the files in its folder, their names, and where each was being read. */
class PdfRepository(
    private val dao: PdfDao,
    private val files: FileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observePdfs(patternId: Long): Flow<List<PatternAttachment>> = dao.observeAll(patternId)

    suspend fun getPdfs(patternId: Long): List<PatternAttachment> = dao.getAll(patternId)

    /** Copies a PDF the person picked into the pattern. Returns the new PDF's id. */
    suspend fun addPdf(patternId: Long, uri: Uri, name: String): Long =
        insert(patternId, files.importToPattern(patternId, uri, FILE_NAME, EXTENSION), name)

    /** Moves an imported PDF (already on the phone, in the import's folder) into the pattern. */
    suspend fun adoptPdf(patternId: Long, file: File, name: String): Long =
        insert(patternId, files.adoptIntoPattern(patternId, file, FILE_NAME), name)

    suspend fun renamePdf(id: Long, name: String) {
        val clean = cleanName(name)
        if (clean.isNotEmpty()) dao.setName(id, clean)
    }

    /** Removes a PDF from its pattern and deletes the file. */
    suspend fun removePdf(id: Long) {
        val pdf = dao.get(id) ?: return
        dao.delete(id)
        files.delete(pdf.filePath)
    }

    suspend fun setPage(id: Long, page: Int) = dao.setLastPage(id, page.coerceAtLeast(0))

    /** Moves the PDF's row marker, or turns it off with null. */
    suspend fun setRowMarker(id: Long, marker: RowMarker?) = dao.setMarker(id, marker?.page, marker?.top, marker?.height)

    private suspend fun insert(patternId: Long, path: String, name: String): Long =
        dao.insert(
            PatternAttachment(
                patternId = patternId,
                filePath = path,
                displayName = cleanName(name).ifEmpty { FILE_NAME },
                addedAt = clock(),
            ),
        )

    private companion object {
        const val FILE_NAME = "pdf"
        const val EXTENSION = "pdf"
        const val MAX_NAME_LENGTH = 120
        val SPACES = Regex("""\s+""")

        fun cleanName(name: String): String = name.trim().replace(SPACES, " ").take(MAX_NAME_LENGTH).trim()
    }
}
