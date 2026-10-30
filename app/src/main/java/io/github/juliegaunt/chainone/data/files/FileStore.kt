package io.github.juliegaunt.chainone.data.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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
    suspend fun importToPattern(patternId: Long, uri: Uri, baseName: String, extension: String = extensionFor(uri)): String {
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { input ->
                saveToPattern(patternId, input, baseName, extension)
            } ?: throw IOException("Could not open $uri")
        }
    }

    /**
     * Moves a file that's already on the phone (such as a downloaded photo) into the pattern's
     * folder and returns its new relative path.
     */
    suspend fun adoptIntoPattern(patternId: Long, file: File, baseName: String): String = withContext(Dispatchers.IO) {
        val dir = patternDir(patternId).apply { mkdirs() }
        val target = File(dir, "$baseName-${UUID.randomUUID().toString().take(8)}.${file.extension.ifEmpty { "bin" }}")
        moveOrCopy(file, target)
        target.relativeTo(root).path
    }

    /**
     * Moves a saved copy of a web page (a folder holding `index.html` and its photos) into the
     * pattern's folder, replacing any earlier copy. Returns the relative path of `index.html`.
     */
    suspend fun adoptSnapshot(patternId: Long, folder: File, pageName: String): String = withContext(Dispatchers.IO) {
        val target = File(patternDir(patternId), SNAPSHOT_DIR)
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        moveOrCopy(folder, target)
        File(target, pageName).relativeTo(root).path
    }

    /** Renaming is instant; copying is the fallback if the two places are on different storage. */
    private fun moveOrCopy(source: File, target: File) {
        if (source.renameTo(target)) return
        if (!source.copyRecursively(target, overwrite = true)) throw IOException("Could not copy $source")
        source.deleteRecursively()
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

    /** The name a picked file goes by, such as "Granny Square.pdf", if the app it came from says. */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) { contentResolver.displayName(uri) }

    companion object {
        const val PATTERNS_DIR = "patterns"
        private const val SNAPSHOT_DIR = "snapshot"
    }
}

fun ContentResolver.displayName(uri: Uri): String? =
    try {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
