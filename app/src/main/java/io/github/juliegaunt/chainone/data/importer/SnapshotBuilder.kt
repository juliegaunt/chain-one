package io.github.juliegaunt.chainone.data.importer

import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/**
 * Builds the saved copy of a pattern page: the cleaned article on a plain, readable page with
 * no scripts, styles, forms or embeds. Image `src`s are absolute web addresses at this point;
 * [PatternImporter] downloads them and points each `src` at the local file.
 */
object SnapshotBuilder {

    private val SAFELIST: Safelist = Safelist.relaxed()
        .addTags("figure", "figcaption", "hr")
        .removeAttributes("img", "width", "height", "align")
        .removeAttributes("table", "width")
        .removeAttributes("col", "width")
        .removeAttributes("colgroup", "width")
        .removeAttributes("td", "width")
        .removeAttributes("th", "width")

    private val STYLE = """
        :root { color-scheme: light dark; }
        body { font-family: sans-serif; font-size: 17px; line-height: 1.55; margin: 0 auto; padding: 8px 16px 32px;
               max-width: 760px; color: #201A1B; background: #FFFBFF; overflow-wrap: break-word; }
        a { color: #D14D72; }
        img { max-width: 100%; height: auto; border-radius: 8px; }
        figure { margin: 16px 0; }
        table { border-collapse: collapse; width: 100%; }
        td, th { border: 1px solid #D6C2C4; padding: 4px 8px; text-align: left; }
        header { border-bottom: 1px solid #D6C2C4; margin-bottom: 16px; }
        header h1 { font-size: 24px; line-height: 1.25; margin: 16px 0 4px; }
        header p { color: #524345; font-size: 14px; margin: 0 0 12px; }
        @media (prefers-color-scheme: dark) {
            body { color: #ECE0E0; background: #171213; }
            a { color: #FFB1C0; }
            td, th, header { border-color: #524345; }
            header p { color: #D6C2C4; }
        }
    """.trimIndent()

    /**
     * @param content the page's cleaned main content (from [ParsedPattern.content])
     * @param caption a line such as "Saved from example.com on 25 October 2026"
     */
    fun build(content: Element, title: String, sourceUrl: String, caption: String): Document {
        val prepared = content.clone().also { it.setBaseUri(content.baseUri()) }
        // Point lazy-loaded images at the real photo before the cleaner drops data-* attributes.
        prepared.select("img").forEach { img ->
            val url = PatternParser.imageUrl(img)
            if (url == null) img.remove() else img.attr("src", url)
        }
        val dirty = Document.createShell(sourceUrl).also { it.body().appendChild(prepared) }
        val cleaned = Cleaner(SAFELIST).clean(dirty)

        val page = Document.createShell(sourceUrl)
        page.head().apply {
            appendElement("meta").attr("charset", "utf-8")
            appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1")
            appendElement("title").text(title)
            appendElement("style").appendChild(DataNode(STYLE))
        }
        page.body().appendElement("header").apply {
            appendElement("h1").text(title)
            appendElement("p").appendElement("a").attr("href", sourceUrl).text(caption)
        }
        cleaned.body().childNodes().toList().forEach { page.body().appendChild(it) }
        // Links open in the browser; they shouldn't try to navigate inside the saved copy.
        page.select("a[href]").forEach { link ->
            val absolute = link.absUrl("href")
            if (absolute.isEmpty()) link.unwrap() else link.attr("href", absolute)
        }
        page.outputSettings().prettyPrint(false)
        return page
    }
}
