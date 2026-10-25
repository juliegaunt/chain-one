package io.github.juliegaunt.chainone.data.files

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Stores pattern files (images, PDFs, snapshots) in app-private storage under
 * `patterns/<patternId>/`. Callers only ever see paths relative to [root].
 */
class FileStore(
    private val root: File,
    private val contentResolver: ContentResolver,
) {

    fun resolve(relativePath: String): File = File(root, relativePath)

    /** Copies [input] into the pattern's folder and returns the new file's relative path. */
    suspend fun saveToPattern(
        patternId: Long,
        input: InputStream,
        baseName: String,
        extension: String,
    ): String = withContext(Dispatchers.IO) {
        val dir = patternDir(patternId).apply { mkdirs() }
        // A fresh name each time, so a replaced file never collides with (or is cached as) the old one.
        val file = File(dir, "$baseName-${UUID.randomUUID().toString().take(8)}.$extension")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        file.relativeTo(root).path
    }

    /** Copies a file the user picked (a content:// URI) into the pattern's folder. */
    suspend fun importToPattern(patternId: Long, uri: Uri, baseName: String): String {
        val extension = extensionFor(uri)
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { input ->
                saveToPattern(patternId, input, baseName, extension)
            } ?: throw IOException("Could not open $uri")
        }
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        resolve(relativePath).delete()
    }

    suspend fun deletePatternFiles(patternId: Long) = withContext(Dispatchers.IO) {
        patternDir(patternId).deleteRecursively()
    }

    private fun patternDir(patternId: Long) = File(root, "$PATTERNS_DIR/$patternId")

    private fun extensionFor(uri: Uri): String =
        contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
            ?: "bin"

    companion object {
        const val PATTERNS_DIR = "patterns"
    }
}
