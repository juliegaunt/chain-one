package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.db.rowMarker
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.repository.PdfRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PdfRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: ChainOneDatabase
    private lateinit var root: File
    private lateinit var patterns: PatternRepository
    private lateinit var pdfs: PdfRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChainOneDatabase::class.java).allowMainThreadQueries().build()
        root = tempFolder.newFolder("files")
        val files = FileStore(root, context.contentResolver)
        patterns = PatternRepository(db.patternDao(), files) { now }
        pdfs = PdfRepository(db.pdfDao(), files) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun adoptPdfMovesTheFileInAndListsIt() = runTest {
        val id = patterns.create(PatternDraft(title = "Tote"))
        val staged = tempFolder.newFile("pattern.pdf").apply { writeText("%PDF") }

        val pdfId = pdfs.adoptPdf(id, staged, "  Sunny   Tote ")

        val pdf = pdfs.getPdfs(id).single()
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
        val id = patterns.create(PatternDraft(title = "Cardigan"))
        pdfs.adoptPdf(id, tempFolder.newFile("a.pdf"), "Pattern")
        now = 2_000L
        pdfs.adoptPdf(id, tempFolder.newFile("b.pdf"), "Charts")

        assertEquals(listOf("Pattern", "Charts"), pdfs.getPdfs(id).map { it.displayName })
    }

    @Test
    fun renamePdfIgnoresBlankNames() = runTest {
        val id = patterns.create(PatternDraft(title = "Hat"))
        val pdfId = pdfs.adoptPdf(id, tempFolder.newFile("hat.pdf"), "hat")

        pdfs.renamePdf(pdfId, " Hat, all sizes ")
        pdfs.renamePdf(pdfId, "   ")

        assertEquals("Hat, all sizes", pdfs.getPdfs(id).single().displayName)
    }

    @Test
    fun removePdfDeletesTheRowAndTheFile() = runTest {
        val id = patterns.create(PatternDraft(title = "Hat"))
        val pdfId = pdfs.adoptPdf(id, tempFolder.newFile("hat.pdf"), "Hat")
        val path = pdfs.getPdfs(id).single().filePath

        pdfs.removePdf(pdfId)

        assertTrue(pdfs.getPdfs(id).isEmpty())
        assertFalse(File(root, path).exists())
    }

    @Test
    fun pageAndRowMarkerAreRemembered() = runTest {
        val id = patterns.create(PatternDraft(title = "Blanket"))
        val pdfId = pdfs.adoptPdf(id, tempFolder.newFile("blanket.pdf"), "Blanket")

        pdfs.setPage(pdfId, 3)
        pdfs.setRowMarker(pdfId, RowMarker(page = 3, top = 0.4f, height = 0.05f))
        assertEquals(3, pdfs.getPdfs(id).single().lastPage)
        assertEquals(RowMarker(3, 0.4f, 0.05f), pdfs.getPdfs(id).single().rowMarker)

        pdfs.setRowMarker(pdfId, null)
        assertNull(pdfs.getPdfs(id).single().rowMarker)
    }

    @Test
    fun deletingPatternRemovesItsPdfs() = runTest {
        val id = patterns.create(PatternDraft(title = "Blanket"))
        val pdfId = pdfs.adoptPdf(id, tempFolder.newFile("blanket.pdf"), "Blanket")

        patterns.delete(id)

        assertNull(db.pdfDao().get(pdfId))
        assertFalse(File(root, "patterns/$id").exists())
    }
}
