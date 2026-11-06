package io.github.juliegaunt.chainone.data.importer

import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedPageEditorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** A page saved before promotions were hidden on import: local photos, no tidy marker. */
    private fun oldPage(): File = File(tempFolder.newFolder("snapshot"), "index.html").apply {
        writeText(
            """
            <html><head><title>Cozy Cowl</title></head><body>
            <p>Intro</p>
            <figure><img src="image-1.jpg" alt="Cozy Cowl"></figure>
            <figure><img src="image-2.jpg" alt="Link to purchase the Cozy Cowl pdf on Etsy."></figure>
            <p>Round 1: Ch 80.</p>
            </body></html>
            """.trimIndent(),
        )
    }

    @Test
    fun anOldPageIsTidiedOnceAndShowingAllSticks() = runTest {
        val page = oldPage()

        assertTrue(SavedPageEditor.tidyOnce(page))
        assertEquals(1, SavedPageEditor.hiddenCount(page))
        assertFalse("only once", SavedPageEditor.tidyOnce(page))

        assertTrue(SavedPageEditor.showAll(page))
        assertEquals(0, SavedPageEditor.hiddenCount(page))
        assertFalse("shown stays shown", SavedPageEditor.tidyOnce(page))
        assertEquals(0, SavedPageEditor.hiddenCount(page))
    }

    @Test
    fun aPageTidiedBeforeLeadInsWereHiddenGetsThemHiddenOnce() = runTest {
        val page = File(tempFolder.newFolder("tidied"), "index.html").apply {
            writeText(
                """
                <html><head><meta name="chain-one-tidied" content="1"></head><body>
                <h2>Want to pin it for later?</h2>
                <figure data-chain-one-hidden=""><img src="image-2.jpg"></figure>
                <p>Round 1: Ch 80.</p>
                </body></html>
                """.trimIndent(),
            )
        }

        assertTrue(SavedPageEditor.tidyOnce(page))
        assertEquals(2, SavedPageEditor.hiddenCount(page))
        assertFalse("only once", SavedPageEditor.tidyOnce(page))
    }

    @Test
    fun anOldTidiedPageWithEverythingShownStaysShown() = runTest {
        val page = File(tempFolder.newFolder("shown"), "index.html").apply {
            writeText(
                """<html><head><meta name="chain-one-tidied" content="1"></head><body>
                <h2>Want to pin it for later?</h2><figure><img src="image-2.jpg" alt="Pin it"></figure></body></html>""",
            )
        }

        SavedPageEditor.tidyOnce(page)

        assertEquals(0, SavedPageEditor.hiddenCount(page))
    }

    @Test
    fun hidingAPictureByHandAlsoHidesItsLeadIn() = runTest {
        val page = File(tempFolder.newFolder("byhand"), "index.html").apply {
            writeText("""<html><head></head><body><p>Intro</p><p>Click the image below to save it!</p><figure><img src="image-5.jpg"></figure></body></html>""")
        }

        SavedPageEditor.hidePicture(page, "image-5.jpg")

        assertEquals(2, SavedPageEditor.hiddenCount(page))
    }

    @Test
    fun picturesCanBeHiddenByHandAndTheRuleHidesThem() = runTest {
        val page = oldPage()

        assertTrue(SavedPageEditor.hidePicture(page, "image-1.jpg"))
        assertFalse("no such picture", SavedPageEditor.hidePicture(page, "image-9.jpg"))

        val doc = Jsoup.parse(page, "UTF-8")
        assertTrue(doc.selectFirst("img[src=image-1.jpg]")!!.parent()!!.hasAttr(Promotions.HIDDEN))
        assertTrue(doc.getElementById("chain-one-hidden")!!.data().contains("display: none"))
        assertTrue(doc.selectFirst("p")!!.text() == "Intro")
    }

    @Test
    fun aNewlySavedPageAlreadyHidesItsPromotions() {
        val content = PageContent.clean(Jsoup.parse(javaClass.getResource("/pages/promotions.html")!!.readText()).selectFirst("article")!!)

        val page = SnapshotBuilder.build(content, "Cozy Cowl", "https://example.com/cozy-cowl/", "Saved from example.com")

        assertEquals(7, page.select("[${Promotions.HIDDEN}]").size)
        assertTrue(page.getElementById("chain-one-hidden") != null)
        assertTrue(page.selectFirst("meta[name=chain-one-tidied]") != null)
    }
}
