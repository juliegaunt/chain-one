package io.github.juliegaunt.chainone.data.importer

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.MalformedURLException
import java.net.URL

/** What could be read from a pattern's web page. Blank strings mean "not found". */
data class ParsedPattern(
    val title: String,
    val designer: String = "",
    val hookSize: String = "",
    val yarnWeight: String = "",
    val materials: String = "",
    val instructions: String = "",
    /** False when nothing on the page looked like pattern instructions; [instructions] is then blank. */
    val foundInstructions: Boolean = false,
    val coverImageUrl: String? = null,
    /** A cleaned copy of the page's main content, for the saved copy of the page. */
    val content: Element? = null,
)

/**
 * Reads a crochet pattern out of a web page. It tries the most reliable sources first:
 * 1. the structured data pattern-card plugins embed (JSON-LD `HowTo`/`Recipe`),
 * 2. the pattern card's own HTML,
 * 3. labelled fields such as "Hook size: 5 mm" (Ravelry and many blogs),
 * 4. the page's main article, split at headings like "Materials" and "Pattern".
 */
object PatternParser {

    private const val CARD_INSTRUCTIONS =
        ".mv-create-instructions, .wprm-recipe-instructions-container, .tasty-recipes-instructions"
    private const val CARD_MATERIALS = ".mv-create-materials, .mv-create-supplies, .mv-create-tools, " +
        ".wprm-recipe-ingredients-container, .wprm-recipe-equipment-container, " +
        ".tasty-recipes-ingredients, .tasty-recipes-equipment"

    private val HOOK_LABEL = Regex("""^(crochet\s+)?hooks?(\s+sizes?)?$""", RegexOption.IGNORE_CASE)
    private val YARN_LABEL = Regex("""^(yarn\s+)?weight$""", RegexOption.IGNORE_CASE)
    private val DESIGNER_LABEL = Regex("""^(designer|designed\s+by|pattern\s+by)$""", RegexOption.IGNORE_CASE)

