package io.github.juliegaunt.chainone.data.importer

import io.github.juliegaunt.chainone.data.pdf.PdfOpenException
import io.github.juliegaunt.chainone.data.pdf.PdfProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Element
import java.io.File

/**
 * Downloads a pattern page, reads it with [PatternParser] and stages the result (fields, cover
 * photo and a saved copy of the page) for the review screen.
 *
 * @param caption the line under the saved copy's title, given the site's name,
 *   e.g. "Saved from example.com on 25 October 2026"
 */
class PatternImporter(
    private val web: WebClient,
    private val staging: ImportStaging,
    private val pdfs: PdfImporter,
    private val caption: (host: String) -> String,
) {

    /**
     * [url] should already be cleaned with [UrlTools.clean]. Throws [ImportException] on failure.
     * The staged source link is the page's final address, after any redirects (short links
     * such as pin.it lead to the real page). A link to a PDF stages the PDF instead.
     */
    suspend fun import(url: String): StagedImport {
        staging.discardStale()
        val document = try {
            web.fetchPage(UrlTools.forFetching(url))
        } catch (e: ImportException) {
            if (e.problem == ImportProblem.IS_PDF) return importPdf(url)
            throw e
        }
        val pageUrl = UrlTools.clean(document.location()) ?: url
        val parsed = withContext(Dispatchers.Default) { PatternParser.parse(document) }

        val folder = staging.newFolder()
        try {
            val cover = parsed.coverImageUrl?.let { saveImage(it, folder, COVER_NAME) }
            val snapshot = parsed.content?.let { saveSnapshot(it, parsed.title, pageUrl, folder) }
            val staged = StagedImport(
                id = folder.name,
                sourceUrl = pageUrl,
                title = parsed.title,
                designer = parsed.designer,
                hookSize = parsed.hookSize,
                yarnWeight = parsed.yarnWeight,
                materials = parsed.materials,
                instructions = parsed.instructions,
                foundInstructions = parsed.foundInstructions,
                coverFile = cover,
                snapshotDir = snapshot,
            )
            staging.save(staged)
            return staged
        } catch (e: Throwable) {
            // Includes cancellation (the person backed out): leave nothing behind.
            withContext(Dispatchers.IO + NonCancellable) { folder.deleteRecursively() }
            throw e
        }
    }

    private suspend fun importPdf(url: String): StagedImport {
        val download = web.download(UrlTools.forFetching(url), MAX_PDF_BYTES)
            ?: throw ImportException(ImportProblem.IS_PDF)
        return try {
            pdfs.stage(PdfNames.fromUrl(url), sourceUrl = url) { download.bytes.inputStream() }
        } catch (e: PdfOpenException) {
            throw ImportException(
                if (e.problem == PdfProblem.PROTECTED) ImportProblem.PDF_PROTECTED else ImportProblem.PDF_UNREADABLE,
                e,
            )
        }
    }

    /** Stages just the link, for when the page can't be downloaded but the person wants to keep it. */
    suspend fun linkOnly(url: String): StagedImport {
        val folder = staging.newFolder()
        val staged = StagedImport(id = folder.name, sourceUrl = url, downloaded = false)
        staging.save(staged)
        return staged
    }

    suspend fun discard(staged: StagedImport) = staging.discard(staged.id)

    private suspend fun saveSnapshot(content: Element, title: String, url: String, folder: File): File {
        val dir = File(folder, SNAPSHOT_DIR)
        val page = withContext(Dispatchers.Default) { SnapshotBuilder.build(content, title, url, caption(UrlTools.host(url))) }

        val images = page.select("img")
        images.drop(MAX_IMAGES).forEach { it.remove() }
        val urls = images.take(MAX_IMAGES).map { it.attr("src") }.distinct()

        withContext(Dispatchers.IO) { dir.mkdirs() }
        val limit = Semaphore(PARALLEL_DOWNLOADS)
        val saved: Map<String, File?> = coroutineScope {
            urls.mapIndexed { index, imageUrl ->
                async { imageUrl to limit.withPermit { saveImage(imageUrl, dir, "image-${index + 1}") } }
            }.awaitAll().toMap()
        }
        // Photos that couldn't be downloaded are left out rather than shown as broken images.
        images.take(MAX_IMAGES).forEach { img ->
            val file = saved[img.attr("src")]
            if (file == null) img.remove() else img.attr("src", file.name)
        }

        withContext(Dispatchers.IO) { File(dir, SNAPSHOT_PAGE).writeText(page.outerHtml()) }
        return dir
    }

    private suspend fun saveImage(url: String, folder: File, baseName: String): File? {
        val download = web.download(url, MAX_IMAGE_BYTES) ?: return null
        val extension = imageExtension(download.contentType, url) ?: return null
        return withContext(Dispatchers.IO) {
            File(folder, "$baseName.$extension").apply { writeBytes(download.bytes) }
        }
    }

    /** Only common photo formats; anything else (including SVG, which can carry scripts) is skipped. */
    private fun imageExtension(contentType: String, url: String): String? {
        val fromType = when (contentType.substringBefore(';').trim()) {
            "image/jpeg", "image/jpg", "image/pjpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
        if (fromType != null) return fromType
        if (contentType.startsWith("image/") || contentType.isEmpty() || contentType.startsWith("application/octet-stream")) {
            return when (url.substringBefore('?').substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "jpg"
                "png" -> "png"
                "webp" -> "webp"
                "gif" -> "gif"
                else -> null
            }
        }
        return null
    }

    companion object {
        const val SNAPSHOT_DIR = "snapshot"
        const val SNAPSHOT_PAGE = "index.html"
        private const val COVER_NAME = "cover"
        private const val MAX_IMAGES = 40
        private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        private const val PARALLEL_DOWNLOADS = 4
        private const val MAX_PDF_BYTES = 50 * 1024 * 1024
    }
}
