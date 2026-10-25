package io.github.juliegaunt.chainone.data.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric provides Android's real org.json, which the JSON-LD reader uses.
@RunWith(RobolectricTestRunner::class)
class PatternParserTest {

    private fun page(name: String, url: String = "https://example.com/patterns/$name/"): Document {
        val html = checkNotNull(javaClass.classLoader?.getResource("pages/$name.html")).readText()
        return Jsoup.parse(html, url)
    }

    @Test
    fun howToCardGivesEveryField() {
        val parsed = PatternParser.parse(page("howto-card"))

        assertEquals("Sunny Granny Square Tote", parsed.title)
        assertEquals("Jo Sample", parsed.designer)
        assertEquals("5 mm (H-8)", parsed.hookSize)
        assertEquals("Worsted (#4)", parsed.yarnWeight)
        assertEquals(
            listOf(
                "- Worsted weight cotton yarn (#4), 3 skeins",
                "- Stitch markers",
                "- 5 mm (H-8) crochet hook",
                "- Tapestry needle",
            ).joinToString("\n"),
            parsed.materials,
        )
        assertEquals(
            listOf(
                "## Granny squares (make 10)",
                "Rnd 1: Ch 4, 11 dc in 4th ch from hook. Join with sl st. (12 dc)",
                "Rnd 2: Ch 3, dc in same st,",
                "2 dc in each st around. (24 dc)",
                "## Assembly",
                "Whip stitch squares together & weave in ends.",
            ).joinToString("\n"),
            parsed.instructions,
        )
        assertTrue(parsed.foundInstructions)
        assertEquals("https://example.com/images/tote-main.jpg", parsed.coverImageUrl)
    }

    @Test
    fun cleanedContentKeepsArticleButDropsClutter() {
        val content = PatternParser.parse(page("howto-card")).content!!
        val text = content.text()

        assertTrue(text.contains("perfect for summer markets"))
        assertFalse("share buttons", text.contains("Share on Pinterest"))
        assertFalse("ads", text.contains("Advertisement"))
        assertFalse("comments", text.contains("Love it!"))
        assertTrue(content.select("script").isEmpty())
        // The article's own "tag-social" class must not get the whole article thrown away.
        assertTrue(text.contains("Granny squares"))
    }

    @Test
    fun recipeCardIsReadAsAPattern() {
        val parsed = PatternParser.parse(page("recipe-card"))

        assertEquals("Chunky Ribbed Beanie", parsed.title)
        assertEquals("Pat Hooker", parsed.designer)
        assertEquals("10 mm", parsed.hookSize)
        assertEquals("Super bulky (#6)", parsed.yarnWeight)
        assertEquals("- Super bulky yarn (#6), 120 yards\n- 10 mm hook", parsed.materials)
        assertTrue(parsed.instructions.startsWith("Row 1: Ch 21"))
        assertEquals(3, parsed.instructions.lines().size)
        assertEquals("https://example.org/beanie.jpg", parsed.coverImageUrl)
    }

    @Test
    fun plainBlogIsSplitAtItsHeadings() {
        val parsed = PatternParser.parse(page("plain-blog"))

        assertEquals("Easy Market Bag", parsed.title)
        assertEquals("Little Loops Blog", parsed.designer)
        assertEquals("4.0 mm", parsed.hookSize)
        assertEquals("DK", parsed.yarnWeight)
        assertEquals(
            "- DK weight cotton, about 200 yards\n- 4.0mm crochet hook\n- Scissors",
            parsed.materials,
        )
        assertEquals(
            listOf(
                "## Pattern",
                "Round 1: Make a magic ring, 8 sc into the ring.",
                "Round 2: 2 sc in each st around. (16 sc)",
                "Round 3: *Ch 5, skip 1 st, sc in next* around.",
                "- Fasten off.",
                "- Weave in ends.",
            ).joinToString("\n"),
            parsed.instructions,
        )
        assertTrue(parsed.foundInstructions)
        // No og:image: the first real photo, at its largest size up to 1600 px, skipping the tiny icon.
        assertEquals("https://example.com/wp-content/uploads/bag-large.jpg", parsed.coverImageUrl)
    }

    @Test
    fun instructionsHeadingWinsOverTitleRepeatedAsHeading() {
        val parsed = PatternParser.parse(page("title-heading-first"))

        assertTrue(parsed.foundInstructions)
        val lines = parsed.instructions.lines()
        // Not the copyright note under the repeated title, and not cut short at "Pin it for later".
        assertEquals("## Instructions", lines.first())
        assertTrue(lines.contains("1. Ch8. Hdc in 2nd ch from hook and each ch across. Turn. – 7 st"))
        assertEquals("2. Ch3. Dc in next st and each st around. Ss to top of ch3. – 30 st", lines.last())
        assertEquals("- Worsted weight yarn, about 90 yds\n- 5mm Hook", parsed.materials)
    }

    @Test
    fun labeledFieldsAreRead() {
        val parsed = PatternParser.parse(page("labeled-fields"))

        assertEquals("Sam Designer", parsed.designer)
        // Short labelled values are kept as the site wrote them; "Weight: 100 g" isn't a yarn weight.
        assertEquals("5.0 mm (H)", parsed.hookSize)
        assertEquals("Worsted (9 wpi)", parsed.yarnWeight)
        assertEquals("https://images.example.net/mittens.jpg", parsed.coverImageUrl)
    }

    @Test
    fun pageWithoutAPatternHasNoInstructions() {
        val parsed = PatternParser.parse(page("news-article"))

        assertEquals("Town Council Approves New Library Hours", parsed.title)
        assertEquals("", parsed.instructions)
        assertFalse(parsed.foundInstructions)
        assertEquals("", parsed.hookSize)
        // The page itself is still kept for the saved copy.
        assertTrue(parsed.content!!.text().contains("knitting circle"))
    }

    @Test
    fun markupConversionHandlesHeadingsListsBreaksAndTables() {
        val html = """
            <div><h3>Notes</h3><p>Line one<br>Line two</p>
            <ul><li>Outer<ul><li>Inner</li></ul></li></ul>
            <table><tr><th>Size</th><th>Chains</th></tr><tr><td>S</td><td>40</td></tr></table></div>
        """.trimIndent()

        assertEquals(
            listOf("## Notes", "Line one", "Line two", "- Outer", "- Inner", "Size | Chains", "S | 40").joinToString("\n"),
            PageContent.toMarkup(Jsoup.parseBodyFragment(html).body()),
        )
    }
}
