package io.github.juliegaunt.chainone.data.importer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.pdf.PdfOpenException
import io.github.juliegaunt.chainone.data.pdf.PdfProblem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Robolectric can't draw PDFs, so a fake check stands in for Android's PDF reader. */
@RunWith(RobolectricTestRunner::class)
class PdfImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var stagingRoot: File
    private lateinit var staging: ImportStaging
    private lateinit var importer: PdfImporter
    private var problem: PdfProblem? = null
    private var writesCover = true

    @Before
    fun setUp() {
        stagingRoot = tempFolder.newFolder("imports")
        staging = ImportStaging(stagingRoot)
        val context = ApplicationProvider.getApplicationContext<Context>()
        importer = PdfImporter(staging, context.contentResolver) { _, cover ->
            if (writesCover && problem == null) cover.writeText("page 1")
            problem
        }
    }

    @Test
    fun stagesThePdfWithATitleFromItsNameAndPageOneAsCover() = runTest {
        val staged = importer.stage("Granny_Square.pdf", sourceUrl = "") { "%PDF-1.7 pattern".byteInputStream() }

        assertEquals("Granny Square", staged.title)
        assertEquals("Granny_Square", staged.pdfName)
        assertEquals("", staged.sourceUrl)
        assertEquals("%PDF-1.7 pattern", staged.pdfFile!!.readText())
        assertEquals("page 1", staged.coverFile!!.readText())
        assertNull(staged.snapshotDir)
        // Survives the app being closed mid-review.
        assertEquals(staged, staging.load(staged.id))
    }

    @Test
    fun noCoverWhenPageOneCouldNotBeDrawn() = runTest {
        writesCover = false

        val staged = importer.stage("Hat.pdf", sourceUrl = "") { "%PDF".byteInputStream() }

        assertNull(staged.coverFile)
        assertNull(staging.load(staged.id)!!.coverFile)
    }

    @Test
    fun pdfThatCannotBeShownLeavesNothingBehind() = runTest {
        problem = PdfProblem.PROTECTED

        try {
            importer.stage("Locked.pdf", sourceUrl = "") { "%PDF".byteInputStream() }
            fail("expected PdfOpenException")
        } catch (e: PdfOpenException) {
            assertEquals(PdfProblem.PROTECTED, e.problem)
        }
        assertTrue(stagingRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun aPdfOverTheSizeLimitIsRefused() = runTest {
        val small = PdfImporter(staging, ApplicationProvider.getApplicationContext<Context>().contentResolver, maxBytes = 10) { _, _ -> null }

        try {
            small.stage("Huge.pdf", sourceUrl = "") { ByteArray(11).inputStream() }
            fail("expected PdfOpenException")
        } catch (e: PdfOpenException) {
            assertEquals(PdfProblem.TOO_LARGE, e.problem)
        }
        assertTrue(stagingRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun importsAPickedFile() = runTest {
        val picked = tempFolder.newFile("Sunny_Tote.pdf").apply { writeText("%PDF tote") }

        val staged = importer.importFile(Uri.fromFile(picked))

        assertEquals("Sunny Tote", staged.title)
        assertEquals("%PDF tote", staged.pdfFile!!.readText())
    }
}
