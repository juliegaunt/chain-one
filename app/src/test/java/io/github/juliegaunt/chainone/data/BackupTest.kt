package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.backup.BackupContents
import io.github.juliegaunt.chainone.data.backup.BackupException
import io.github.juliegaunt.chainone.data.backup.BackupJson
import io.github.juliegaunt.chainone.data.backup.BackupProblem
import io.github.juliegaunt.chainone.data.backup.LibraryBackup
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.db.LibrarySnapshot
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.PatternTag
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.importer.StagedImport
import io.github.juliegaunt.chainone.data.repository.CounterRepository
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.settings.AppSettings
import io.github.juliegaunt.chainone.data.settings.LibraryLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Backs up a library with one of everything, then restores it as on a new phone (an empty
 * database and files folder) and as a replacement for an existing library.
 */
@RunWith(RobolectricTestRunner::class)
class BackupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = AppSettings(context)
    private var now = 1_000L

    /** One phone's worth of app: database, files folder and the classes that use them. */
    private inner class Phone {
        val db: ChainOneDatabase = Room.inMemoryDatabaseBuilder(context, ChainOneDatabase::class.java).allowMainThreadQueries().build()
        val root: File = tempFolder.newFolder()
        val patterns = PatternRepository(db.patternDao(), FileStore(root, context.contentResolver)) { now++ }
        val counters = CounterRepository(db.counterDao(), db.patternDao()) { now++ }
        val backup = LibraryBackup(db.backupDao(), root, settings, appVersion = "test") { 1_700_000_000_000L }
    }

    private val phones = mutableListOf<Phone>()
    private lateinit var old: Phone

    @Before
    fun setUp() {
        old = phone()
    }

    @After
    fun tearDown() {
        phones.forEach { it.db.close() }
    }

    private fun phone() = Phone().also { phones += it }

    /** Two patterns with tags, a cover, a PDF with a row marker, a saved web page and counters. */
    private suspend fun fillLibrary(phone: Phone) {
        val blanket = phone.patterns.create(
            PatternDraft(title = "Blanket", designer = "Jo", notes = "For the baby", status = PatternStatus.IN_PROGRESS, tags = listOf("baby", "Blanket")),
        )
        phone.patterns.setFavorite(blanket, true)
        phone.patterns.setCover(blanket, "cover".byteInputStream(), "jpg")
        val pdf = phone.patterns.adoptPdf(blanket, tempFolder.newFile("blanket.pdf").apply { writeText("%PDF blanket") }, "Blanket chart")
        phone.patterns.setPdfPage(pdf, 2)
        phone.patterns.setRowMarker(pdf, RowMarker(page = 2, top = 0.4f, height = 0.035f))
        val row = phone.counters.add(blanket, "Row")
        repeat(12) { phone.counters.increment(row) }
        phone.counters.jumpToRow(blanket, "Row", value = 12, section = 1)
        val repeat = phone.counters.add(blanket, "Sleeve", repeatLength = 4, repeatTimes = 5)
        repeat(7) { phone.counters.increment(repeat) }

        val tote = phone.patterns.create(PatternDraft(title = "Tote", sourceUrl = "https://example.com/tote", tags = listOf("baby")))
        val page = tempFolder.newFolder().apply {
            File(this, "index.html").writeText("<p>tote</p>")
            File(this, "image-1.jpg").writeText("photo")
        }
        phone.patterns.attachImport(tote, StagedImport(id = "x", sourceUrl = "https://example.com/tote", snapshotDir = page), keepCover = false)
    }

    private suspend fun backupOf(phone: Phone): ByteArray =
        ByteArrayOutputStream().also { phone.backup.export(it) }.toByteArray()

    /** Every file under `patterns/`, by path, with its contents. */
    private fun filesOf(phone: Phone): Map<String, String> =
        File(phone.root, "patterns").walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(phone.root).invariantSeparatorsPath to it.readText() }

    @Test
    fun aBackupRestoresEverythingOnANewPhone() = runTest {
        fillLibrary(old)
        settings.restore(AppSettings.Snapshot(LibraryLayout.LIST, countWithVolumeKeys = true, keepScreenOn = false))
        val zip = backupOf(old)

        // A new phone starts with the default settings.
        settings.restore(AppSettings.Snapshot(LibraryLayout.GRID, countWithVolumeKeys = false, keepScreenOn = true))
        val new = phone()
        val summary = new.backup.inspect(zip.inputStream())
        assertEquals(2, summary.patternCount)
        assertEquals(1, summary.pdfCount)
        assertEquals(1_700_000_000_000L, summary.createdAt)

        new.backup.restore(zip.inputStream())

        assertEquals(old.db.backupDao().snapshot(), new.db.backupDao().snapshot())
        assertEquals(filesOf(old), filesOf(new))
        assertTrue(filesOf(new).keys.any { it.endsWith("/snapshot/image-1.jpg") })
        assertEquals(AppSettings.Snapshot(LibraryLayout.LIST, countWithVolumeKeys = true, keepScreenOn = false), settings.snapshot())
        // Counters come back; their undo history doesn't.
        val blanket = new.db.backupDao().allPatterns().single { it.title == "Blanket" }
        assertEquals(listOf(12, 7), new.counters.observeCounters(blanket.id).first().map { it.value })
        assertFalse(new.counters.observeCanUndo(blanket.id).first())
        assertFalse(File(new.root, ".restore").exists())
    }

    @Test
    fun restoringReplacesTheLibraryAndItsFiles() = runTest {
        fillLibrary(old)
        val zip = backupOf(old)
        val other = phone()
        val mine = other.patterns.create(PatternDraft(title = "Mine"))
        other.patterns.setCover(mine, "mine".byteInputStream(), "jpg")
        val myFiles = filesOf(other).keys

        other.backup.restore(zip.inputStream())

        assertEquals(listOf("Blanket", "Tote"), other.db.backupDao().allPatterns().map { it.title })
        assertTrue(myFiles.none { File(other.root, it).exists() })
        assertEquals(filesOf(old), filesOf(other))
        assertTrue(other.root.list()!!.none { it.startsWith(".restore") })
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() = runTest {
        assertRefused(BackupProblem.NOT_A_BACKUP, "just some text".toByteArray())
        assertRefused(BackupProblem.NOT_A_BACKUP, zipOf("notes.txt" to "hello".toByteArray()))
        assertRefused(BackupProblem.NOT_A_BACKUP, zipOf(LibraryBackup.MANIFEST to """{"format": "something-else"}""".toByteArray()))
    }

    @Test
    fun aBackupFromANewerVersionIsRefused() = runTest {
        val manifest = """{"format": "${BackupJson.FORMAT}", "version": ${BackupJson.VERSION + 1}, "createdAt": 1}"""
        assertRefused(BackupProblem.NEWER_VERSION, zipOf(LibraryBackup.MANIFEST to manifest.toByteArray()))
    }

    @Test
    fun aCutShortBackupIsRefusedWhereverItEnds() = runTest {
        fillLibrary(old)
        val zip = backupOf(old)
        // Wherever the file stops once backup.json has begun (inside it, inside a photo, between
        // two files, mid-header), it's refused as damaged and nothing changes. The zip's own
        // index after the last file isn't needed: every file is complete by then.
        val manifestBegun = 30 + LibraryBackup.MANIFEST.length
        val indexStart = ByteBuffer.wrap(zip, zip.size - 22 + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int
        for (length in (manifestBegun until indexStart step 7) + (indexStart - 1)) {
            assertRefused(BackupProblem.DAMAGED, zip.copyOf(length), inspectToo = false)
        }
    }

    @Test
    fun aZipThatWritesOutsideItsFolderIsRefused() = runTest {
        val outside = File(tempFolder.root, "escaped.txt")
        val zip = zipOf(
            // Listed, so it's the path itself that's refused.
            LibraryBackup.MANIFEST to manifest(emptyLibrary, files = mapOf("../../escaped.txt" to 6L)),
            "files/../../escaped.txt" to "gotcha".toByteArray(),
        )
        assertRefused(BackupProblem.DAMAGED, zip, inspectToo = false)
        assertFalse(outside.exists())
    }

    @Test
    fun aFileTheBackupDoesNotListIsRefused() = runTest {
        val zip = zipOf(
            LibraryBackup.MANIFEST to manifest(emptyLibrary),
            "files/patterns/1/extra.jpg" to "sneaked in".toByteArray(),
        )
        assertRefused(BackupProblem.DAMAGED, zip, inspectToo = false)
    }

    @Test
    fun aFileBiggerThanListedIsRefused() = runTest {
        // How a zip bomb would look: tiny on the list, huge when unpacked.
        val zip = zipOf(
            LibraryBackup.MANIFEST to manifest(emptyLibrary, files = mapOf("patterns/1/cover.jpg" to 10L)),
            "files/patterns/1/cover.jpg" to ByteArray(100_000),
        )
        assertRefused(BackupProblem.DAMAGED, zip, inspectToo = false)
    }

    @Test
    fun aBackupWithoutItsFileListIsRefused() = runTest {
        val manifest = """{"format": "${BackupJson.FORMAT}", "version": ${BackupJson.VERSION}, "createdAt": 1}"""
        assertRefused(BackupProblem.DAMAGED, zipOf(LibraryBackup.MANIFEST to manifest.toByteArray()))
    }

    @Test
    fun aPathOutsideTheLibraryFolderIsRefused() = runTest {
        fillLibrary(old)
        val snapshot = old.db.backupDao().snapshot()
        val bad = snapshot.copy(patterns = snapshot.patterns.map { it.copy(coverImagePath = "../../shared_prefs/settings.xml") })
        assertRefused(BackupProblem.DAMAGED, zipOf(LibraryBackup.MANIFEST to manifest(bad)), inspectToo = false)
    }

    @Test
    fun aBackupTheDatabaseRejectsLeavesTheLibraryAsItWas() = runTest {
        fillLibrary(old)
        val snapshot = old.db.backupDao().snapshot()
        // A tag link to a tag that isn't in the backup.
        val bad = snapshot.copy(patternTags = snapshot.patternTags + PatternTag(snapshot.patterns.first().id, 999))
        assertRefused(BackupProblem.DAMAGED, zipOf(LibraryBackup.MANIFEST to manifest(bad)), inspectToo = false)
    }

    /** Neither inspecting nor restoring [zip] works, and the existing library is untouched. */
    private suspend fun assertRefused(problem: BackupProblem, zip: ByteArray, inspectToo: Boolean = true) {
        val library = old.db.backupDao().snapshot()
        val files = filesOf(old)
        if (inspectToo) assertProblem(problem) { old.backup.inspect(zip.inputStream()) }
        assertProblem(problem) { old.backup.restore(zip.inputStream()) }
        assertEquals(library, old.db.backupDao().snapshot())
        assertEquals(files, filesOf(old))
        assertTrue(old.root.list()!!.none { it.startsWith(".restore") })
    }

    private suspend fun assertProblem(problem: BackupProblem, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $problem")
        } catch (e: BackupException) {
            assertEquals(problem, e.problem)
        }
    }

    private val emptyLibrary = LibrarySnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    private fun manifest(library: LibrarySnapshot, files: Map<String, Long> = emptyMap()): ByteArray =
        BackupJson.write(BackupContents(1L, library, settings.snapshot(), files), "test").toByteArray()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