    private val MATERIALS_HEADING = Regex(
        """\b(materials|supplies|you\s*(?:'|’)?ll\s+need|you\s+will\s+need|what\s+you\s+need|tools)\b""",
        RegexOption.IGNORE_CASE,
    )
    /** Headings that clearly start the instructions. */
    private val INSTRUCTIONS_HEADING = Regex(
        """\b(instructions|directions|how\s+to\s+(make|crochet)|let'?s\s+(begin|start|get\s+started))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** "Pattern" headings are tried only after those: blogs often repeat the title ("… Crochet Pattern") as a heading. */
    private val PATTERN_HEADING = Regex("""\bpattern\b""", RegexOption.IGNORE_CASE)

    /** Headings that mark the end of the post rather than part of the pattern. */
    private val END_HEADING = Regex(
        """\b(comments?|leave\s+a\s+reply|related|similar|you\s+(may|might|will)\s+(also\s+)?(like|love)|""" +
            """more\s+(free\s+)?(crochet\s+)?patterns|other\s+(patterns|designs))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Headings that never start the instructions, such as "Pin it for later" or "Share". */
    private val NOT_A_START = Regex("""\b(pin|share|save|more|related|similar|other|also|comments?)\b""", RegexOption.IGNORE_CASE)

    private val ROW_MARKER = Regex("""\b(row|rnd|round|r)\s*\d+""", RegexOption.IGNORE_CASE)

    // Abbreviations may have a count attached, as in "Ch6" or "ch3".
    private val STITCH = Regex(
        """\b(sc|dc|hdc|tr|dtr|ch|sl\s?st|ss|sts?|inc|dec|sc2tog|dc2tog|hdc2tog|yo|fpdc|bpdc|blo|flo|mr|sk)\d*\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE_SEPARATORS = listOf(" | ", " - ", " – ", " — ", " · ", " :: ")
    private val PREFIX_SEPARATORS = listOf(": ", " | ", " - ", " – ", " — ", " · ")
    private val SRCSET_SEPARATOR = Regex(""",\s+""")
    private val SPACES = Regex("""\s+""")
    private const val MAX_SECTION_HEADING = 60
    private const val MAX_KEPT_VALUE = 40

    fun parse(document: Document): ParsedPattern {
        val jsonLd = JsonLd.read(document)
        val content = PageContent.findMainContent(document)
        val contentLines = content?.let { PageContent.toMarkup(it).lines() }.orEmpty()

        // Instructions: pattern-card steps, then the card's HTML, then the article itself.
        val steps = jsonLd.stepLines()
        val cardInstructions = cardMarkup(document, CARD_INSTRUCTIONS)
        val articleInstructions = articleInstructions(contentLines)
        val (instructions, found) = when {
            steps.size >= 2 -> steps.joinToString("\n") to true
            cardInstructions.isNotBlank() -> cardInstructions to true
            looksLikePattern(articleInstructions) -> articleInstructions to true
            else -> "" to false
        }

        val supplies = jsonLd.supplies()
        val tools = jsonLd.tools()
        val materials = when {
            supplies.isNotEmpty() || tools.isNotEmpty() -> (supplies + tools).joinToString("\n") { "- $it" }
            else -> cardMarkup(document, CARD_MATERIALS).ifBlank { section(contentLines, MATERIALS_HEADING).asBullets() }
        }

        val allText = contentLines.joinToString("\n")
        val hookSize = labeledValue(document, HOOK_LABEL)?.let { keepShort(it, CraftDetails::hookSizeIn) }
            ?: CraftDetails.findHookSize((tools + materials + allText).joinToString("\n"))
        val yarnWeight = labeledValue(document, YARN_LABEL)?.let { keepShort(it, CraftDetails::yarnWeightIn) }
            ?: CraftDetails.findYarnWeight((supplies + materials + allText).joinToString("\n"))

        val siteName = meta(document, "og:site_name")
        val title = cleanTitle(
            jsonLd.title().ifBlank { null }
                ?: meta(document, "og:title")
                ?: document.selectFirst("h1")?.text()?.ifBlank { null }
                ?: document.title(),
            siteName,
        )
        val designer = jsonLd.author().ifBlank { null }
            ?: labeledValue(document, DESIGNER_LABEL)
            ?: meta(document, "author")
            ?: siteName
            ?: ""

        val coverImageUrl = jsonLd.imageUrl()?.let { resolve(document.location(), it) }
            ?: meta(document, "og:image")?.let { resolve(document.location(), it) }
            ?: meta(document, "twitter:image")?.let { resolve(document.location(), it) }
            ?: content?.select("img")?.firstOrNull { !isTiny(it) }?.let(::imageUrl)

        return ParsedPattern(
            title = title,
            designer = designer,
            hookSize = hookSize,
            yarnWeight = yarnWeight,
            materials = materials,
            instructions = instructions,
            foundInstructions = found,
            coverImageUrl = coverImageUrl,
            content = content,
        )
    }

    /** True if [text] reads like crochet instructions: numbered rows/rounds or lots of stitch abbreviations. */
    fun looksLikePattern(text: String): Boolean =
        ROW_MARKER.findAll(text).count() >= 2 || STITCH.findAll(text).count() >= 8

    /**
     * The article from its instructions heading onwards, stopping at "More patterns", "Comments"
     * and the like. "Instructions"-style headings are tried before "Pattern" ones, and the first
     * that leads to something that reads like crochet wins. Falls back to the whole article.
     */
    private fun articleInstructions(lines: List<String>): String {
        fun startsAt(heading: Regex) = lines.indices.filter { i ->
            val line = lines[i]
            isSectionStart(line, heading) && !isSectionStart(line, MATERIALS_HEADING) && !NOT_A_START.containsMatchIn(line)
        }
        val starts = (startsAt(INSTRUCTIONS_HEADING) + startsAt(PATTERN_HEADING)).distinct()
        return starts.asSequence().map { sliceFrom(lines, it) }.firstOrNull(::looksLikePattern)
            ?: sliceFrom(lines, 0)
    }

    private fun sliceFrom(lines: List<String>, start: Int): String {
        val fromStart = lines.drop(start)
        val end = fromStart.drop(1).indexOfFirst { it.startsWith("## ") && END_HEADING.containsMatchIn(it) }
        return (if (end >= 0) fromStart.take(end + 1) else fromStart).joinToString("\n").trim()
    }

    /** The lines under the first heading (or "Label:" line) matching [heading], up to the next one. */
    private fun section(lines: List<String>, heading: Regex): List<String> {
        val start = lines.indexOfFirst { isSectionStart(it, heading) }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).takeWhile { !isHeadingLike(it) }
    }

    /** Long headings are sentences ("You can see the full free pattern below…"), not section names. */
    private fun isSectionStart(line: String, heading: Regex): Boolean =
        isHeadingLike(line) && line.length <= MAX_SECTION_HEADING &&
            heading.containsMatchIn(line.removePrefix("## ").trimEnd(':'))

    /** A `## ` heading, or a short bold-style label such as "Materials:" on its own line. */
    private fun isHeadingLike(line: String): Boolean =
        line.startsWith("## ") || (line.endsWith(":") && line.length <= 40 && !line.startsWith("- "))

    private fun List<String>.asBullets(): String =
        joinToString("\n") { if (it.startsWith("- ") || it.startsWith("## ")) it else "- $it" }

    private fun cardMarkup(document: Document, selector: String): String =
        document.select(selector).joinToString("\n") { PageContent.toMarkup(PageContent.clean(it.clone())) }.trim()

    /**
     * The value next to a label such as "Hook size", in any of the usual layouts:
     * `<dt>Hook size</dt><dd>…</dd>`, `<th>…</th><td>…</td>`, `<div>label</div><div>value</div>`
     * or `<p><strong>Hook size:</strong> 5 mm</p>`.
     */
    private fun labeledValue(document: Document, label: Regex): String? {
        for (element in document.body().select("*")) {
            val own = element.ownText().trim().trimEnd(':').trim()
            if (own.isEmpty() || own.length > 30 || !label.matches(own)) continue
            val value = (
                element.nextElementSibling()?.text()?.trim()?.ifEmpty { null }
                    ?: element.parent()?.ownText()?.trim()?.trimStart(':')?.trim()
                )
                // Ravelry follows some values with a "?" help link.
                ?.trimEnd('?', '.', ' ')
            if (!value.isNullOrEmpty() && value.length <= 120) return value
        }
        return null
    }

    /**
     * A labelled field's value as written ("4.0 mm (G)") when it's short, or just the size [extract]
     * finds in a long one. Null if it holds no size at all (a "Weight: 100 g" field, say).
     */
    private fun keepShort(value: String, extract: (String) -> String): String? {
        val extracted = extract(value).ifEmpty { return null }
        return if (value.length <= MAX_KEPT_VALUE) value else extracted
    }

    private fun meta(document: Document, name: String): String? =
        document.selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.trim()?.ifEmpty { null }

    /** Drops the site name from titles like "Granny Square Bag | Yarn Blog" or "Yarn Blog: Granny Square Bag". */
    private fun cleanTitle(raw: String, siteName: String?): String {
        var title = raw.trim()
        if (siteName != null) {
            for (separator in TITLE_SEPARATORS) {
                val suffix = separator + siteName
                if (title.endsWith(suffix, ignoreCase = true) && title.length > suffix.length) {
                    title = title.dropLast(suffix.length).trim()
                }
            }
            for (separator in PREFIX_SEPARATORS) {
                val prefix = siteName + separator
                if (title.startsWith(prefix, ignoreCase = true) && title.length > prefix.length) {
                    title = title.drop(prefix.length).trim()
                }
            }
        }
        if (" | " in title) title = title.substringBeforeLast(" | ").trim()
        return title
    }

    private fun isTiny(img: Element): Boolean {
        val width = img.attr("width").toIntOrNull()
        val height = img.attr("height").toIntOrNull()
        return (width != null && width < 150) || (height != null && height < 150)
    }

    /**
     * The best address for an image, looking past lazy-loading placeholders
     * (`data-src`, `srcset`, …) to the real photo. Returns an absolute URL or null.
     */
    fun imageUrl(img: Element): String? {
        val base = img.baseUri()
        val candidates = sequenceOf(
            img.attr("data-lazy-src"),
            img.attr("data-src"),
            img.attr("data-original"),
            bestFromSrcset(img.attr("data-lazy-srcset")),
            bestFromSrcset(img.attr("data-srcset")),
            bestFromSrcset(img.attr("srcset")),
            img.attr("src"),
        )
        return candidates
            .filter { it.isNotBlank() && !it.startsWith("data:") }
            .mapNotNull { resolve(base, it.trim()) }
            .firstOrNull()
    }

    /** Picks the largest image up to 1600 px wide from a `srcset` list. */
    private fun bestFromSrcset(srcset: String): String {
        if (srcset.isBlank()) return ""
        val entries = srcset.split(SRCSET_SEPARATOR).mapNotNull { entry ->
            val parts = entry.trim().split(SPACES)
            val url = parts.firstOrNull()?.ifBlank { null } ?: return@mapNotNull null
            val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
            url to width
        }
        return (entries.filter { it.second in 1..1600 }.maxByOrNull { it.second } ?: entries.lastOrNull())?.first.orEmpty()
    }

    internal fun resolve(base: String, url: String): String? = try {
        val resolved = if (base.isBlank()) URL(url) else URL(URL(base), url)
        resolved.toString().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    } catch (e: MalformedURLException) {
        null
    }
}
