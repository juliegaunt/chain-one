package io.github.juliegaunt.chainone.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfNamesTest {

    @Test
    fun titleTurnsFileNameSeparatorsIntoSpaces() {
        assertEquals("Granny Square v2", PdfNames.title("Granny_Square_v2.pdf"))
        assertEquals("Granny square v2", PdfNames.title("granny-square-v2.pdf"))
        assertEquals("Baby blanket", PdfNames.title("baby-blanket.PDF"))
        assertEquals("Market Bag", PdfNames.title("Market+Bag.pdf"))
    }

    @Test
    fun titleKeepsDashesInNamesThatHaveSpaces() {
        assertEquals("Top-down Hat", PdfNames.title("Top-down Hat.pdf"))
        assertEquals("Top-down Hat Pattern", PdfNames.title("Top-down_Hat_Pattern.pdf"))
    }

    @Test
    fun titleDropsTheNumberBrowsersAddToRepeatDownloads() {
        assertEquals("Shawl", PdfNames.title("shawl (1).pdf"))
        assertEquals("Shawl (Large)", PdfNames.title("Shawl (Large).pdf"))
    }

    @Test
    fun displayNameIsTheFileNameWithoutExtension() {
        assertEquals("Granny_Square-v2", PdfNames.displayName("Granny_Square-v2.pdf"))
        assertEquals("notes.txt", PdfNames.displayName("notes.txt"))
        assertEquals(".pdf", PdfNames.displayName(".pdf"))
    }

    @Test
    fun fileNameFromLinkIsTheDecodedLastPart() {
        assertEquals("Hat Pattern.pdf", PdfNames.fromUrl("https://example.com/files/Hat%20Pattern.pdf?dl=1"))
        assertEquals(PdfNames.DEFAULT_FILE_NAME, PdfNames.fromUrl("https://example.com/"))
        assertEquals(PdfNames.DEFAULT_FILE_NAME, PdfNames.fromUrl("not a link"))
    }
}
