package io.github.juliegaunt.chainone.data.importer

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Finds a web page's main content, strips the clutter around it and turns it into Chain One's text markup. */
internal object PageContent {

    /** Where blogs and pattern sites usually keep the article, most specific first. */
    private val CONTENT_SELECTORS = listOf(
        "[itemprop=articleBody]",
        ".entry-content",
        ".post-content",
        ".post-body",
        ".article-content",
        ".article-body",
        "article",
        "main",
        "[role=main]",
        "#content",
        ".content",
    )

    private const val MIN_CONTENT_CHARS = 200

    private val JUNK_TAGS = "script, style, noscript, template, iframe, form, button, input, select, textarea, " +
        "nav, aside, footer, svg, canvas, video, audio, object, embed, dialog, " +
        "[hidden], [aria-hidden=true], [role=navigation], [role=complementary], [role=dialog]"

    /** Class or id words that mark sharing buttons, ads, comments and other non-pattern parts of a page. */
    private val JUNK_WORDS = listOf(
        "share", "sharing", "social", "comment", "related", "newsletter", "subscribe", "signup", "popup",
        "modal", "sidebar", "widget", "breadcrumb", "cookie", "consent", "jump-to", "skip-link", "pinit",
        "print-button", "author-box", "post-navigation", "yarpp", "sponsor", "advert", "adsbygoogle", "promo",
    )
    private val AD_TOKEN = Regex("""^(ad|ads)([-_].*)?$|.*[-_](ad|ads)$""")
    private val HIDDEN_STYLE = Regex("""display\s*:\s*none|visibility\s*:\s*hidden""", RegexOption.IGNORE_CASE)

    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "main", "header", "blockquote", "ul", "ol", "dl", "dt", "dd",
        "table", "thead", "tbody", "tfoot", "figure", "figcaption", "pre", "address", "center", "hr",
    )
    private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /** Runs of spaces, tabs and non-breaking spaces, which become one space. */
    private val SPACES = Regex("""[ \t ]+""")

    /**
     * The page's main content as a cleaned copy (the page itself is left untouched), or null.
     * The usual article containers are tried most specific first, but one made mostly of links
     * (a category menu, say) loses to any candidate with clearly more real text.
     */
    fun findMainContent(document: Document): Element? {
        val candidates = (CONTENT_SELECTORS.flatMap { document.select(it) } + listOfNotNull(densestBlock(document)))
            .distinct()
            .filter { it.text().length >= MIN_CONTENT_CHARS }
        val scores = candidates.associateWith(::readableText)
        val best = scores.values.maxOrNull() ?: 0
        val chosen = candidates.firstOrNull { scores.getValue(it) >= best * 6 / 10 && best > 0 } ?: document.body()
        // A copy doesn't always keep the page's address, which relative image links need.
        val copy = chosen.clone().also { it.setBaseUri(document.location()) }
        return clean(copy).takeIf { it.text().isNotBlank() }
    }

    /** Removes scripts, menus, sharing buttons, ads, comments and hidden parts from [root] (in place). */
    fun clean(root: Element): Element {
        root.select(JUNK_TAGS).forEach { if (it !== root) it.remove() }
        // WordPress puts category and tag names (e.g. "tag-social") in the article's own classes,
        // so never drop a part that holds most of the text.
        val majority = root.text().length / 2
        root.select("[class], [id], [style]").forEach { element ->
            if (element !== root && isJunk(element) && element.text().length <= majority) element.remove()
        }
        return root
    }

    /** Characters of text that aren't link text: high for articles, near zero for menus. */
    private fun readableText(element: Element): Int =
        element.text().length - element.select("a").sumOf { it.text().length }

    private fun isJunk(element: Element): Boolean {
        if (HIDDEN_STYLE.containsMatchIn(element.attr("style"))) return true
        val tokens = (element.classNames() + element.id()).map { it.lowercase() }.filter { it.isNotEmpty() }
        return tokens.any { token -> AD_TOKEN.matches(token) || JUNK_WORDS.any { token.contains(it) } }
    }

    /** For pages without the usual markup: the block holding the most paragraph and list text (links don't count). */
    private fun densestBlock(document: Document): Element? =
        document.select("div, section, td")
            .maxByOrNull { block -> block.select("> p, > ul > li, > ol > li").sumOf(::readableText) }
            ?.takeIf { it.text().length >= MIN_CONTENT_CHARS }

    /**
     * Converts HTML to Chain One's instruction markup: one line per paragraph or line break,
     * `## ` before headings and `- ` before list items.
     */
    fun toMarkup(element: Element): String {
        val writer = MarkupWriter()
        writer.visitChildren(element)
        writer.flush()
        return writer.lines.joinToString("\n")
    }

    private class MarkupWriter {
        val lines = mutableListOf<String>()
        private val current = StringBuilder()

        fun flush() {
            current.toString().lineSequence()
                .map { it.replace(SPACES, " ").trim() }
                .filter { it.isNotEmpty() }
                .forEach { lines += it }
            current.clear()
        }

        fun visitChildren(element: Element) {
            element.childNodes().forEach(::visit)
        }

        private fun visit(node: Node) {
            when (node) {
                is TextNode -> current.append(node.text())
                is Element -> visitElement(node)
            }
        }

        private fun visitElement(element: Element) {
            val tag = element.normalName()
            when {
                tag in HEADING_TAGS -> {
                    flush()
                    element.text().trim().takeIf { it.isNotEmpty() }?.let { lines += "## $it" }
                }
                tag == "li" -> {
                    flush()
                    val nested = element.select("> ul, > ol")
                    val ownText = element.clone().apply { select("> ul, > ol").remove() }.text().trim()
                    if (ownText.isNotEmpty()) lines += "- $ownText"
                    nested.forEach(::visitChildren)
                }
                tag == "tr" -> {
                    flush()
                    val cells = element.select("> th, > td").map { it.text().trim() }.filter { it.isNotEmpty() }
                    if (cells.isNotEmpty()) lines += cells.joinToString(" | ")
                }
                tag == "br" -> current.append('\n')
                tag == "img" || tag == "picture" -> Unit
                tag in BLOCK_TAGS -> {
                    flush()
                    visitChildren(element)
                    flush()
                }
                else -> visitChildren(element)
            }
        }
    }
}
