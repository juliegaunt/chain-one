package io.github.juliegaunt.chainone.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Why an import couldn't download the page. Each has its own message on the import screen. */
enum class ImportProblem {
    INVALID_LINK,
    NO_CONNECTION,
    TIMEOUT,
    BLOCKED,
    NOT_FOUND,
    SERVER_ERROR,
    /** The link is a PDF that couldn't be downloaded (links to PDFs are otherwise imported as PDFs). */
    IS_PDF,
    PDF_PROTECTED,
    PDF_UNREADABLE,
    NOT_A_WEB_PAGE,
}

class ImportException(val problem: ImportProblem, cause: Throwable? = null) : IOException(problem.name, cause)

class Download(val bytes: ByteArray, val contentType: String)

/** The only part of the app that uses the network. An interface so tests can supply pages without one. */
interface WebClient {
    /** Downloads and parses a web page, or throws [ImportException]. */
    suspend fun fetchPage(url: String): Document

    /** Downloads a file such as a photo, or returns null if it fails or is larger than [maxBytes]. */
    suspend fun download(url: String, maxBytes: Int): Download?
}

class JsoupWebClient : WebClient {

    override suspend fun fetchPage(url: String): Document = withContext(Dispatchers.IO) {
        val response = try {
            execute(url, MAX_PAGE_BYTES)
        } catch (e: IOException) {
            throw ImportException(problemFor(e), e)
        } catch (e: IllegalArgumentException) {
            throw ImportException(ImportProblem.INVALID_LINK, e)
        }

        when (response.statusCode()) {
            in 200..299 -> Unit
            in REFUSED -> throw ImportException(ImportProblem.BLOCKED)
            404, 410 -> throw ImportException(ImportProblem.NOT_FOUND)
            else -> throw ImportException(ImportProblem.SERVER_ERROR)
        }

        val type = response.contentType().orEmpty().lowercase()
        when {
            "pdf" in type -> throw ImportException(ImportProblem.IS_PDF)
            type.isNotEmpty() && "html" !in type && "xml" !in type -> throw ImportException(ImportProblem.NOT_A_WEB_PAGE)
        }
        try {
            response.parse()
        } catch (e: IOException) {
            throw ImportException(problemFor(e), e)
        }
    }

    override suspend fun download(url: String, maxBytes: Int): Download? = withContext(Dispatchers.IO) {
        try {
            // One byte over the limit tells a too-large file apart from one exactly at it.
            val response = execute(url, maxBytes + 1)
            val bytes = response.bodyAsBytes()
            if (response.statusCode() !in 200..299 || bytes.size > maxBytes) return@withContext null
            Download(bytes, response.contentType().orEmpty().lowercase())
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Asks as Chain One first, then, if the site refuses, as a phone browser. Sites behind
     * Cloudflare often block a phone that claims to be Chrome but doesn't connect like Chrome;
     * other sites turn away anything that doesn't look like a browser.
     */
    private fun execute(url: String, maxBytes: Int): Connection.Response {
        val response = connect(url, APP_USER_AGENT).maxBodySize(maxBytes).execute()
        if (response.statusCode() !in REFUSED) return response
        return connect(url, BROWSER_USER_AGENT).maxBodySize(maxBytes).execute()
    }

    private fun connect(url: String, userAgent: String): Connection = Jsoup.connect(url)
        .userAgent(userAgent)
        .header("Accept-Language", "en")
        .timeout(TIMEOUT_MS)
        .followRedirects(true)
        .ignoreHttpErrors(true)
        .ignoreContentType(true)

    private fun problemFor(e: IOException): ImportProblem = when (e) {
        is MalformedURLException -> ImportProblem.INVALID_LINK
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> ImportProblem.NO_CONNECTION
        is SocketTimeoutException -> ImportProblem.TIMEOUT
        else -> ImportProblem.NO_CONNECTION
    }

    private companion object {
        const val APP_USER_AGENT = "ChainOne (Android; pattern import)"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"

        /** Answers that mean "not for you" (rather than "not found"); 503 is also Cloudflare's challenge page. */
        val REFUSED = setOf(401, 403, 429, 451, 503)
        const val TIMEOUT_MS = 20_000
        const val MAX_PAGE_BYTES = 8 * 1024 * 1024
    }
}
