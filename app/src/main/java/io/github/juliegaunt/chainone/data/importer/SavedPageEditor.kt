package io.github.juliegaunt.chainone.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException

/**
 * Hides and shows parts of a pattern's saved web page (see [SnapshotBuilder]). Nothing is deleted:
 * hidden parts are marked [Promotions.HIDDEN], and a style rule in the page hides them.
 */
object SavedPageEditor {

    private const val STYLE_ID = "chain-one-hidden"

    /** Marks a page whose promotions have been looked for, so [tidyOnce] leaves it alone. */
    private const val TIDIED = "chain-one-tidied"

    /** The marker's content: 1 hid pictures and buy links; 2 also hides the lines introducing them. */
    private const val TIDY_VERSION = 2

    private val HIDDEN = "[${Promotions.HIDDEN}]"

    /** Adds the style rule that hides marked parts, and notes the page as tidied. */
    fun prepare(page: Document) {
        if (page.getElementById(STYLE_ID) == null) {
            page.head().appendElement("style").attr("id", STYLE_ID).appendChild(DataNode("$HIDDEN { display: none !important; }"))
        }
        val marker = page.selectFirst("meta[name=$TIDIED]") ?: page.head().appendElement("meta").attr("name", TIDIED)
        marker.attr("content", TIDY_VERSION.toString())
    }

    /**
     * For pages saved before promotions were hidden on import: hides them, once. A page tidied by
     * an older version only has the newer steps applied around what's already hidden, so a page
     * whose hidden parts were shown again stays that way. Returns true if the page changed.
     */
    suspend fun tidyOnce(page: File): Boolean = edit(page) { doc ->
        val tidied = doc.selectFirst("meta[name=$TIDIED]")?.attr("content")?.toIntOrNull()
        when {
            tidied == null -> Promotions.hideIn(doc.body())
            tidied < TIDY_VERSION -> Promotions.hideLeadIns(doc.body())
            else -> return@edit false
        }
        prepare(doc)
        true
    }

    /**
     * Hides the picture saved as [fileName] (such as "image-3.jpg"), and any line just introducing
     * it. Returns true if one was found.
     */
    suspend fun hidePicture(page: File, fileName: String): Boolean = edit(page) { doc ->
        val img = doc.select("img").firstOrNull { it.attr("src").substringAfterLast('/') == fileName && it.closest(HIDDEN) == null }
            ?: return@edit false
        Promotions.hide(img, doc.body())
        Promotions.hideLeadIns(doc.body())
        prepare(doc)
        true
    }

    /** Shows every hidden part again. */
    suspend fun showAll(page: File): Boolean = edit(page) { doc ->
        val hidden = doc.select(HIDDEN)
        hidden.removeAttr(Promotions.HIDDEN)
        hidden.isNotEmpty()
    }

    /** How many parts of the page are hidden (0 if there's no page). */
    suspend fun hiddenCount(page: File): Int = withContext(Dispatchers.IO) {
        if (page.isFile) Jsoup.parse(page, Charsets.UTF_8.name()).select(HIDDEN).size else 0
    }

    /** Applies [change] to the page and saves it, if it changed, by writing a copy and swapping it in. */
    private suspend fun edit(page: File, change: (Document) -> Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!page.isFile) return@withContext false
        val doc = Jsoup.parse(page, Charsets.UTF_8.name())
        if (!change(doc)) return@withContext false
        doc.outputSettings().prettyPrint(false)
        val copy = File(page.parentFile, "${page.name}.new")
        copy.writeText(doc.outerHtml())
        if (!copy.renameTo(page)) {
            copy.delete()
            throw IOException("Could not save $page")
        }
        true
    }
}
