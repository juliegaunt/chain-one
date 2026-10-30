package io.github.juliegaunt.chainone.data.importer

import java.net.URI
import java.net.URISyntaxException

/** Turns a PDF's file name into something a person would call it. */
object PdfNames {

    /** The name shown for the PDF on the pattern page: the file name without ".pdf". */
    fun displayName(fileName: String): String =
        fileName.trim().replace(PDF_EXTENSION, "").trim().ifEmpty { fileName.trim() }

    /**
     * A title for the pattern, e.g. "Granny_Square-v2.pdf" → "Granny Square v2",
     * "baby-blanket (1).pdf" → "Baby blanket". Dashes only count as spaces when the name has
     * none, so "Top-down Hat.pdf" keeps its dash.
     */
    fun title(fileName: String): String {
        var name = displayName(fileName)
            // "(1)" is how browsers name a second download of the same file.
            .replace(DOWNLOAD_COPY, "")
            .replace('_', ' ')
        if (' ' !in name.trim()) name = name.replace('-', ' ').replace('+', ' ')
        name = name.replace(SPACES, " ").trim()
        if (name.none { it.isUpperCase() }) name = name.replaceFirstChar { it.titlecase() }
        return name
    }

    /** The file name at the end of a link, e.g. "https://example.com/files/Hat%20Pattern.pdf?dl=1" → "Hat Pattern.pdf". */
    fun fromUrl(url: String): String {
        val path = try {
            URI(url).path
        } catch (e: URISyntaxException) {
            null
        }
        return path?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: DEFAULT_FILE_NAME
    }

    const val DEFAULT_FILE_NAME = "Pattern.pdf"

    private val PDF_EXTENSION = Regex("""\.pdf$""", RegexOption.IGNORE_CASE)
    private val DOWNLOAD_COPY = Regex("""\s*\(\d+\)\s*$""")
    private val SPACES = Regex("""\s+""")
}
