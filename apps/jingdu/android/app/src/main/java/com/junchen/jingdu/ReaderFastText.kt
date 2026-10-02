package com.junchen.jingdu

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
internal fun rememberReaderTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    val enabled = remember(manager) { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { active ->
            enabled.value = active
        }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled.value
}

/**
 * Paged normal reading reuses the exact StaticLayout that established the page boundary whenever
 * the visible body has no span that changes its appearance. Styled pages deliberately rebuild so
 * headings, highlights and TTS emphasis remain authoritative. Either way, layout work stays off the
 * frame thread and the Canvas only draws a ready layout.
 */
@Composable
internal fun Text(
    text: AnnotatedString, modifier: Modifier, style: TextStyle, overflow: TextOverflow,
    selectionMode: Boolean? = null, onRequestSelection: (() -> Unit)? = null,
) {
    var internalSelectionMode by remember(text.text) { mutableStateOf(false) }
    val selecting = selectionMode ?: internalSelectionMode
    if (selecting || rememberReaderTouchExplorationEnabled()) {
        androidx.compose.material3.Text(text = text, modifier = modifier, style = style, overflow = overflow)
        return
    }
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val nativeTypeface by resolver.resolveAsTypeface(
        fontFamily = style.fontFamily,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
        fontStyle = style.fontStyle ?: FontStyle.Normal,
        fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
    )
    val resolvedColor = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else style.color
    BoxWithConstraints(modifier.fillMaxWidth().armSelectionOnLongPress(text.text) {
        if (onRequestSelection != null) onRequestSelection() else internalSelectionMode = true
    }) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val heightPx = constraints.maxHeight.coerceAtLeast(1)
        val reusable = remember(text, widthPx, heightPx) {
            val headingOnly = text.spanStyles.isNotEmpty() && text.spanStyles.all { range ->
                val span = range.item
                span.background == Color.Unspecified &&
                    span.color == Color.Unspecified &&
                    (span.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold
            }
            val measurementCompatible = text.spanStyles.isEmpty() || headingOnly
            if (measurementCompatible) {
                ReaderPageLayoutCache.reusableLayoutFor(text.text, widthPx, heightPx, headingOnly)
            } else null
        }
        val layout by produceState<StaticLayout?>(reusable, text, style, widthPx, density.density, density.fontScale, nativeTypeface, resolvedColor, reusable) {
            if (reusable == null) {
                value = withContext(Dispatchers.Default) { buildFastStaticLayout(text, style, density, nativeTypeface, resolvedColor, widthPx) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            layout?.let { ready ->
                // The layout is already measured off-main. Canvas replay is deliberately retained
                // for paged mode so page changes create normal RenderThread-observable frames.
                ready.paint.color = resolvedColor.toArgb()
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipRect(0f, 0f, size.width, size.height)
                ready.draw(canvas)
                canvas.restore()
            }
        }
    }
}

/** Native bounded continuous layout: one worker-built StaticLayout owns geometry and drawing. */
internal data class ReaderContinuousOverlay(
    val startUtf16: Int,
    val endUtf16: Int,
    val color: Int,
)

internal class ReaderContinuousLayout internal constructor(
    internal val layout: StaticLayout,
    internal val raster: ReaderStaticLayoutBitmapTileSet,
) {
    val lineCount: Int get() = layout.lineCount
    val height: Int get() = layout.height
    fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset.coerceIn(0, layout.text.length))
    fun getLineTop(line: Int): Float = layout.getLineTop(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))).toFloat()
    fun getLineForVerticalPosition(y: Float): Int = layout.getLineForVertical(y.roundToInt().coerceAtLeast(0))
    fun getLineStart(line: Int): Int = layout.getLineStart(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0)))
    fun getOffsetForHorizontal(line: Int, x: Float): Int =
        layout.getOffsetForHorizontal(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0)), x)
            .coerceIn(0, layout.text.length)
}

/**
 * Non-snapshot scroll model for the continuous hot path. Gesture deltas do not invalidate Compose;
 * the attached native viewport consumes offsets at most once per vsync.
 */
internal fun readerContinuousFlingNeedsHandoff(
    offsetPx: Float,
    maxOffsetPx: Float,
    viewportHeightPx: Int,
    velocityY: Int,
    canHandoffPrevious: Boolean,
    canHandoffNext: Boolean,
): Boolean {
    if (viewportHeightPx <= 0 || velocityY == 0) return false
    val edge = viewportHeightPx * 0.35f
    return when {
        velocityY > 0 && canHandoffNext ->
            maxOffsetPx.coerceAtLeast(0f) - offsetPx.coerceAtLeast(0f) <= edge
        velocityY < 0 && canHandoffPrevious ->
            offsetPx.coerceAtLeast(0f) <= edge
        else -> false
    }
}

