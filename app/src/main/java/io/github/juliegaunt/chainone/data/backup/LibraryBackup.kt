package io.github.juliegaunt.chainone.data.backup

import android.database.SQLException
import io.github.juliegaunt.chainone.data.db.BackupDao
import io.github.juliegaunt.chainone.data.db.LibrarySnapshot
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Why a file can't be restored. Each has its own message. */
enum class BackupProblem {
    /** Not a zip, or a zip without Chain One's `backup.json`. */
    NOT_A_BACKUP,

    /** Made by a newer version of Chain One that this one can't read. */
    NEWER_VERSION,

    /** A Chain One backup, but cut short or broken. */
    DAMAGED,
}

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : IOException(problem.name, cause)

/** What a backup holds, shown before restoring it. */
data class BackupSummary(val createdAt: Long, val patternCount: Int, val pdfCount: Int)

/**
 * Backs the whole library up to one zip file and restores it. The zip holds `backup.json` (every
 * pattern, tag, PDF and counter, plus the settings; not the undo history) and, under `files/`,
 * each pattern's folder as it is in app storage. Restoring replaces the library, all or nothing.
 */
class LibraryBackup(
    private val dao: BackupDao,
    /** The app's files folder, which holds `patterns/`. */
    private val filesRoot: File,
    private val settings: AppSettings,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun patternCount(): Int = dao.patternCount()

    /** Writes a backup of everything to [output], and closes it. */
    suspend fun export(output: OutputStream) {
        val library = dao.snapshot()
        withContext(Dispatchers.IO) {
            val files = library.patterns.flatMap { pattern ->
                File(filesRoot, "${FileStore.PATTERNS_DIR}/${pattern.id}").walkTopDown().filter { it.isFile }.toList()
            }
            val listed = files.associate { it.relativeTo(filesRoot).invariantSeparatorsPath to it.length() }
            val json = BackupJson.write(BackupContents(clock(), library, settings.snapshot(), listed), appVersion)
            ZipOutputStream(output.buffered()).use { zip ->
                // First, so a restore can check the backup before unpacking any files.
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(json.toByteArray())
                zip.closeEntry()
                for (file in files) {
                    // Photos and PDFs are already compressed; squeezing them again only takes time.
                    zip.setLevel(if (file.extension.lowercase() in COMPRESSED) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                    zip.putNextEntry(ZipEntry(FILES_PREFIX + file.relativeTo(filesRoot).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Reads what [input] holds without changing anything. Throws [BackupException] if it can't be restored. */
    suspend fun inspect(input: InputStream): BackupSummary = withContext(Dispatchers.IO) {
        ZipInputStream(input.buffered()).use { zip ->
            val contents = readZip(zip, unpackInto = null)
            BackupSummary(contents.createdAt, contents.library.patterns.size, contents.library.attachments.size)
        }
    }

    /**
     * Replaces the whole library (patterns, files, counters and settings) with the backup in
     * [input]. The files are unpacked and checked first; if anything fails, the library is left
     * exactly as it was. The undo history starts empty.
     */
    suspend fun restore(input: InputStream) {
        val staging = File(filesRoot, RESTORE_DIR)
        try {
            val contents = withContext(Dispatchers.IO) {
                staging.deleteRecursively()
                staging.mkdirs()
                ZipInputStream(input.buffered()).use { zip -> readZip(zip, unpackInto = staging) }
            }
            if (!contents.library.hasSafePaths() || !withContext(Dispatchers.IO) { contents.allFilesIn(staging) }) {
                throw BackupException(BackupProblem.DAMAGED)
            }
            swapIn(contents, File(staging, FileStore.PATTERNS_DIR))
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { staging.deleteRecursively() }
        }
    }

    /**
     * Reads `backup.json` and, with [unpackInto], writes the files there. Without it, stops as soon
     * as `backup.json` has been read.
     */
    private fun readZip(zip: ZipInputStream, unpackInto: File?): BackupContents {
        var contents: BackupContents? = null
        // Once backup.json turns up, it's a Chain One backup: anything wrong after that is damage.
        var isBackup = false
        try {
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == MANIFEST -> {
                        isBackup = true
                        contents = BackupJson.read(readLimited(zip, MAX_MANIFEST_BYTES).decodeToString())
                        if (unpackInto == null) break
                    }
                    unpackInto != null && entry.name.startsWith(FILES_PREFIX) && !entry.isDirectory -> {
                        // A file isn't trusted until its manifest has been checked.
                        if (contents == null) throw BackupException(BackupProblem.NOT_A_BACKUP)
                        val target = safeTarget(unpackInto, entry.name.removePrefix(FILES_PREFIX))
                            ?: throw BackupException(BackupProblem.DAMAGED)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        } catch (e: ZipException) {
            throw BackupException(if (isBackup) BackupProblem.DAMAGED else BackupProblem.NOT_A_BACKUP, e)
        } catch (e: EOFException) {
            throw BackupException(if (isBackup) BackupProblem.DAMAGED else BackupProblem.NOT_A_BACKUP, e)
        }
        return contents ?: throw BackupException(BackupProblem.NOT_A_BACKUP)
    }

    /**
     * Puts the unpacked files where the library's are and the backup's rows in the database. The
     * old files are only deleted once the database has taken the new rows; if it doesn't, both
     * go back as they were.
     */
    private suspend fun swapIn(contents: BackupContents, incoming: File) {
        val live = File(filesRoot, FileStore.PATTERNS_DIR)
        val previous = File(filesRoot, PREVIOUS_DIR)
        withContext(Dispatchers.IO) {
            incoming.mkdirs()
            previous.deleteRecursively()
            if (live.exists() && !live.renameTo(previous)) throw IOException("Could not move the current files aside")
            if (!incoming.renameTo(live)) {
                previous.renameTo(live)
                throw IOException("Could not move the restored files in")
            }
        }
        try {
            dao.replaceAll(contents.library)
        } catch (e: Throwable) {
            withContext(Dispatchers.IO + NonCancellable) {
                live.deleteRecursively()
                previous.renameTo(live)
            }
            // The database turned the backup's rows down, e.g. a PDF of a pattern that isn't in it.
            if (e is SQLException) throw BackupException(BackupProblem.DAMAGED, e)
            throw e
        }
        settings.restore(contents.settings)
        withContext(Dispatchers.IO + NonCancellable) { previous.deleteRecursively() }
    }

    /** Where [path] goes inside [root], or null if it would land outside it (a zip trick). */
    private fun safeTarget(root: File, path: String): File? {
        val target = File(root, path)
        return target.takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    /**
     * Whether every file the backup lists was unpacked, at its full size. A zip cut short between
     * two files reads as if it simply ended there, so this is how a partial one is caught.
     */
    private fun BackupContents.allFilesIn(staging: File): Boolean =
        files?.all { (path, size) -> safeTarget(staging, path)?.let { it.isFile && it.length() == size } == true } ?: true

    /** File paths in the database must point inside the library's own folder. */
    private fun LibrarySnapshot.hasSafePaths(): Boolean {
        val paths = patterns.flatMap { listOfNotNull(it.coverImagePath, it.snapshotPath) } +
            images.map { it.filePath } + attachments.map { it.filePath }
        return paths.all { path ->
            path.startsWith(FileStore.PATTERNS_DIR + "/") && path.split('/').none { it == ".." || it.isEmpty() }
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > limit) throw BackupException(BackupProblem.DAMAGED)
        }
        return out.toByteArray()
    }

    companion object {
        const val MANIFEST = "backup.json"
        const val FILES_PREFIX = "files/"

        /** Inside the files folder, so moving them into place is a quick rename. */
        private const val RESTORE_DIR = ".restore"
        private const val PREVIOUS_DIR = ".restore-previous"
        private const val MAX_MANIFEST_BYTES = 64 * 1024 * 1024
        private val COMPRESSED = setOf("jpg", "jpeg", "png", "webp", "gif", "pdf")
    }
}
