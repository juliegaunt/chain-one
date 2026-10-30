package io.github.juliegaunt.chainone.ui.pattern

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.pdf.PageSize
import io.github.juliegaunt.chainone.data.pdf.PdfOpenException
import io.github.juliegaunt.chainone.data.pdf.PdfPages
import io.github.juliegaunt.chainone.data.pdf.PdfProblem
import io.github.juliegaunt.chainone.ui.importer.messageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class Ready(val pages: PdfPages) : PdfLoad
    data class Failed(val problem: PdfProblem) : PdfLoad
}

/**
 * Shows a PDF's pages one under another: drag to scroll, pinch or double-tap to zoom. The row
 * marker is a see-through bar that marks the row being worked on; it's saved with [onMarkerChange]
 * whenever it's moved, and the page being read with [onPageChange].
 */
@Composable
fun PdfViewer(
    file: File,
    initialPage: Int,
    initialMarker: RowMarker?,
    onPageChange: (Int) -> Unit,
    onMarkerChange: (RowMarker?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val load by produceState<PdfLoad>(PdfLoad.Loading, file) {
        var pages: PdfPages? = null
        try {
            pages = PdfPages.open(file)
            value = if (pages.pageCount == 0) PdfLoad.Failed(PdfProblem.UNREADABLE) else PdfLoad.Ready(pages)
            awaitCancellation()
        } catch (e: PdfOpenException) {
            value = PdfLoad.Failed(e.problem)
        } finally {
            pages?.close()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        when (val current = load) {
            PdfLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is PdfLoad.Failed -> Text(
                stringResource(current.problem.messageRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
            is PdfLoad.Ready -> PdfPagesView(current.pages, initialPage, initialMarker, onPageChange, onMarkerChange)
        }
    }
}

/** A part of a page drawn at full sharpness for the current zoom, placed by fractions of the page. */
private class SharpPart(val page: Int, val area: Rect, val image: ImageBitmap)

private enum class MarkerGrip { BAR, RESIZE }

@Composable
private fun PdfPagesView(
    pages: PdfPages,
    initialPage: Int,
    initialMarker: RowMarker?,
    onPageChange: (Int) -> Unit,
    onMarkerChange: (RowMarker?) -> Unit,
) {
    val density = LocalDensity.current
    val viewport = remember(pages) {
        PdfViewport(pages.pageSizes, gapPx = with(density) { PageGap.toPx() }, initialPage = initialPage.coerceIn(0, pages.pageCount - 1))
    }
    var marker by remember(pages) { mutableStateOf(initialMarker?.takeIf { it.page < pages.pageCount }) }
    var showHint by remember { mutableStateOf(false) }
    val savePage by rememberUpdatedState(onPageChange)
    val saveMarker by rememberUpdatedState(onMarkerChange)
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Offset>()
    var motion by remember { mutableStateOf<Job?>(null) }

    // Whole pages, drawn screen-wide; kept for the pages on screen and their neighbours.
    val pageImages = remember(pages) { mutableStateMapOf<Int, ImageBitmap>() }
    var sharpParts by remember(pages) { mutableStateOf(emptyList<SharpPart>()) }
    val baseWidth = min(viewport.width, MAX_BASE_WIDTH_PX).roundToInt()

    LaunchedEffect(pages, baseWidth) {
        if (baseWidth <= 0) return@LaunchedEffect
        pageImages.clear()
        snapshotFlow { viewport.visiblePages() }.distinctUntilChanged().collectLatest { visible ->
            if (visible.isEmpty()) return@collectLatest
            val wanted = (visible.first - 1).coerceAtLeast(0)..(visible.last + 1).coerceAtMost(pages.pageCount - 1)
            pageImages.keys.filter { it !in wanted }.forEach { pageImages.remove(it) }
            // The pages on screen first, then the ones either side, ready for scrolling.
            for (page in visible + wanted) {
                if (page in pageImages) continue
                try {
                    pageImages[page] = pages.render(page, baseWidth).asImageBitmap()
                } catch (e: IOException) {
                    return@collectLatest
                }
            }
        }
    }

    // Once zooming or scrolling stops, redraws what's on screen at full sharpness.
    LaunchedEffect(pages, baseWidth) {
        snapshotFlow { listOf(viewport.scale, viewport.x, viewport.y, viewport.height) }.collectLatest {
            delay(SHARPEN_DELAY_MS)
            if (viewport.pageWidth <= baseWidth * SHARPEN_ABOVE) {
                sharpParts = emptyList()
                return@collectLatest
            }
            sharpParts = viewport.visiblePages().mapNotNull { page ->
                val area = viewport.visibleArea(page) ?: return@mapNotNull null
                val pageWidth = viewport.pageWidth
                val pageHeight = viewport.pageHeight(page)
                val left = (area.left * pageWidth).roundToInt()
                val top = (area.top * pageHeight).roundToInt()
                val width = (area.width * pageWidth).roundToInt()
                val height = (area.height * pageHeight).roundToInt()
                if (width <= 0 || height <= 0) return@mapNotNull null
                try {
                    SharpPart(page, area, pages.renderPart(page, pageWidth, left, top, width, height).asImageBitmap())
                } catch (e: IOException) {
                    null
                }
            }
        }
    }

    LaunchedEffect(viewport) {
        snapshotFlow { if (viewport.width > 0f) viewport.currentPage else null }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { savePage(it) }
    }
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(HINT_MS)
            showHint = false
        }
    }

    val gripSize = with(density) { MarkerGripTouch.toPx() }
    val barTouch = with(density) { MarkerMinTouch.toPx() }
    val gripInset = with(density) { MarkerGripInset.toPx() }
    fun markerGripAt(point: Offset): MarkerGrip? {
        val current = marker ?: return null
        val bar = viewport.markerRect(current)
        val grip = viewport.gripCenter(current, gripInset)
        return when {
            abs(point.x - grip.x) < gripSize / 2 && abs(point.y - grip.y) < gripSize / 2 -> MarkerGrip.RESIZE
            point.y in (bar.center.y - max(bar.height, barTouch) / 2)..(bar.center.y + max(bar.height, barTouch) / 2) &&
                point.x in bar.left..bar.right -> MarkerGrip.BAR
            else -> null
        }
    }

    fun moveMarker(grip: MarkerGrip, dy: Float) {
        val current = marker ?: return
        val change = dy / viewport.pageHeight(current.page)
        marker = when (grip) {
            MarkerGrip.BAR -> current.copy(top = (current.top + change).coerceIn(0f, 1f - current.height))
            MarkerGrip.RESIZE -> current.copy(height = (current.height + change).coerceIn(MIN_MARKER_HEIGHT, 1f - current.top))
        }
    }

    fun stepMarker(rows: Int) {
        val moved = marker?.let { stepped(it, rows, pages.pageCount) } ?: return
        marker = moved
        viewport.reveal(moved)
        saveMarker(moved)
    }

    val pageDescription = stringResource(R.string.pdf_page_description, viewport.currentPage + 1, pages.pageCount)
    val markerColor = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport.setSize(it.width.toFloat(), it.height.toFloat()) }
            .semantics { contentDescription = pageDescription }
            .pointerInput(viewport) {
                detectTapGestures(
                    onDoubleTap = { point ->
                        val target = if (viewport.scale > DOUBLE_TAP_SCALE * 0.9f) 1f else DOUBLE_TAP_SCALE
                        motion?.cancel()
                        motion = scope.launch {
                            animate(viewport.scale, target) { value, _ -> viewport.zoomBy(value / viewport.scale, point) }
                        }
                    },
                    // Long-pressing a row puts the marker there.
                    onLongPress = { point ->
                        if (markerGripAt(point) != null) return@detectTapGestures
                        val (page, fraction) = viewport.pageAt(point) ?: return@detectTapGestures
                        val height = marker?.height ?: DEFAULT_MARKER_HEIGHT
                        val placed = RowMarker(page, (fraction - height / 2).coerceIn(0f, 1f - height), height)
                        if (marker == null) showHint = true
                        marker = placed
                        saveMarker(placed)
                    },
                )
            }
            .pointerInput(viewport) {
                val maxVelocity = viewConfiguration.maximumFlingVelocity
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    motion?.cancel()
                    val grip = markerGripAt(down.position)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var pointers = 1
                    var moving = false
                    var totalPan = Offset.Zero
                    var totalZoom = 1f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed != pointers) {
                            // A finger added or lifted: the centre jumps, which isn't a real movement.
                            pointers = pressed
                            tracker.resetTracking()
                        }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (!moving) {
                            totalZoom *= zoomChange
                            totalPan += panChange
                            val zoomMotion = abs(1 - totalZoom) * event.calculateCentroidSize(useCurrent = false)
                            moving = zoomMotion > viewConfiguration.touchSlop || totalPan.getDistance() > viewConfiguration.touchSlop
                        }
                        if (moving) {
                            if (grip != null && pointers == 1) {
                                moveMarker(grip, panChange.y)
                            } else {
                                if (zoomChange != 1f) viewport.zoomBy(zoomChange, event.calculateCentroid(useCurrent = false))
                                viewport.panBy(panChange.x, panChange.y)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        tracker.addPosition(event.changes.first().uptimeMillis, event.calculateCentroid())
                    }
                    if (grip != null && moving) {
                        marker?.let(saveMarker)
                    } else if (moving && pointers == 1) {
                        val velocity = tracker.calculateVelocity()
                        val start = Offset(
                            velocity.x.coerceIn(-maxVelocity, maxVelocity),
                            velocity.y.coerceIn(-maxVelocity, maxVelocity),
                        )
                        motion = scope.launch {
                            var last = Offset.Zero
                            AnimationState(Offset.VectorConverter, Offset.Zero, start).animateDecay(decay) {
                                val delta = value - last
                                last = value
                                if (!viewport.panBy(delta.x, delta.y)) cancelAnimation()
                            }
                        }
                    }
                }
            }
            .drawBehind {
                val visible = viewport.visiblePages()
                visible.forEach { page ->
                    val rect = viewport.pageRect(page)
                    drawRect(Color.White, rect.topLeft, rect.size)
                    pageImages[page]?.let { drawImageInto(it, rect) }
                }
                sharpParts.forEach { part ->
                    if (part.page !in visible) return@forEach
                    val rect = viewport.pageRect(part.page)
                    drawImageInto(
                        part.image,
                        Rect(
                            rect.left + part.area.left * rect.width,
                            rect.top + part.area.top * rect.height,
                            rect.left + part.area.right * rect.width,
                            rect.top + part.area.bottom * rect.height,
                        ),
                    )
                }
                marker?.let { drawMarker(viewport, it, markerColor) }
            },
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconToggleButton(
                checked = marker != null,
                onCheckedChange = { on ->
                    val placed = if (on) viewport.newMarker() else null
                    marker = placed
                    showHint = on
                    saveMarker(placed)
                },
            ) {
                Icon(
                    painterResource(R.drawable.ic_row_marker),
                    contentDescription = stringResource(if (marker == null) R.string.pdf_marker_show else R.string.pdf_marker_hide),
                )
            }
            if (marker != null) {
                FilledTonalIconButton(onClick = { stepMarker(-1) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.pdf_marker_up))
                }
                FilledTonalIconButton(onClick = { stepMarker(1) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.pdf_marker_down))
                }
            }
        }
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp, end = 64.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    stringResource(R.string.pdf_marker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Text(
                stringResource(R.string.pdf_page_of, viewport.currentPage + 1, pages.pageCount),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** The marker moved by its own height, onto the next or previous page past an edge. */
internal fun stepped(marker: RowMarker, rows: Int, pageCount: Int): RowMarker {
    val top = marker.top + rows * marker.height
    return when {
        top < 0f && marker.page > 0 -> marker.copy(page = marker.page - 1, top = 1f - marker.height)
        top + marker.height > 1f && marker.page < pageCount - 1 -> marker.copy(page = marker.page + 1, top = 0f)
        else -> marker.copy(top = top.coerceIn(0f, 1f - marker.height))
    }
}

private fun DrawScope.drawImageInto(image: ImageBitmap, rect: Rect) {
    drawImage(
        image,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}

private fun DrawScope.drawMarker(viewport: PdfViewport, marker: RowMarker, color: Color) {
    val bar = viewport.markerRect(marker)
    val line = 2.dp.toPx()
    drawRect(color.copy(alpha = 0.22f), bar.topLeft, bar.size)
    drawRect(color, bar.topLeft, Size(bar.width, line))
    drawRect(color, Offset(bar.left, bar.bottom - line), Size(bar.width, line))
    val grip = viewport.gripCenter(marker, MarkerGripInset.toPx())
    val gripSize = Size(MarkerGripWidth.toPx(), MarkerGripHeight.toPx())
    drawRoundRect(
        color,
        topLeft = Offset(grip.x - gripSize.width / 2, grip.y - gripSize.height / 2),
        size = gripSize,
        cornerRadius = CornerRadius(gripSize.height / 2),
    )
}

/**
 * Where the viewer is looking, in screen pixels. At zoom 1 each page is as wide as the viewer;
 * pages sit one under another with a small gap.
 */
@Stable
private class PdfViewport(private val pageSizes: List<PageSize>, private val gapPx: Float, private val initialPage: Int) {
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

    /** The page at the top quarter of the screen, which is where people read. */
    val currentPage: Int
        get() = if (tops.isEmpty()) initialPage else pageAtContentY((y + height / 4) / scale)

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

private val PageGap = 8.dp
private val MarkerMinTouch = 36.dp
private val MarkerGripTouch = 48.dp
private val MarkerGripWidth = 32.dp
private val MarkerGripHeight = 10.dp
private val MarkerGripInset = 40.dp

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_BASE_WIDTH_PX = 1440f

/** Zoomed in this much past the page pictures' own size, the screen is redrawn sharp. */
private const val SHARPEN_ABOVE = 1.15f
private const val SHARPEN_DELAY_MS = 150L
private const val HINT_MS = 5_000L

/** About two lines of a typical pattern PDF. */
private const val DEFAULT_MARKER_HEIGHT = 0.035f
private const val DEFAULT_MARKER_TOP = 0.3f
private const val MIN_MARKER_HEIGHT = 0.008f