internal fun readerContinuousBoundaryRequested(
    requestedOffsetPx: Float,
    currentOffsetPx: Float,
    maxOffsetPx: Float,
    overscrollPx: Float,
): Boolean {
    val overscroll = overscrollPx.coerceAtLeast(0f)
    val hitTop = requestedOffsetPx < -overscroll && currentOffsetPx <= 1f
    val hitBottom = requestedOffsetPx > maxOffsetPx + overscroll &&
        currentOffsetPx >= (maxOffsetPx - 1f).coerceAtLeast(0f)
    return hitTop || hitBottom
}

internal class ReaderContinuousScrollModel {
    var offsetPx: Float = 0f
        private set
    var maxOffsetPx: Float = 0f
        private set
    private var scrollSinkOwner: Any? = null
    private var scrollSink: ((Float) -> Unit)? = null

    fun attachScrollSink(owner: Any, sink: (Float) -> Unit) {
        scrollSinkOwner = owner
        scrollSink = sink
        sink(offsetPx)
    }

    fun detachScrollSink(owner: Any) {
        if (scrollSinkOwner !== owner) return
        scrollSinkOwner = null
        scrollSink = null
    }

    internal fun hasScrollSinkFor(owner: Any): Boolean = scrollSinkOwner === owner

    fun setRange(rangePx: Int) {
        setRangeAndOffset(rangePx, offsetPx)
    }

    fun setRangeAndOffset(rangePx: Int, anchoredOffsetPx: Float) {
        maxOffsetPx = rangePx.toFloat().coerceAtLeast(0f)
        val next = anchoredOffsetPx.coerceIn(0f, maxOffsetPx)
        if (next == offsetPx) return
        offsetPx = next
        scrollSink?.invoke(next)
    }

    fun setOffset(value: Float) {
        val next = value.coerceIn(0f, maxOffsetPx)
        if (next == offsetPx) return
        offsetPx = next
        scrollSink?.invoke(next)
    }
}

/**
 * Viewport-sized native continuous renderer. The 4K source window remains authoritative and
 * bounded, but its StaticLayout is split into worker-rasterized bitmap tiles. A scroll frame
 * invalidates only this viewport and blits the visible opaque tiles, avoiding whole-window text
 * replay, full-screen alpha blending, and any synthetic measurement pulse.
 */
private class ReaderContinuousViewportView(context: Context) : View(context) {
    private val viewConfig = ViewConfiguration.get(context)
    private val scroller = OverScroller(context)
    private val density = resources.displayMetrics.density
    private var textLayout: StaticLayout? = null
    private var tileSet: ReaderStaticLayoutBitmapTileSet? = null
    private var textColor: Int = 0
    private var overlay: ReaderContinuousOverlay? = null
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var scrollModel: ReaderContinuousScrollModel? = null
    private var settings = ReaderSettings()
    private var systemLeftInsetPx = 0
    private var systemRightInsetPx = 0
    private var offsetPx = 0f
    private var renderedOffsetPx = 0
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var scrolling = false
    private var pinching = false
    private var pinchStart = 0f
    private var pinchScale = 1f
    private var longPressTriggered = false
    private var lastCenterTapAt = 0L
    private var flingRunning = false
    private var activeFlingVelocityY = 0
    private var pendingHandoffVelocityY = 0
    private var canHandoffPrevious = false
    private var canHandoffNext = false
    private var lastBoundarySignalAt = 0L
    private var pendingScrollY = 0
    private var scrollScheduled = false
    private val scrollSinkCallback: (Float) -> Unit = ::setScrollOffset
    private var pendingCenterTap: Runnable? = null
    private val applyPendingScroll = Runnable {
        scrollScheduled = false
        val next = pendingScrollY.coerceAtLeast(0)
        if (renderedOffsetPx != next) {
            renderedOffsetPx = next
            postInvalidateOnAnimation()
        }
    }
    private var velocityTracker: VelocityTracker? = null
    private var onPrevious: () -> Unit = {}
    private var onNext: () -> Unit = {}
    private var onToggleControls: () -> Unit = {}
    private var onBrightnessDelta: (Float) -> Unit = {}
    private var onResizeFont: (Float) -> Unit = {}
    private var onBookmark: () -> Unit = {}
    private var onAnyTouch: () -> Unit = {}
    private var onScrollSettled: () -> Unit = {}
    private var onLongPress: (Float, Float) -> Unit = { _, _ -> }
    private val longPress = Runnable {
        if (!scrolling && !pinching) {
            longPressTriggered = true
            onLongPress(downX, downY)
        }
    }

