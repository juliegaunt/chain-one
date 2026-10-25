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
    IS_PDF,
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
            connect(url).maxBodySize(MAX_PAGE_BYTES).execute()
        } catch (e: IOException) {
            throw ImportException(problemFor(e), e)
        } catch (e: IllegalArgumentException) {
            throw ImportException(ImportProblem.INVALID_LINK, e)
        }

        when (response.statusCode()) {
            in 200..299 -> Unit
            401, 403, 429, 451 -> throw ImportException(ImportProblem.BLOCKED)
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
            val response = connect(url).maxBodySize(maxBytes + 1).execute()
            val bytes = response.bodyAsBytes()
            if (response.statusCode() !in 200..299 || bytes.size > maxBytes) return@withContext null
            Download(bytes, response.contentType().orEmpty().lowercase())
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun connect(url: String): Connection = Jsoup.connect(url)
        // Many pattern sites turn away requests that don't look like a phone browser.
        .userAgent(USER_AGENT)
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
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
        const val TIMEOUT_MS = 20_000
        const val MAX_PAGE_BYTES = 8 * 1024 * 1024
    }
}
