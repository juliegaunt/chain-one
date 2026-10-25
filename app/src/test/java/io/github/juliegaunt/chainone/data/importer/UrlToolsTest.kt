package io.github.juliegaunt.chainone.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlToolsTest {

    @Test
    fun extractsLinkFromSharedText() {
        assertEquals(
            "https://example.com/tote-pattern/",
            UrlTools.extractUrl("Sunny Tote – free pattern https://example.com/tote-pattern/."),
        )
        assertNull(UrlTools.extractUrl("no link here"))
    }

    @Test
    fun cleanDropsTrackingAndFragmentButKeepsRealParameters() {
        assertEquals(
            "https://www.example.com/p?id=42",
            UrlTools.clean("https://WWW.Example.com/p?utm_source=pin&id=42&fbclid=abc#comments"),
        )
    }

    @Test
    fun cleanAddsMissingSchemeAndRejectsNonsense() {
        assertEquals("https://example.com/bag", UrlTools.clean("example.com/bag"))
        assertNull(UrlTools.clean("granny square"))
        assertNull(UrlTools.clean("ftp://example.com/file"))
        assertNull(UrlTools.clean("https://localhost/x"))
        assertNull(UrlTools.clean(""))
    }

    @Test
    fun fetchesOverHttps() {
        assertEquals("https://example.com/a", UrlTools.forFetching("http://example.com/a"))
        assertEquals("https://example.com/a", UrlTools.forFetching("https://example.com/a"))
    }

    @Test
    fun variantsCoverSchemeWwwAndTrailingSlash() {
        val variants = UrlTools.sameUrlVariants("https://example.com/bag/")
        listOf(
            "https://example.com/bag/",
            "https://example.com/bag",
            "http://example.com/bag/",
            "https://www.example.com/bag",
            "http://www.example.com/bag/",
        ).forEach { assertTrue("missing $it", it in variants) }
        assertEquals(8, variants.size)
    }

    @Test
    fun hostDropsWww() {
        assertEquals("example.com", UrlTools.host("https://www.example.com/x"))
    }
}