    init {
        isClickable = true
        isFocusable = true
    }

    fun configure(
        model: ReaderContinuousScrollModel,
        nextSettings: ReaderSettings,
        leftInsetPx: Int,
        rightInsetPx: Int,
        canPreviousWindowHandoff: Boolean,
        canNextWindowHandoff: Boolean,
        previous: () -> Unit,
        next: () -> Unit,
        toggleControls: () -> Unit,
        brightnessDelta: (Float) -> Unit,
        resizeFont: (Float) -> Unit,
        bookmark: () -> Unit,
        anyTouch: () -> Unit,
        scrollSettled: () -> Unit,
        longPressAction: (Float, Float) -> Unit,
    ) {
        if (scrollModel !== model) scrollModel?.detachScrollSink(this)
        scrollModel = model
        settings = nextSettings
        systemLeftInsetPx = leftInsetPx
        systemRightInsetPx = rightInsetPx
        canHandoffPrevious = canPreviousWindowHandoff
        canHandoffNext = canNextWindowHandoff
        onPrevious = previous
        onNext = next
        onToggleControls = toggleControls
        onBrightnessDelta = brightnessDelta
        onResizeFont = resizeFont
        onBookmark = bookmark
        onAnyTouch = anyTouch
        onScrollSettled = scrollSettled
        onLongPress = longPressAction
        model.attachScrollSink(this, scrollSinkCallback)
    }

    fun setOverlay(next: ReaderContinuousOverlay?) {
        if (overlay == next) return
        overlay = next
        postInvalidateOnAnimation()
    }

    fun setTextLayout(ready: ReaderContinuousLayout, color: Int) {
        val changed = textLayout !== ready.layout || tileSet !== ready.raster || textColor != color
        val previousTiles = tileSet
        textLayout = ready.layout
        tileSet = ready.raster
        textColor = color
        if (previousTiles !== ready.raster) previousTiles?.close()
        if (changed) {
            ready.raster.requestVisible(renderedOffsetPx, height) { postInvalidateOnAnimation() }
            postInvalidateOnAnimation()
            if (pendingHandoffVelocityY != 0) post(::resumePendingFling)
        }
    }

    fun setScrollOffset(value: Float) {
        if (value == offsetPx) return
        offsetPx = value
        pendingScrollY = value.roundToInt()
        if (!scrollScheduled) {
            scrollScheduled = true
            postOnAnimation(applyPendingScroll)
        }
    }

