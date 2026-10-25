package io.github.juliegaunt.chainone.data.importer

import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
class PatternImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Serves pages and files from memory instead of the internet. */
    private class FakeWebClient : WebClient {
        val pages = mutableMapOf<String, String>()
        val files = mutableMapOf<String, Download>()
        val fetched = mutableListOf<String>()
        var failWith: ImportProblem? = null

        override suspend fun fetchPage(url: String): Document {
            fetched += url
            failWith?.let { throw ImportException(it) }
            return Jsoup.parse(pages.getValue(url), url)
        }

        override suspend fun download(url: String, maxBytes: Int): Download? = files[url]
    }

    private val web = FakeWebClient()
    private lateinit var stagingRoot: File
    private lateinit var staging: ImportStaging
    private lateinit var importer: PatternImporter

    @Before
    fun setUp() {
        stagingRoot = tempFolder.newFolder("imports")
        staging = ImportStaging(stagingRoot)
        importer = PatternImporter(web, staging) { host -> "Saved from $host" }
    }

    private fun jpeg(text: String) = Download(text.toByteArray(), "image/jpeg")

    @Test
    fun importStagesFieldsCoverAndPageCopyWithLocalPhotos() = runTest {
        web.pages["https://example.com/bag"] = """
            <html><head>
              <meta property="og:title" content="Market Bag">
              <meta property="og:image" content="https://example.com/cover.jpg">
            </head><body><article>
              <p>A roomy bag for the farmers market, worked in rounds from the bottom up with a simple mesh.</p>
              <p><img src="/one.jpg"><img src="/broken.jpg"><img src="/one.jpg"></p>
              <h2>Pattern</h2>
              <p>Round 1: 8 sc in magic ring.</p><p>Round 2: 2 sc in each st around.</p>
            </article></body></html>
        """.trimIndent()
        web.files["https://example.com/cover.jpg"] = jpeg("cover")
        web.files["https://example.com/one.jpg"] = jpeg("one")

        // Typed as http: still fetched over https, and the page's own address is kept.
        val staged = importer.import("http://example.com/bag")

        assertEquals(listOf("https://example.com/bag"), web.fetched)
        assertEquals("https://example.com/bag", staged.sourceUrl)
        assertEquals("Market Bag", staged.title)
        assertTrue(staged.foundInstructions)
        assertEquals("cover", staged.coverFile!!.readText())

        val page = File(staged.snapshotDir, PatternImporter.SNAPSHOT_PAGE)
        val html = Jsoup.parse(page, "UTF-8")
        // The photo that failed is dropped; the repeated one is downloaded once and used twice.
        assertEquals(listOf("image-1.jpg", "image-1.jpg"), html.select("article img, body img").map { it.attr("src") })
        assertEquals("one", File(staged.snapshotDir, "image-1.jpg").readText())
        assertTrue(html.text().contains("Saved from example.com"))

        assertEquals(staged, staging.load(staged.id))
    }

    @Test
    fun failedDownloadLeavesNothingBehind() = runTest {
        web.failWith = ImportProblem.BLOCKED

        try {
            importer.import("https://example.com/blocked")
            fail("expected ImportException")
        } catch (e: ImportException) {
            assertEquals(ImportProblem.BLOCKED, e.problem)
        }
        assertTrue(stagingRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun linkOnlyKeepsJustTheLink() = runTest {
        val staged = importer.linkOnly("https://example.com/x")

        val loaded = staging.load(staged.id)!!
        assertEquals("https://example.com/x", loaded.sourceUrl)
        assertFalse(loaded.downloaded)
        assertNull(loaded.coverFile)
        assertNull(loaded.snapshotDir)
    }

    @Test
    fun discardDeletesTheFolderAndRejectsPathTricks() = runTest {
        val staged = importer.linkOnly("https://example.com/x")
        staging.discard(staged.id)
        assertNull(staging.load(staged.id))

        try {
            staging.load("../escape")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun staleReviewsAreCleanedUp() = runTest {
        var now = 0L
        val staging = ImportStaging(stagingRoot) { now }
        val old = File(stagingRoot, "old").apply { mkdirs(); setLastModified(1_000L) }
        val fresh = File(stagingRoot, "fresh").apply { mkdirs(); setLastModified(90_000_000L) }
        now = 100_000_000L

        staging.discardStale()

        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}
