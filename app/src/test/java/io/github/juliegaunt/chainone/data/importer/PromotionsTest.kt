package io.github.juliegaunt.chainone.data.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionsTest {

    private val page = Jsoup.parse(javaClass.getResource("/pages/promotions.html")!!.readText(), "https://example.com/cozy-cowl/")
    private val content = PageContent.clean(page.selectFirst("article")!!)

    private fun hidden(element: Element) = element.closest("[${Promotions.HIDDEN}]") != null
    private fun img(fileName: String) = content.select("img").single { img -> listOf("data-lazy-src", "src").any { img.attr(it).endsWith(fileName) } }

    @Test
    fun hidesBuyAndPinGraphics() {
        // By its description, by its link to Pinterest, and by its file name.
        assertTrue(hidden(img("Shop-Promo.jpg")))
        assertTrue(hidden(img("Cowl-2.jpg")))
        assertTrue(hidden(img("pin-it-button.png")))
        // The figure goes with it, so no empty frame is left.
        assertTrue(img("Shop-Promo.jpg").parent()!!.hasAttr(Promotions.HIDDEN))
    }

    @Test
    fun keepsThePatternPhotos() {
        assertFalse(hidden(img("IMG_1234.jpg")))
        assertFalse(hidden(img("IMG_1240.jpg")))
    }

    @Test
    fun hidesAShortBuyLineButNotASentenceThatMentionsAShop() {
        val buy = content.select("p").single { it.text().startsWith("Click here") }
        val ravelry = content.select("p").single { it.text().contains("Ravelry") }

        assertTrue(buy.hasAttr(Promotions.HIDDEN))
        assertFalse(hidden(ravelry))
    }

    @Test
    fun hiddenLinesStayOutOfTheInstructions() {
        val markup = PageContent.toMarkup(content)

        assertFalse(markup.contains("Click here to buy"))
        assertTrue(markup.contains("Round 1: Ch 80"))
        assertTrue(markup.contains("Ravelry"))
    }

    @Test
    fun neverHidesTheWholeArticle() {
        val article = Jsoup.parse("""<div><img src="promo.jpg" alt="Link to purchase the pdf"></div>""").selectFirst("div")!!

        Promotions.hideIn(article)

        assertFalse(article.hasAttr(Promotions.HIDDEN))
        assertTrue(article.selectFirst("img")!!.hasAttr(Promotions.HIDDEN))
    }
}