    override fun isOpaque(): Boolean = tileSet != null

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val layout = textLayout ?: return
        val maxOffset = (layout.height - height).coerceAtLeast(0)
        val scrollY = renderedOffsetPx.coerceIn(0, maxOffset)
        tileSet?.requestVisible(scrollY, height) { postInvalidateOnAnimation() }
        val drewRaster = tileSet?.draw(canvas, scrollY, height) == true
        if (!drewRaster) {
            canvas.save()
            canvas.clipRect(0, 0, width, height)
            canvas.translate(0f, -scrollY.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
        drawOverlay(canvas, layout, scrollY)
    }

    private fun drawOverlay(canvas: android.graphics.Canvas, layout: StaticLayout, scrollY: Int) {
        val active = overlay ?: return
        if (layout.lineCount <= 0 || active.endUtf16 <= active.startUtf16 || layout.text.isEmpty()) return
        val start = active.startUtf16.coerceIn(0, layout.text.length)
        val end = active.endUtf16.coerceIn(start + 1, layout.text.length)
        val firstLine = layout.getLineForOffset(start).coerceIn(0, layout.lineCount - 1)
        val lastLine = layout.getLineForOffset((end - 1).coerceAtLeast(start)).coerceIn(firstLine, layout.lineCount - 1)
        overlayPaint.color = active.color
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(0f, -scrollY.toFloat())
        for (line in firstLine..lastLine) {
            val left = if (line == firstLine) layout.getPrimaryHorizontal(start) else layout.getLineLeft(line)
            val right = if (line == lastLine) layout.getPrimaryHorizontal(end) else layout.getLineRight(line)
            val x0 = minOf(left, right)
            val x1 = maxOf(left, right).coerceAtLeast(x0 + 1f)
            canvas.drawRect(
                x0,
                layout.getLineTop(line).toFloat(),
                x1,
                layout.getLineBottom(line).toFloat(),
                overlayPaint,
            )
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!scroller.isFinished) scroller.abortAnimation()
                flingRunning = false
                activeFlingVelocityY = 0
                pendingHandoffVelocityY = 0
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x; downY = event.y; lastY = event.y; downAt = event.eventTime
                scrolling = false; pinching = false; pinchScale = 1f; longPressTriggered = false
                removeCallbacks(longPress)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                onAnyTouch()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPress)
                cancelPendingCenterTap()
                if (event.pointerCount >= 2) {
                    pinching = true
                    pinchStart = pointerDistance(event).coerceAtLeast(1f)
                    pinchScale = 1f
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    pinchScale = (pointerDistance(event) / pinchStart).coerceIn(0.55f, 1.8f)
                    lastY = event.y
                    return true
                }
                val totalX = event.x - downX
                val totalY = event.y - downY
                if (abs(totalX) > viewConfig.scaledTouchSlop || abs(totalY) > viewConfig.scaledTouchSlop) {
                    removeCallbacks(longPress)
                    cancelPendingCenterTap()
                }
                val brightnessZone = settings.brightnessGestureEnabled && downX >= systemLeftInsetPx + 8f * density && downX <= systemLeftInsetPx + width * 0.14f
                if (!brightnessZone && !scrolling && abs(totalY) > viewConfig.scaledTouchSlop && abs(totalY) > abs(totalX) * 1.10f) scrolling = true
                if (scrolling) scrollModel?.let { model ->
                    val requested = model.offsetPx + (lastY - event.y)
                    model.setOffset(requested)
                    signalBoundaryHandoff(requested, model)
                }
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val handledScroll = scrolling
                val handledPinch = pinching
                val handledLongPress = longPressTriggered
                if (handledPinch && settings.pinchFontEnabled && abs(pinchScale - 1f) >= 0.04f) {
                    onResizeFont(pinchScale)
                } else if (!handledLongPress && !handledScroll && !handledPinch) {
                    dispatchCompletedGesture(event)
                    performClick()
                }
                if (handledScroll) finishScrollWithFling()
                recycleTouch()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (scrolling) onScrollSettled()
                recycleTouch()
                return true
            }
        }
        return true
    }

    private fun signalBoundaryHandoff(requestedOffset: Float, model: ReaderContinuousScrollModel) {
        val overscroll = 18f * density
        if (!readerContinuousBoundaryRequested(
                requestedOffset,
                model.offsetPx,
                model.maxOffsetPx,
                overscroll,
            )
        ) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBoundarySignalAt < 180L) return
        lastBoundarySignalAt = now
        onScrollSettled()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val model = scrollModel
            model?.setOffset(scroller.currY.toFloat())
            if (model != null && flingRunning && readerContinuousFlingNeedsHandoff(
                    offsetPx = model.offsetPx,
                    maxOffsetPx = model.maxOffsetPx,
                    viewportHeightPx = height,
                    velocityY = activeFlingVelocityY,
                    canHandoffPrevious = canHandoffPrevious,
                    canHandoffNext = canHandoffNext,
                )
            ) {
                val direction = if (activeFlingVelocityY >= 0) 1 else -1
                pendingHandoffVelocityY = direction * scroller.currVelocity.roundToInt()
                    .coerceAtLeast(viewConfig.scaledMinimumFlingVelocity)
                scroller.abortAnimation()
                flingRunning = false
                activeFlingVelocityY = 0
                onScrollSettled()
            }
        } else if (flingRunning) {
            flingRunning = false
            activeFlingVelocityY = 0
            onScrollSettled()
        }
    }

    private fun resumePendingFling() {
        val velocity = pendingHandoffVelocityY
        val model = scrollModel
        if (velocity == 0 || model == null || model.maxOffsetPx <= 0f) {
            pendingHandoffVelocityY = 0
            return
        }
        pendingHandoffVelocityY = 0
        activeFlingVelocityY = velocity
        scroller.fling(
            0,
            model.offsetPx.roundToInt(),
            0,
            velocity,
            0,
            0,
            0,
            model.maxOffsetPx.roundToInt(),
        )
        flingRunning = true
        postInvalidateOnAnimation()
    }

    private fun finishScrollWithFling() {
        val model = scrollModel ?: return onScrollSettled()
        val tracker = velocityTracker ?: return onScrollSettled()
        tracker.computeCurrentVelocity(1000, viewConfig.scaledMaximumFlingVelocity.toFloat())
        val velocity = (-tracker.yVelocity).roundToInt()
        if (abs(velocity) >= viewConfig.scaledMinimumFlingVelocity && model.maxOffsetPx > 0f) {
            scroller.fling(0, model.offsetPx.roundToInt(), 0, velocity, 0, 0, 0, model.maxOffsetPx.roundToInt())
            activeFlingVelocityY = velocity
            flingRunning = true
            postInvalidateOnAnimation()
        } else onScrollSettled()
    }

    private fun dispatchCompletedGesture(event: MotionEvent) {
        val dx = event.x - downX
        val dy = event.y - downY
        val swipe = 52f * density
        val tapSlop = 14f * density
        val edgeGuard = 8f * density
        if (settings.brightnessGestureEnabled && downX >= systemLeftInsetPx + edgeGuard && downX <= systemLeftInsetPx + width * 0.14f && abs(dy) > abs(dx) * 1.35f && abs(dy) >= swipe) {
            onBrightnessDelta((-dy / height.coerceAtLeast(1).toFloat()) * 0.8f)
            return
        }
        val pagedNavigation = settings.readingMode == ReaderMode.PAGED
        if (pagedNavigation && settings.swipePagingEnabled && downX > systemLeftInsetPx + edgeGuard && downX < width - systemRightInsetPx - edgeGuard && abs(dx) >= swipe && abs(dx) > abs(dy) * 1.25f) {
            var forward = dx < 0f
            if (settings.reversePagingGestures) forward = !forward
            if (forward) onNext() else onPrevious()
            return
        }
        if (event.eventTime - downAt > 360L || hypot(dx.toDouble(), dy.toDouble()).toFloat() > tapSlop || width <= 0) return
        if (downX <= systemLeftInsetPx + edgeGuard || downX >= width - systemRightInsetPx - edgeGuard) return
        val edge = when (settings.tapZonePreset) {
            ReaderTapZonePreset.BALANCED, ReaderTapZonePreset.CUSTOM -> width * settings.tapZoneEdgeFraction
            ReaderTapZonePreset.RIGHT_HANDED -> width * 0.22f
            ReaderTapZonePreset.LEFT_HANDED -> width * 0.32f
        }
        when {
            pagedNavigation && downX < edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onNext() else onPrevious()
            pagedNavigation && downX > width - edge && settings.tapPagingEnabled -> if (settings.reversePagingGestures) onPrevious() else onNext()
            else -> dispatchCenterTap(event.eventTime)
        }
    }

    private fun dispatchCenterTap(tapAt: Long) {
        fun dispatch(action: ReaderGestureAction) {
            when (action) {
                ReaderGestureAction.CONTROLS -> onToggleControls()
                ReaderGestureAction.BOOKMARK -> onBookmark()
                ReaderGestureAction.NEXT -> onNext()
                ReaderGestureAction.PREVIOUS -> onPrevious()
                ReaderGestureAction.NONE -> Unit
            }
        }
        val centerAction = if (settings.advancedGestureCustomizationEnabled) settings.centerTapAction else ReaderGestureAction.CONTROLS
        val doubleAction = if (settings.advancedGestureCustomizationEnabled) settings.doubleTapAction else if (settings.doubleTapBookmarkEnabled) ReaderGestureAction.BOOKMARK else ReaderGestureAction.NONE
        if (doubleAction == ReaderGestureAction.NONE) {
            cancelPendingCenterTap()
            dispatch(centerAction)
            return
        }
        if (ReaderGesturePolicy.isDoubleTap(lastCenterTapAt, tapAt)) {
            cancelPendingCenterTap()
            dispatch(doubleAction)
            return
        }
        cancelPendingCenterTap()
        lastCenterTapAt = tapAt
        val task = Runnable {
            if (lastCenterTapAt == tapAt) {
                lastCenterTapAt = 0L
                pendingCenterTap = null
                dispatch(centerAction)
            }
        }
        pendingCenterTap = task
        postDelayed(task, DOUBLE_TAP_CONFIRM_MS)
    }

    private fun cancelPendingCenterTap() {
        pendingCenterTap?.let(::removeCallbacks)
        pendingCenterTap = null
        lastCenterTapAt = 0L
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot((event.getX(0) - event.getX(1)).toDouble(), (event.getY(0) - event.getY(1)).toDouble()).toFloat()
    }

    private fun recycleTouch() {
        removeCallbacks(longPress)
        velocityTracker?.recycle(); velocityTracker = null
        scrolling = false; pinching = false
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(applyPendingScroll)
        removeCallbacks(longPress)
        cancelPendingCenterTap()
        scrollModel?.detachScrollSink(this)
        scrollModel = null
        if (!scroller.isFinished) scroller.abortAnimation()
        flingRunning = false
        activeFlingVelocityY = 0
        pendingHandoffVelocityY = 0
        velocityTracker?.recycle()
        velocityTracker = null
        scrollScheduled = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DOUBLE_TAP_CONFIRM_MS = 330L
    }
}

