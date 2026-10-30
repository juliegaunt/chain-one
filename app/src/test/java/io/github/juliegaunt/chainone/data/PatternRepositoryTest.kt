package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.db.rowMarker
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.importer.StagedImport
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PatternRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: ChainOneDatabase
    private lateinit var root: File
    private lateinit var repository: PatternRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChainOneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = tempFolder.newFolder("files")
        repository = PatternRepository(db.patternDao(), FileStore(root, context.contentResolver)) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createTrimsTextAndDropsBlankUrl() = runTest {
        val id = repository.create(PatternDraft(title = "  Market Bag  ", designer = " Jo ", sourceUrl = "   "))

        val pattern = repository.getPattern(id)!!
        assertEquals("Market Bag", pattern.title)
        assertEquals("Jo", pattern.designer)
        assertNull(pattern.sourceUrl)
        assertEquals(1_000L, pattern.createdAt)
        assertEquals(1_000L, pattern.updatedAt)
    }

    @Test
    fun updateKeepsCreatedTimeAndRefreshesUpdatedTime() = runTest {
        val id = repository.create(PatternDraft(title = "Beanie"))
        now = 5_000L

        repository.update(id, PatternDraft(title = "Ribbed Beanie", hookSize = "6 mm"))

        val pattern = repository.getPattern(id)!!
        assertEquals("Ribbed Beanie", pattern.title)
        assertEquals("6 mm", pattern.hookSize)
        assertEquals(1_000L, pattern.createdAt)
        assertEquals(5_000L, pattern.updatedAt)
    }

    @Test
    fun replacingCoverDeletesOldFile() = runTest {
        val id = repository.create(PatternDraft(title = "Shawl"))

        repository.setCover(id, "first".byteInputStream(), "jpg")
        val firstPath = repository.getPattern(id)!!.coverImagePath!!
        assertEquals("first", File(root, firstPath).readText())

        repository.setCover(id, "second".byteInputStream(), "png")
        val secondPath = repository.getPattern(id)!!.coverImagePath!!

        assertNotEquals(firstPath, secondPath)
        assertTrue(secondPath.startsWith("patterns/$id/"))
        assertEquals("second", File(root, secondPath).readText())
        assertFalse("old cover should be deleted", File(root, firstPath).exists())
    }

    @Test
    fun removeCoverClearsPathAndFile() = runTest {
        val id = repository.create(PatternDraft(title = "Socks"))
        repository.setCover(id, "photo".byteInputStream(), "jpg")
        val path = repository.getPattern(id)!!.coverImagePath!!

        repository.removeCover(id)

        assertNull(repository.getPattern(id)!!.coverImagePath)
        assertFalse(File(root, path).exists())
    }

    @Test
    fun attachImportMovesCoverAndPageCopyIntoThePattern() = runTest {
        val staged = stagedImport(withCover = true)
        val id = repository.create(PatternDraft(title = "Tote"))

        repository.attachImport(id, staged, keepCover = true)

        val pattern = repository.getPattern(id)!!
        assertEquals("cover", File(root, pattern.coverImagePath!!).readText())
        assertEquals("patterns/$id/snapshot/index.html", pattern.snapshotPath)
        assertEquals("<p>page</p>", File(root, pattern.snapshotPath!!).readText())
        assertTrue(File(root, "patterns/$id/snapshot/image-1.jpg").exists())
        assertFalse("files are moved, not copied", staged.coverFile!!.exists())
        assertFalse(staged.snapshotDir!!.exists())
    }

    @Test
    fun attachImportCanSkipTheDownloadedCover() = runTest {
        val staged = stagedImport(withCover = true)
        val id = repository.create(PatternDraft(title = "Tote"))

        repository.attachImport(id, staged, keepCover = false)

        assertNull(repository.getPattern(id)!!.coverImagePath)
        assertTrue(repository.getPattern(id)!!.snapshotPath != null)
    }

    @Test
    fun findsPatternSavedFromAnotherSpellingOfTheLink() = runTest {
        val id = repository.create(PatternDraft(title = "Bag", sourceUrl = "http://www.example.com/bag"))

        assertEquals(id, repository.findBySourceUrl("https://example.com/bag/")?.id)
        assertNull(repository.findBySourceUrl("https://example.com/other"))
    }

    private fun stagedImport(withCover: Boolean): StagedImport {
        val folder = tempFolder.newFolder("staged")
        val cover = if (withCover) File(folder, "cover.jpg").apply { writeText("cover") } else null
        val snapshot = File(folder, "snapshot").apply {
            mkdirs()
            File(this, "index.html").writeText("<p>page</p>")
            File(this, "image-1.jpg").writeText("img")
        }
        return StagedImport(id = "staged", sourceUrl = "https://example.com/tote", coverFile = cover, snapshotDir = snapshot)
    }

    @Test
    fun adoptPdfMovesTheFileInAndListsIt() = runTest {
        val id = repository.create(PatternDraft(title = "Tote"))
        val staged = tempFolder.newFile("pattern.pdf").apply { writeText("%PDF") }

        val pdfId = repository.adoptPdf(id, staged, "  Sunny   Tote ")

        val pdf = repository.getPdfs(id).single()
        assertEquals(pdfId, pdf.id)
        assertEquals("Sunny Tote", pdf.displayName)
        assertTrue(pdf.filePath.startsWith("patterns/$id/") && pdf.filePath.endsWith(".pdf"))
        assertEquals("%PDF", File(root, pdf.filePath).readText())
        assertFalse("files are moved, not copied", staged.exists())
        assertEquals(0, pdf.lastPage)
        assertNull(pdf.rowMarker)
    }

    @Test
    fun pdfsAreListedInTheOrderTheyWereAdded() = runTest {
        val id = repository.create(PatternDraft(title = "Cardigan"))
        repository.adoptPdf(id, tempFolder.newFile("a.pdf"), "Pattern")
        now = 2_000L
        repository.adoptPdf(id, tempFolder.newFile("b.pdf"), "Charts")

        assertEquals(listOf("Pattern", "Charts"), repository.getPdfs(id).map { it.displayName })
    }

    @Test
    fun renamePdfIgnoresBlankNames() = runTest {
        val id = repository.create(PatternDraft(title = "Hat"))
        val pdfId = repository.adoptPdf(id, tempFolder.newFile("hat.pdf"), "hat")

        repository.renamePdf(pdfId, " Hat, all sizes ")
        repository.renamePdf(pdfId, "   ")

        assertEquals("Hat, all sizes", repository.getPdfs(id).single().displayName)
    }

    @Test
    fun removePdfDeletesTheRowAndTheFile() = runTest {
        val id = repository.create(PatternDraft(title = "Hat"))
        val pdfId = repository.adoptPdf(id, tempFolder.newFile("hat.pdf"), "Hat")
        val path = repository.getPdfs(id).single().filePath

        repository.removePdf(pdfId)

        assertTrue(repository.getPdfs(id).isEmpty())
        assertFalse(File(root, path).exists())
    }

    @Test
    fun pageAndRowMarkerAreRemembered() = runTest {
        val id = repository.create(PatternDraft(title = "Blanket"))
        val pdfId = repository.adoptPdf(id, tempFolder.newFile("blanket.pdf"), "Blanket")

        repository.setPdfPage(pdfId, 3)
        repository.setRowMarker(pdfId, RowMarker(page = 3, top = 0.4f, height = 0.05f))
        assertEquals(3, repository.getPdfs(id).single().lastPage)
        assertEquals(RowMarker(3, 0.4f, 0.05f), repository.getPdfs(id).single().rowMarker)

        repository.setRowMarker(pdfId, null)
        assertNull(repository.getPdfs(id).single().rowMarker)
    }

    @Test
    fun deletingPatternRemovesItsPdfs() = runTest {
        val id = repository.create(PatternDraft(title = "Blanket"))
        val pdfId = repository.adoptPdf(id, tempFolder.newFile("blanket.pdf"), "Blanket")

        repository.delete(id)

        assertNull(db.patternDao().getAttachment(pdfId))
        assertFalse(File(root, "patterns/$id").exists())
    }

    @Test
    fun deleteRemovesPatternAndItsFolder() = runTest {
        val id = repository.create(PatternDraft(title = "Blanket"))
        repository.setCover(id, "photo".byteInputStream(), "jpg")
        val folder = File(root, "patterns/$id")
        assertTrue(folder.exists())

        repository.delete(id)

        assertNull(repository.getPattern(id))
        assertFalse(folder.exists())
    }
}
