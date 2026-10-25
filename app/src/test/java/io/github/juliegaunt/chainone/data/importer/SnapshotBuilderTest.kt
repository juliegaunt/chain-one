package io.github.juliegaunt.chainone.data.importer

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotBuilderTest {

    @Test
    fun buildsAPlainPageWithAbsoluteImagesAndNoScripts() {
        val content = Jsoup.parse(
            """
            <div class="entry-content" onclick="evil()">
              <p style="color:red">Round 1: <a href="/stitches/sc">sc</a> around.</p>
              <img src="data:image/gif;base64,AAAA" data-src="/img/photo.jpg" width="600" height="400">
              <img src="data:image/gif;base64,AAAA">
              <iframe src="https://video.example/embed"></iframe>
              <script>evil()</script>
              <a href="javascript:evil()">bad link</a>
            </div>
            """.trimIndent(),
            "https://example.com/pattern/",
        ).selectFirst("div")!!

        val page = SnapshotBuilder.build(content, "Tote", "https://example.com/pattern/", "Saved from example.com")
        val html = page.outerHtml()

        assertEquals("Tote", page.title())
        assertEquals("Tote", page.selectFirst("header h1")!!.text())
        assertEquals("https://example.com/pattern/", page.selectFirst("header a")!!.attr("href"))
        assertEquals(listOf("https://example.com/img/photo.jpg"), page.select("img").map { it.attr("src") })
        assertEquals("https://example.com/stitches/sc", page.select("a").first { it.text() == "sc" }.attr("href"))
        assertFalse(html.contains("evil"))
        assertFalse(html.contains("iframe"))
        assertFalse(html.contains("color:red"))
        assertFalse(page.select("img").first()!!.hasAttr("width"))
        assertTrue(page.select("style").isNotEmpty())
        assertTrue(page.text().contains("bad link"))
    }
}