internal fun readerContinuousTilePrefetchIndices(
    scrollY: Int,
    viewportHeight: Int,
    tileHeight: Int,
    tileCount: Int,
): List<Int> {
    if (tileCount <= 0 || tileHeight <= 0) return emptyList()
    val first = (scrollY.coerceAtLeast(0) / tileHeight).coerceIn(0, tileCount - 1)
    val last = ((scrollY.coerceAtLeast(0) + viewportHeight.coerceAtLeast(1) - 1) / tileHeight)
        .coerceIn(first, tileCount - 1)
    val start = (first - 1).coerceAtLeast(0)
    val end = (last + 1).coerceAtMost(tileCount - 1)
    return (start..end).toList()
}

internal class ReaderStaticLayoutBitmapTileSet(
    private val layout: StaticLayout,
    requestedTileHeightPx: Int,
    private val backgroundColor: Int,
) : AutoCloseable {
    private data class Tile(val top: Int, val bitmap: android.graphics.Bitmap)
    private val width = layout.width.coerceAtLeast(1)
    private val totalHeight = layout.height.coerceAtLeast(1)
    private val tileHeightPx = requestedTileHeightPx.coerceIn(MIN_BITMAP_TILE_HEIGHT_PX, MAX_BITMAP_TILE_HEIGHT_PX)
    private val tileCount = ((totalHeight + tileHeightPx - 1) / tileHeightPx).coerceAtLeast(1)
    private val tiles = object : java.util.LinkedHashMap<Int, Tile>(MAX_CACHED_TILES, 0.75f, true) {}
    private val inFlight = mutableSetOf<Int>()
    @Volatile private var closed = false

    init {
        // Construction already runs on the layout worker. Seed only the opening tile; all other
        // tiles are created on demand and the LRU is bounded independently of source-window height.
        publishTile(0, renderTile(0))
    }

    fun requestVisible(scrollY: Int, viewportHeight: Int, onReady: () -> Unit) {
        readerContinuousTilePrefetchIndices(scrollY, viewportHeight, tileHeightPx, tileCount)
            .forEach { requestTile(it, onReady) }
    }

    @Synchronized
    fun draw(canvas: android.graphics.Canvas, scrollY: Int, viewportHeight: Int): Boolean {
        if (closed) return false
        val visible = readerContinuousTilePrefetchIndices(
            scrollY,
            viewportHeight,
            tileHeightPx,
            tileCount,
        ).filter { index ->
            val top = index * tileHeightPx
            top < scrollY + viewportHeight.coerceAtLeast(1) && top + tileHeightPx > scrollY
        }
        if (visible.isEmpty() || visible.any { !tiles.containsKey(it) }) return false
        visible.forEach { index ->
            val tile = tiles[index] ?: return false
            canvas.drawBitmap(tile.bitmap, 0f, (tile.top - scrollY).toFloat(), null)
        }
        return true
    }

    private fun requestTile(index: Int, onReady: () -> Unit) {
        synchronized(this) {
            if (closed || index !in 0 until tileCount || tiles.containsKey(index) || !inFlight.add(index)) return
        }
        TILE_WORKER.execute {
            val tile = runCatching { renderTile(index) }.getOrNull()
            var published = false
            synchronized(this) {
                inFlight.remove(index)
                if (!closed && tile != null) {
                    publishTileLocked(index, tile)
                    published = true
                } else {
                    tile?.bitmap?.recycle()
                }
            }
            if (published) onReady()
        }
    }

    private fun renderTile(index: Int): Tile {
        val top = index * tileHeightPx
        val tileHeight = minOf(tileHeightPx, totalHeight - top).coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, tileHeight, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(backgroundColor)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.save()
        canvas.clipRect(0, 0, width, tileHeight)
        canvas.translate(0f, -top.toFloat())
        layout.draw(canvas)
        canvas.restore()
        bitmap.setHasAlpha(false)
        bitmap.prepareToDraw()
        return Tile(top, bitmap)
    }

    @Synchronized
    private fun publishTile(index: Int, tile: Tile) {
        if (closed) {
            tile.bitmap.recycle()
            return
        }
        publishTileLocked(index, tile)
    }

    private fun publishTileLocked(index: Int, tile: Tile) {
        // Never recycle a tile that may still be referenced by the previous hardware display list.
        // Dropping the strong reference is enough to bound retained memory while Android/GC can
        // release the pixels after RenderThread is finished with them.
        tiles[index] = tile
        while (tiles.size > MAX_CACHED_TILES) {
            val iterator = tiles.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    @Synchronized
    internal fun cachedTileCountForTest(): Int = tiles.size

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        tiles.clear()
        inFlight.clear()
    }

    private companion object {
        const val MIN_BITMAP_TILE_HEIGHT_PX = 256
        const val MAX_BITMAP_TILE_HEIGHT_PX = 1536
        const val MAX_CACHED_TILES = 5
        val TILE_WORKER = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "jingdu-continuous-raster").apply { isDaemon = true }
        }
    }
}

