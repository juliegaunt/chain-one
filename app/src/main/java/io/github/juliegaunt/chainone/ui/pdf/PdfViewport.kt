package io.github.juliegaunt.chainone.ui.pdf

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.pdf.PageSize
import kotlin.math.max
import kotlin.math.min

/** The marker moved by its own height, onto the next or previous page past an edge. */
internal fun stepped(marker: RowMarker, rows: Int, pageCount: Int): RowMarker {
    val top = marker.top + rows * marker.height
    return when {
        top < 0f && marker.page > 0 -> marker.copy(page = marker.page - 1, top = 1f - marker.height)
        top + marker.height > 1f && marker.page < pageCount - 1 -> marker.copy(page = marker.page + 1, top = 0f)
        else -> marker.copy(top = top.coerceIn(0f, 1f - marker.height))
    }
}

/**
 * Where the viewer is looking, in screen pixels. At zoom 1 each page is as wide as the viewer;
 * pages sit one under another with a small gap.
 */
@Stable
internal class PdfViewport(private val pageSizes: List<PageSize>, private val gapPx: Float, private val initialPage: Int) {
    var width by mutableFloatStateOf(0f)
        private set
    var height by mutableFloatStateOf(0f)
        private set
    var scale by mutableFloatStateOf(1f)
        private set
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    // Page tops and heights at zoom 1; they change only with the viewer's width.
    private var tops = FloatArray(0)
    private var heights = FloatArray(0)
    private var contentHeight = 0f

    val pageWidth: Float get() = width * scale

    fun pageHeight(page: Int): Float = heights[page] * scale

    /**
     * The page at the top quarter of the screen, which is where people read. It checks [width]
     * (state) rather than the page tops (not state), so a derivedStateOf of it updates once the
     * viewer has been measured.
     */
    val currentPage: Int
        get() = if (width <= 0f) initialPage else pageAtContentY((y + height / 4) / scale)

    fun setSize(newWidth: Float, newHeight: Float) {
        if (newWidth == width && newHeight == height) return
        val widthChanged = newWidth != width
        // After turning the phone, stay on the same page.
        val anchor = currentPage
        width = newWidth
        height = newHeight
        if (widthChanged) {
            heights = FloatArray(pageSizes.size) { newWidth * pageSizes[it].height / pageSizes[it].width }
            tops = FloatArray(pageSizes.size)
            var top = 0f
            for (i in pageSizes.indices) {
                tops[i] = top
                top += heights[i] + gapPx
            }
            contentHeight = top - gapPx
            y = tops[anchor] * scale
        }
        clamp()
    }

    fun visiblePages(): IntRange {
        if (tops.isEmpty() || height <= 0f) return IntRange.EMPTY
        val first = pageAtContentY(y / scale)
        var last = first
        while (last + 1 < tops.size && tops[last + 1] * scale < y + height) last++
        return first..last
    }

    fun pageRect(page: Int): Rect {
        val top = tops[page] * scale - y
        return Rect(-x, top, -x + pageWidth, top + pageHeight(page))
    }

    /** The part of [page] on screen, as fractions of the page, or null if none is. */
    fun visibleArea(page: Int): Rect? {
        val rect = pageRect(page)
        val visible = rect.intersect(Rect(0f, 0f, width, height))
        if (visible.isEmpty) return null
        return Rect(
            (visible.left - rect.left) / rect.width,
            (visible.top - rect.top) / rect.height,
            (visible.right - rect.left) / rect.width,
            (visible.bottom - rect.top) / rect.height,
        )
    }

    /** The page under [point] and how far down it the point is (0 at the top, 1 at the bottom). */
    fun pageAt(point: Offset): Pair<Int, Float>? {
        if (tops.isEmpty()) return null
        val contentY = (y + point.y) / scale
        val page = pageAtContentY(contentY)
        val fraction = (contentY - tops[page]) / heights[page]
        return if (fraction in 0f..1f) page to fraction else null
    }

    fun markerRect(marker: RowMarker): Rect {
        val page = pageRect(marker.page)
        val top = page.top + marker.top * page.height
        return Rect(page.left, top, page.right, top + marker.height * page.height)
    }

    /** The resize tab sits on the marker's bottom edge near the right, where pages usually have a margin. */
    fun gripCenter(marker: RowMarker, insetPx: Float): Offset {
        val bar = markerRect(marker)
        return Offset(min(bar.right, width) - insetPx, bar.bottom)
    }

    /** A marker on the page being read, a third of the way down the screen. */
    fun newMarker(): RowMarker {
        val (page, fraction) = pageAt(Offset(width / 2, height / 3)) ?: (currentPage to DEFAULT_MARKER_TOP)
        return RowMarker(page, fraction.coerceIn(0f, 1f - DEFAULT_MARKER_HEIGHT), DEFAULT_MARKER_HEIGHT)
    }

    /** Scrolls so [marker] is on screen, if it isn't. */
    fun reveal(marker: RowMarker) {
        val bar = markerRect(marker)
        if (bar.top < 0f || bar.bottom > height) {
            y += bar.top - height / 3
            clamp()
        }
    }

    /** Moves the view with a finger's movement. Returns false if it was already at the edge. */
    fun panBy(dx: Float, dy: Float): Boolean {
        val oldX = x
        val oldY = y
        x -= dx
        y -= dy
        clamp()
        return x != oldX || y != oldY
    }

    /** Zooms by [factor], keeping the point under [focus] where it is. */
    fun zoomBy(factor: Float, focus: Offset) {
        val newScale = (scale * factor).coerceIn(1f, MAX_SCALE)
        val change = newScale / scale
        x = (x + focus.x) * change - focus.x
        y = (y + focus.y) * change - focus.y
        scale = newScale
        clamp()
    }

    private fun clamp() {
        x = x.coerceIn(0f, max(0f, pageWidth - width))
        y = y.coerceIn(0f, max(0f, contentHeight * scale - height))
    }

    /** The last page starting at or above [contentY] (at zoom 1). */
    private fun pageAtContentY(contentY: Float): Int {
        var low = 0
        var high = tops.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (tops[mid] <= contentY) low = mid else high = mid - 1
        }
        return low
    }
}

private const val MAX_SCALE = 5f

/** About two lines of a typical pattern PDF. */
internal const val DEFAULT_MARKER_HEIGHT = 0.035f
private const val DEFAULT_MARKER_TOP = 0.3f
