package io.github.juliegaunt.chainone.data.importer

import org.jsoup.nodes.Element
import java.net.URI

/**
 * Finds a page's own promotions inside the article: "buy the PDF" and "pin it" graphics, and short
 * "click here to download" lines that link to a shop. They're marked [HIDDEN] rather than removed,
 * so the saved copy keeps them and "Show hidden pictures" can bring them back if a rule was wrong.
 */
object Promotions {

    /** Marks a hidden part of a saved page; its style rule hides it. */
    const val HIDDEN = "data-chain-one-hidden"

    /** Hides the promotions in [root], in place. */
    fun hideIn(root: Element) {
        root.select("img").filter(::isPromotion).forEach { hide(it, root) }
        root.select("a[href]").filter(::isCallToAction).forEach { link ->
            // The whole line or button, if that's all it is; else just the link.
            val block = link.parents().firstOrNull { it.normalName() in BLOCKS }
            hide(if (block != null && block !== root && block.text().length <= MAX_CALL_TO_ACTION) block else link, root)
        }
    }

    /** True for a picture that advertises rather than shows the pattern. */
    fun isPromotion(img: Element): Boolean {
        val description = img.attr("alt") + " " + img.attr("title")
        if (PROMO_DESCRIPTION.containsMatchIn(description)) return true
        if (img.closest("a[href]")?.let { isShop(it.attr("href")) } == true) return true
        val fileNames = listOf("data-lazy-src", "data-src", "data-original", "src").map { img.attr(it).substringAfterLast('/') }
        return fileNames.any { name -> name.lowercase().split(NOT_A_WORD).any { it in PROMO_FILE_WORDS } }
    }

    /**
     * Hides [element], or the wrapper it fills on its own (a picture's link and figure), so no
     * empty frame or caption is left behind. Never climbs as far as [root], the whole article.
     */
    fun hide(element: Element, root: Element) {
        var target = element
        while (true) {
            val parent = target.parent() ?: break
            if (parent === root) break
            if (parent.normalName() !in WRAPPERS || parent.select("img").size > 1 || parent.ownText().isNotBlank()) break
            if (parent.children().any { it !== target && it.normalName() != "figcaption" && it.text().isNotBlank() }) break
            target = parent
        }
        target.attr(HIDDEN, "")
    }

    /** A short "Click here to buy the PDF" link to a shop. */
    private fun isCallToAction(link: Element): Boolean =
        isShop(link.attr("href")) && CALL_TO_ACTION.containsMatchIn(link.text())

    private fun isShop(href: String): Boolean {
        val host = runCatching { URI(href.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return false
        return SHOP_HOSTS.any { host == it || host.endsWith(".$it") } || host.startsWith("pinterest.")
    }

    private const val MAX_CALL_TO_ACTION = 160
    private val NOT_A_WORD = Regex("""[^a-z0-9]+""")
    private val BLOCKS = setOf("p", "li", "div", "figure", "h2", "h3", "h4")
    private val WRAPPERS = setOf("a", "figure", "picture", "p", "div", "span", "center")

    private val SHOP_HOSTS = listOf(
        "pinterest.com", "pin.it", "etsy.com", "etsy.me", "ravelry.com", "lovecrafts.com", "ko-fi.com", "patreon.com",
        "gumroad.com", "payhip.com", "makerist.com", "amazon.com", "amzn.to", "ribblr.com", "kofi.com", "buymeacoffee.com",
    )

    /** How sites describe their own advertising graphics: "Link to purchase the PDF on Etsy", "Pin it for later". */
    private val PROMO_DESCRIPTION = Regex(
        """\b(link to (purchase|buy|pin|download|shop|get)|click (here )?(to|for)|pin (it|this|me|for later|to)|""" +
            """save (this|it) (to|for)|purchase|buy (the|now|it|my|this)|shop (now|the)|download (the|my|your|now|it)|""" +
            """ad[- ]?free|printable pdf|(on|from|at) (etsy|ravelry|lovecrafts|pinterest|ko-?fi|patreon))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Words in advertising graphics' file names, such as "Ravelry-Etsy-Promo.jpg" or "pin-it.png". */
    private val PROMO_FILE_WORDS = setOf(
        "promo", "pin", "pinit", "pinterest", "etsy", "ravelry", "banner", "button", "download", "buy", "purchase", "adfree",
    )

    private val CALL_TO_ACTION = Regex(
        """\b(click|buy|purchase|download|get (the|your|my)|shop|ad[- ]?free|pdf|printable)\b""",
        RegexOption.IGNORE_CASE,
    )
}