/** Continuous keeps the 4K bounded window; real scroll frames blit only visible worker-rasterized tiles. */

@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    overflow: TextOverflow,
    scrollModel: ReaderContinuousScrollModel,
    settings: ReaderSettings,
    systemLeftInsetPx: Int,
    systemRightInsetPx: Int,
    canHandoffPrevious: Boolean,
    canHandoffNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onAnyTouch: () -> Unit,
    onScrollSettled: () -> Unit,
    overlay: ReaderContinuousOverlay? = null,
    selectionMode: Boolean? = null,
    onRequestSelectionAt: ((Int) -> Unit)? = null,
    onTextLayout: (ReaderContinuousLayout) -> Float?,
) {
    var internalSelectionMode by remember(text.text) { mutableStateOf(false) }
    val fallback = (selectionMode ?: internalSelectionMode) || rememberReaderTouchExplorationEnabled()
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val nativeTypeface by resolver.resolveAsTypeface(
        fontFamily = style.fontFamily,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
        fontStyle = style.fontStyle ?: FontStyle.Normal,
        fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
    )
    val resolvedColor = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else style.color
    val rasterBackgroundColor = remember(settings.palette) { readerContinuousRasterBackgroundColor(settings.palette) }
    val baseModifier = modifier.fillMaxSize().clipToBounds()
    BoxWithConstraints(baseModifier) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        val viewportHeightPx = constraints.maxHeight.coerceAtLeast(1)
        val layout by produceState<ReaderContinuousLayout?>(
            null,
            text,
            style,
            overflow,
            widthPx,
            viewportHeightPx,
            density.density,
            density.fontScale,
            nativeTypeface,
            resolvedColor,
            rasterBackgroundColor,
        ) {
            ReaderInteractionRuntime.continuousReady = false
            val ready = withContext(Dispatchers.Default) {
                val staticLayout = buildFastStaticLayout(text, style, density, nativeTypeface, resolvedColor, widthPx)
                ReaderContinuousLayout(
                    staticLayout,
                    ReaderStaticLayoutBitmapTileSet(
                        staticLayout,
                        viewportHeightPx.coerceAtLeast(1),
                        rasterBackgroundColor,
                    ),
                )
            }
            // Compute the source anchor while the previous layout is still displayed, then commit
            // range + offset before exposing the replacement layout. No frame can render a new
            // window using stale pixel coordinates from the outgoing window.
            val anchoredOffset = onTextLayout(ready) ?: scrollModel.offsetPx
            scrollModel.setRangeAndOffset((ready.height - viewportHeightPx).coerceAtLeast(0), anchoredOffset)
            value = ready
            ReaderInteractionRuntime.continuousReady = true
        }
        val ready = layout
        if (fallback) {
            val fallbackScroll = rememberScrollState(initial = scrollModel.offsetPx.roundToInt().coerceAtLeast(0))
            LaunchedEffect(fallbackScroll.value) {
                scrollModel.setOffset(fallbackScroll.value.toFloat())
            }
            LaunchedEffect(fallbackScroll.isScrollInProgress) {
                if (!fallbackScroll.isScrollInProgress) {
                    scrollModel.setOffset(fallbackScroll.value.toFloat())
                    onScrollSettled()
                }
            }
            LaunchedEffect(text.text, ready) {
                if (ready != null) {
                    withFrameNanos { }
                    fallbackScroll.scrollTo(
                        scrollModel.offsetPx.roundToInt().coerceIn(0, fallbackScroll.maxValue),
                    )
                }
            }
            androidx.compose.material3.Text(
                text = text,
                modifier = Modifier.fillMaxSize().verticalScroll(fallbackScroll),
                style = style,
                overflow = overflow,
            )
        } else if (ready != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { androidContext -> ReaderContinuousViewportView(androidContext) },
                update = { viewport ->
                    viewport.setTextLayout(ready, resolvedColor.toArgb())
                    viewport.setOverlay(overlay)
                    viewport.configure(
                        scrollModel,
                        settings,
                        systemLeftInsetPx,
                        systemRightInsetPx,
                        canHandoffPrevious,
                        canHandoffNext,
                        onPrevious,
                        onNext,
                        onToggleControls,
                        onBrightnessDelta,
                        onResizeFont,
                        onBookmark,
                        onAnyTouch,
                        onScrollSettled,
                    ) { x, y ->
                        val line = ready.getLineForVerticalPosition(scrollModel.offsetPx + y)
                        val utf = ready.getOffsetForHorizontal(line, x)
                        if (onRequestSelectionAt != null) onRequestSelectionAt(utf)
                        else internalSelectionMode = true
                    }
                },
            )
        }
    }
}

