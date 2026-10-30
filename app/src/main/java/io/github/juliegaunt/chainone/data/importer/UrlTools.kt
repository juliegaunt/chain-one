package io.github.juliegaunt.chainone.data.importer

import java.net.URI
import java.net.URISyntaxException

/** Link helpers for importing. Pure Kotlin so they can be unit-tested without a phone. */
object UrlTools {

    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Query parameters that only track where a click came from. */
    private val TRACKING_PARAMS = Regex("""^(utm_\w+|fbclid|gclid|dclid|msclkid|igshid|mc_cid|mc_eid|_hsenc|_hsmi)$""")

    /**
     * Finds the first web link in [text]. Browsers often share "Page title https://…", so the
     * link may not be the whole text. Returns null if there is no link.
     */
    fun extractUrl(text: String): String? =
        URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')

    /**
     * Turns what someone typed or shared into a clean http(s) link: adds a missing `https://`,
     * drops the `#fragment` and tracking parameters. Returns null if it isn't a usable web link.
     */
    fun clean(input: String): String? {
        val text = extractUrl(input) ?: input.trim().takeIf { it.isNotEmpty() && ' ' !in it }?.let { "https://$it" }
            ?: return null
        val uri = try {
            URI(text)
        } catch (e: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || host.isNullOrEmpty() || '.' !in host) return null

        val query = uri.rawQuery
            ?.split('&')
            ?.filter { it.isNotEmpty() && !TRACKING_PARAMS.matches(it.substringBefore('=')) }
            ?.joinToString("&")
            ?.ifEmpty { null }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.rawPath.orEmpty()
        return "$scheme://$host$port$path" + (query?.let { "?$it" } ?: "")
    }

    /** Http links are fetched over https: Android blocks unencrypted downloads by default. */
    fun forFetching(cleanUrl: String): String =
        if (cleanUrl.startsWith("http://")) "https://" + cleanUrl.removePrefix("http://") else cleanUrl

    /**
     * Spellings of the same page that a person may have saved before: with or without
     * `https`, `www.` and a trailing slash. Used to spot a pattern that's already in the library.
     */
    fun sameUrlVariants(cleanUrl: String): List<String> {
        val withoutScheme = cleanUrl.substringAfter("://")
        val hosts = if (withoutScheme.startsWith("www.")) {
            listOf(withoutScheme, withoutScheme.removePrefix("www."))
        } else {
            listOf(withoutScheme, "www.$withoutScheme")
        }
        val slashes = hosts.flatMap { rest ->
            val (beforeQuery, query) = rest.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
            val toggled = if (beforeQuery.endsWith('/')) beforeQuery.trimEnd('/') else "$beforeQuery/"
            listOf(beforeQuery, toggled).map { if (query != null) "$it?$query" else it }
        }
        return slashes.flatMap { listOf("https://$it", "http://$it") }.distinct()
    }

    /** True for an http or https address: the only kind the app opens, in the browser. */
    fun isWebLink(url: String): Boolean =
        runCatching { URI(url.trim()).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)

    fun host(url: String): String =
        runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
}
