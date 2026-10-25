package io.github.juliegaunt.chainone.data.importer

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * The structured data (schema.org JSON-LD) a page embeds for search engines. Pattern-card
 * plugins describe a pattern as a `HowTo` (or a `Recipe`) with its supplies, tools and steps.
 */
internal class JsonLd private constructor(private val nodes: List<JSONObject>) {

    /** The pattern itself: a HowTo or Recipe, which carries steps and materials. */
    val pattern: JSONObject? = nodes.firstOrNull { it.hasType("HowTo", "Recipe") }

    /** The page's article, which may name the author and image when there is no pattern card. */
    val article: JSONObject? = nodes.firstOrNull {
        it.hasType("Article", "BlogPosting", "NewsArticle", "CreativeWork", "Product", "WebPage")
    }

    fun title(): String = listOfNotNull(pattern, article)
        .firstNotNullOfOrNull { node -> (node.plainText("name") ?: node.plainText("headline"))?.ifBlank { null } }
        .orEmpty()

    fun author(): String = listOfNotNull(pattern, article)
        .firstNotNullOfOrNull { node ->
            // Ravelry lists the designer as the "brand" of a pattern for sale.
            listOf("author", "creator", "brand").firstNotNullOfOrNull { names(node.opt(it)).firstOrNull() }
        }
        .orEmpty()

    fun imageUrl(): String? = listOfNotNull(pattern, article)
        .firstNotNullOfOrNull { node -> imageUrls(node.opt("image")).firstOrNull() }

    /** Supplies (yarn, notions) as plain lines. */
    fun supplies(): List<String> =
        listOfNotNull(pattern).flatMap { itemNames(it.opt("supply")) + itemNames(it.opt("recipeIngredient")) }

    /** Tools (hooks, scissors, needles) as plain lines. */
    fun tools(): List<String> = listOfNotNull(pattern).flatMap { itemNames(it.opt("tool")) }

    /** The steps in Chain One markup: a `## ` line for each named section, then one line per step. */
    fun stepLines(): List<String> {
        val node = pattern ?: return emptyList()
        val lines = mutableListOf<String>()
        collectSteps(node.opt("step") ?: node.opt("recipeInstructions"), lines)
        return lines
    }

    private fun collectSteps(value: Any?, out: MutableList<String>) {
        when (value) {
            is String -> out += htmlToLines(value)
            is JSONArray -> value.items().forEach { collectSteps(it, out) }
            is JSONObject -> {
                val children = value.opt("itemListElement")
                if (value.hasType("HowToSection") || (children != null && value.optString("text").isBlank())) {
                    value.plainText("name")?.takeIf { it.isNotBlank() }?.let { out += "## $it" }
                    collectSteps(children, out)
                } else {
                    val text = value.optString("text").ifBlank { value.optString("name") }
                    out += htmlToLines(text)
                    collectSteps(children, out)
                }
            }
        }
    }

    private fun names(value: Any?): List<String> = when (value) {
        is String -> listOf(clean(value))
        is JSONArray -> value.items().flatMap(::names)
        is JSONObject -> resolve(value).plainText("name")?.let(::listOf).orEmpty()
        else -> emptyList()
    }.filter { it.isNotBlank() }

    private fun imageUrls(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is JSONArray -> value.items().flatMap(::imageUrls)
        is JSONObject -> resolve(value).let { listOfNotNull(it.optString("url").ifBlank { it.optString("contentUrl") }.ifBlank { null }) }
        else -> emptyList()
    }.filter { it.isNotBlank() }

    private fun itemNames(value: Any?): List<String> = when (value) {
        is String -> listOf(clean(value))
        is JSONArray -> value.items().flatMap(::itemNames)
        is JSONObject -> {
            val quantity = value.optJSONObject("requiredQuantity")?.let { q ->
                listOf(q.optString("value"), q.optString("unitText")).filter { it.isNotBlank() }.joinToString(" ")
            }.orEmpty()
            listOfNotNull(
                (value.plainText("name") ?: value.plainText("text"))
                    ?.let { if (quantity.isNotBlank()) "$it ($quantity)" else it },
            )
        }
        else -> emptyList()
    }.filter { it.isNotBlank() }

    /** Follows an `{"@id": …}` reference to the full node elsewhere in the page's graph. */
    private fun resolve(node: JSONObject): JSONObject {
        val id = node.optString("@id")
        if (id.isEmpty() || node.length() > 1) return node
        return nodes.firstOrNull { it !== node && it.optString("@id") == id } ?: node
    }

    companion object {
        fun read(document: Document): JsonLd = JsonLd(
            document.select("script[type=application/ld+json]").flatMap { script ->
                val parsed = try {
                    JSONTokener(script.data().trim()).nextValue()
                } catch (e: JSONException) {
                    null
                }
                flatten(parsed)
            },
        )

        private fun flatten(value: Any?): List<JSONObject> = when (value) {
            is JSONArray -> value.items().flatMap(::flatten)
            is JSONObject -> listOf(value) + flatten(value.opt("@graph"))
            else -> emptyList()
        }

        private fun JSONArray.items(): List<Any?> = (0 until length()).map { opt(it) }

        private fun JSONObject.hasType(vararg types: String): Boolean {
            val type = opt("@type")
            val names = when (type) {
                is String -> listOf(type)
                is JSONArray -> type.items().filterIsInstance<String>()
                else -> emptyList()
            }
            return names.any { it in types }
        }

        /** A text field with any HTML tags and entities removed, or null if it's missing. */
        private fun JSONObject.plainText(key: String): String? =
            (opt(key) as? String)?.let(::clean)

        private fun clean(text: String): String = Jsoup.parseBodyFragment(text).text().trim()

        /** Step text may contain HTML such as `<br>` between rows; keep those as separate lines. */
        private fun htmlToLines(text: String): List<String> =
            PageContent.toMarkup(Jsoup.parseBodyFragment(text).body()).lines().filter { it.isNotBlank() }
    }
}