private fun readerContinuousRasterBackgroundColor(palette: ReaderPalette): Int = when (palette) {
    ReaderPalette.PAPER -> 0xFFF7F0DE.toInt()
    ReaderPalette.LIGHT -> 0xFFFFFBFF.toInt()
    ReaderPalette.SEPIA -> 0xFFF3E5C8.toInt()
    ReaderPalette.NIGHT -> 0xFF151713.toInt()
    ReaderPalette.OLED -> 0xFF000000.toInt()
}

private fun buildFastStaticLayout(text: AnnotatedString, style: TextStyle, density: androidx.compose.ui.unit.Density, nativeTypeface: Typeface, resolvedColor: Color, widthPx: Int): StaticLayout {
    val fontSizePx = with(density) { style.fontSize.toPx() }.coerceAtLeast(1f)
    val lineHeightMultiplier = if (style.lineHeight == TextUnit.Unspecified || style.lineHeight.value <= 0f || style.fontSize.value <= 0f) 1f else (style.lineHeight.value / style.fontSize.value).coerceAtLeast(1f)
    val letterSpacingEm = if (style.letterSpacing == TextUnit.Unspecified || style.fontSize.value <= 0f) 0f else style.letterSpacing.value / style.fontSize.value
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { color = resolvedColor.toArgb(); textSize = fontSizePx; letterSpacing = letterSpacingEm; typeface = nativeTypeface }
    val rendered = fastSpannable(text, style, density, resolvedColor)
    return StaticLayout.Builder.obtain(rendered, 0, rendered.length, paint, widthPx)
        .setIncludePad(false)
        .setLineSpacing(0f, lineHeightMultiplier)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
        .apply { if (style.textAlign == TextAlign.Justify) setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD) }
        .build()
}

