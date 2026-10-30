package io.github.juliegaunt.chainone.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.sqrt

/** Why a PDF can't be shown. */
enum class PdfProblem {
    /** It needs a password, which Android's PDF reader can't take. */
    PROTECTED,

    /** Damaged, or not really a PDF. */
    UNREADABLE,

    /** Over [io.github.juliegaunt.chainone.data.files.FileStore.MAX_IMPORT_BYTES]. */
    TOO_LARGE,
}

class PdfOpenException(val problem: PdfProblem, cause: Throwable? = null) : IOException(problem.name, cause)

/** A page's size in PDF points (1/72 inch). */
data class PageSize(val width: Int, val height: Int)

/**
 * An open PDF that draws its pages as pictures, using Android's built-in [PdfRenderer].
 * The renderer can only draw one page at a time, so all its work happens on one thread.
 * [close] it when done.
 */
class PdfPages private constructor(
    private val file: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val executor: ExecutorService,
) : Closeable {

    private val thread = executor.asCoroutineDispatcher()

    @Volatile
    private var closed = false

    val pageSizes: List<PageSize> = List(renderer.pageCount) { index ->
        renderer.openPage(index).use { PageSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }
    }

    val pageCount: Int get() = pageSizes.size

    /**
     * The whole page, [widthPx] wide, or narrower for an unusually tall page, so one picture
     * never takes too much memory.
     */
    suspend fun render(page: Int, widthPx: Int): Bitmap = withContext(thread) {
        if (closed) throw IOException("Closed")
        val size = pageSizes[page]
        val height = widthPx.toDouble() * size.height / size.width
        val shrink = min(1.0, sqrt(MAX_PIXELS / (widthPx * height)))
        val bitmap = whiteBitmap((widthPx * shrink).toInt(), (height * shrink).toInt())
        renderer.openPage(page).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        bitmap
    }

    /**
     * Part of a page, for sharp text when zoomed in: the page is drawn [pageWidthPx] wide and the
     * bitmap holds just the area from ([left], [top]) that is [width] × [height] pixels.
     */
    suspend fun renderPart(page: Int, pageWidthPx: Float, left: Int, top: Int, width: Int, height: Int): Bitmap =
        withContext(thread) {
            if (closed) throw IOException("Closed")
            val bitmap = whiteBitmap(width, height)
            renderer.openPage(page).use {
                val scale = pageWidthPx / it.width
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(-left.toFloat(), -top.toFloat())
                }
                it.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            bitmap
        }

    /** Waits for any drawing in progress, then frees the file. Safe to call from the main thread. */
    override fun close() {
        if (closed) return
        closed = true
        executor.execute {
            renderer.close()
            file.close()
        }
        executor.shutdown()
    }

    companion object {
        private const val MAX_PIXELS = 6_000_000.0

        /** Throws [PdfOpenException] if the file can't be shown. */
        suspend fun open(pdf: File): PdfPages {
            val executor = Executors.newSingleThreadExecutor()
            return try {
                withContext(executor.asCoroutineDispatcher()) {
                    val descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = try {
                        PdfRenderer(descriptor)
                    } catch (e: Exception) {
                        descriptor.close()
                        throw e
                    }
                    PdfPages(descriptor, renderer, executor)
                }
            } catch (e: Exception) {
                executor.shutdown()
                throw when (e) {
                    is SecurityException -> PdfOpenException(PdfProblem.PROTECTED, e)
                    is IOException, is IllegalArgumentException -> PdfOpenException(PdfProblem.UNREADABLE, e)
                    else -> e
                }
            }
        }

        private fun whiteBitmap(width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
                // PDF pages are often transparent; paper is white.
                eraseColor(Color.WHITE)
            }
    }
}

/**
 * Checks a PDF can be shown and saves a picture of its first page for the cover.
 * An interface so tests (which can't draw PDFs) can supply their own.
 */
fun interface PdfInspector {
    /** Returns why the PDF can't be shown, or null if it's fine (and [cover] has been written, if possible). */
    suspend fun inspect(pdf: File, cover: File): PdfProblem?
}

class RendererPdfInspector : PdfInspector {
    override suspend fun inspect(pdf: File, cover: File): PdfProblem? {
        val pages = try {
            PdfPages.open(pdf)
        } catch (e: PdfOpenException) {
            return e.problem
        }
        try {
            if (pages.pageCount == 0) return PdfProblem.UNREADABLE
            val page = pages.render(0, COVER_WIDTH_PX)
            // The top of the first page, where patterns put their photo and title.
            val top = Bitmap.createBitmap(page, 0, 0, page.width, min(page.height, page.width))
            withContext(Dispatchers.IO) {
                cover.outputStream().use { top.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
            }
            if (top !== page) top.recycle()
            page.recycle()
        } catch (e: IOException) {
            // No cover is fine; the PDF itself opened.
            cover.delete()
        } finally {
            pages.close()
        }
        return null
    }

    private companion object {
        const val COVER_WIDTH_PX = 900
        const val COVER_QUALITY = 85
    }
}