private fun Modifier.armSelectionOnLongPress(key: String, onLongPress: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val finishedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (finishedEarly == null) onLongPress()
    }
}

private fun fastSpannable(text: AnnotatedString, style: TextStyle, density: androidx.compose.ui.unit.Density, fallbackColor: Color): SpannableString {
    val value = SpannableString(text.text)
    text.spanStyles.forEach { range ->
        val start = range.start.coerceIn(0, value.length); val end = range.end.coerceIn(start, value.length)
        if (end <= start) return@forEach
        val span = range.item
        if (span.background != Color.Unspecified) value.setSpan(BackgroundColorSpan(span.background.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (span.color != Color.Unspecified && span.color != fallbackColor) value.setSpan(ForegroundColorSpan(span.color.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if ((span.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold) value.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    text.paragraphStyles.forEach { range ->
        val lineHeight = range.item.lineHeight
        if (lineHeight != TextUnit.Unspecified && lineHeight.value > 0f) {
            val start = range.start.coerceIn(0, value.length); val end = range.end.coerceIn(start, value.length)
            if (end > start) value.setSpan(FastExactLineHeightSpan(with(density) { lineHeight.toPx() }.roundToInt().coerceAtLeast(1)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    val indent = style.textIndent?.firstLine
    if (indent != null && indent != TextUnit.Unspecified && indent.value > 0f) {
        val margin = with(density) { indent.toPx() }.roundToInt().coerceAtLeast(0)
        if (margin > 0) {
            var start = 0
            while (start < value.length) {
                val end = text.text.indexOf('\n', start).let { if (it < 0) value.length else it + 1 }
                if (start < end && text.text[start] != ReaderTypographySpec.PARAGRAPH_SPACER) value.setSpan(LeadingMarginSpan.Standard(margin, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = end
            }
        }
    }
    return value
}

private class FastExactLineHeightSpan(private val heightPx: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val original = fm.descent - fm.ascent
        if (original <= 0) return
        val ratio = heightPx.toFloat() / original.toFloat()
        fm.descent = (fm.descent * ratio).roundToInt(); fm.ascent = fm.descent - heightPx
    }
}
