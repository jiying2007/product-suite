package com.junchen.jingdu

import android.content.Context
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.Closeable
import java.util.LinkedHashMap
import kotlin.math.roundToInt

internal data class SourceDisplayMap(private val projection: TextProjection) {
    val sourceCodePoints: Long get() = projection.sourceCodePoints
    val displayCodePoints: Long get() = projection.displayCodePoints
    fun sourceForDisplay(displayed: Long): Long = projection.sourceForDisplay(displayed)
    fun displayForSource(source: Long): Long = projection.displayForSource(source)

    companion object {
        fun between(source: String, display: String): SourceDisplayMap = SourceDisplayMap(TextProjection.between(source, display))
        fun compose(first: TextProjection, second: TextProjection): SourceDisplayMap = SourceDisplayMap(first.compose(second))
    }
}

internal data class ReaderDisplayWindow(
    val start: Long,
    val sourceText: String,
    val displayText: String,
    val documentLength: Long,
    val map: SourceDisplayMap,
)

/** Bounded read/presentation pipeline shared by paged and continuous modes. */
internal class ReaderViewportEngine(context: Context, private val bookId: String) : Closeable {
    // Continuous mode owns its own aligned display windows; the page-turn cache would only duplicate
    // memory and I/O here, so this controller explicitly disables it.
    private val reader = ReaderController(false)
    private val cache = object : LinkedHashMap<WindowKey, ReaderDisplayWindow>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WindowKey, ReaderDisplayWindow>?): Boolean = size > MAX_WINDOWS
    }

    init {
        val repository = BookRepository(context.applicationContext)
        val book = repository.list().firstOrNull { it.id == bookId } ?: error("book unavailable")
        reader.open(repository.normalizedFile(book), 0)
    }

    @Synchronized
    fun readAround(position: Long, settings: ReaderSettings): ReaderDisplayWindow {
        val length = reader.length()
        if (length <= 0) return ReaderDisplayWindow(0, "", "", 0, SourceDisplayMap.between("", ""))
        val bounded = position.coerceIn(0, length - 1)
        val continuous = settings.readingMode == ReaderMode.CONTINUOUS
        val windowChars = if (continuous) CONTINUOUS_WINDOW_CHARS else ReaderController.WINDOW_CHARS
        val alignChars = if (continuous) CONTINUOUS_ALIGN_CHARS else PAGE_ALIGN_CHARS
        val backBufferChars = if (continuous) CONTINUOUS_BACK_BUFFER_CHARS else PAGE_BACK_BUFFER_CHARS
        val aligned = ((bounded - backBufferChars).coerceAtLeast(0) / alignChars) * alignChars
        val key = WindowKey(aligned, presentationKey(settings), windowChars)
        cache[key]?.let { return it }
        val source = reader.readAt(aligned, windowChars)
        val presented = ReaderPresentationPipeline.present(source, settings, prewarmSelection = continuous)
        val result = ReaderDisplayWindow(
            start = aligned,
            sourceText = source,
            displayText = presented.displayText,
            documentLength = length,
            map = presented.map,
        )
        cache[key] = result
        return result
    }

    @Synchronized
    fun prefetch(position: Long, settings: ReaderSettings) {
        if (reader.length() <= 0) return
        val windowChars = if (settings.readingMode == ReaderMode.CONTINUOUS) CONTINUOUS_WINDOW_CHARS else ReaderController.WINDOW_CHARS
        readAround(position, settings)
        readAround((position + windowChars / 2).coerceAtMost(reader.length() - 1), settings)
        readAround((position - windowChars / 2).coerceAtLeast(0), settings)
    }

    @Synchronized fun clear() = cache.clear()

    @Synchronized
    override fun close() {
        cache.clear()
        reader.close()
    }

    private fun presentationKey(settings: ReaderSettings): Int = listOf(
        settings.chineseMode.name,
        settings.chineseOverrides.hashCode(),
        settings.compressBlankLines,
        settings.paragraphSpacingEm,
    ).hashCode()

    private data class WindowKey(val start: Long, val presentationKey: Int, val windowChars: Long)

    private companion object {
        const val MAX_WINDOWS = 8
        const val PAGE_ALIGN_CHARS = 512L
        const val PAGE_BACK_BUFFER_CHARS = 384L
        // Hosted #642 measured the best continuous steady state with the original 4 KiB
        // window (P95 55.1 ms versus 70+ ms after 3/2 KiB experiments). Keep multiple screens of
        // headroom so real swipes do not churn window boundaries while remaining strictly bounded.
        const val CONTINUOUS_WINDOW_CHARS = 4096L
        const val CONTINUOUS_ALIGN_CHARS = 1024L
        const val CONTINUOUS_BACK_BUFFER_CHARS = 1024L
    }
}

data class PageLayoutKey(
    val textHash: Int,
    val widthPx: Int,
    val heightPx: Int,
    val typographyFingerprint: Int,
    val columns: Int,
)

data class PageLayoutSnapshot(
    val displayedEndUtf16: Int,
    val displayedCodePoints: Long,
    val sourceCodePoints: Long,
    val firstColumnEndUtf16: Int,
    val reusableVisibleText: String = "",
    val reusableWidthPx: Int = 0,
    val reusableHeightPx: Int = 0,
    val reusableLayout: StaticLayout? = null,
    val reusableHasHeadingStyle: Boolean = false,
)

/** Small LRU used to keep exact page measurement out of repeated Compose layout churn. */
internal object ReaderPageLayoutCache {
    private val cache = object : LinkedHashMap<PageLayoutKey, PageLayoutSnapshot>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageLayoutKey, PageLayoutSnapshot>?): Boolean = size > 16
    }
    private var mostRecent: PageLayoutSnapshot? = null

    @Synchronized
    fun get(key: PageLayoutKey): PageLayoutSnapshot? = cache[key]?.also { mostRecent = it }

    @Synchronized
    fun put(key: PageLayoutKey, value: PageLayoutSnapshot) {
        cache[key] = value
        mostRecent = value
    }

    @Synchronized
    fun clear() {
        cache.clear()
        mostRecent = null
    }

    /**
     * Paged rendering normally receives exactly the visible prefix measured below. For a plain body
     * page the measurement StaticLayout is already the authoritative layout, so drawing can reuse it
     * instead of competing with the frame thread by constructing the same layout a second time.
     * Styled pages (headings/highlights/TTS) deliberately fall back in ReaderFastText so their spans
     * remain authoritative.
     */
    @Synchronized
    fun reusableLayoutFor(visibleText: String, widthPx: Int, heightPx: Int, hasHeadingStyle: Boolean): StaticLayout? {
        if (visibleText.isEmpty() || widthPx <= 0 || heightPx <= 0) return null
        fun matches(snapshot: PageLayoutSnapshot): Boolean =
            snapshot.reusableWidthPx == widthPx &&
                snapshot.reusableHeightPx == heightPx &&
                snapshot.reusableVisibleText == visibleText &&
                snapshot.reusableHasHeadingStyle == hasHeadingStyle

        // The draw immediately following page measurement overwhelmingly asks for the snapshot that
        // was just inserted. Resolve that case without allocating cache.values.toList() on the UI
        // path; only history/back-navigation falls through to the tiny bounded LRU scan.
        mostRecent?.takeIf(::matches)?.let { return it.reusableLayout }
        val iterator = cache.values.iterator()
        var match: PageLayoutSnapshot? = null
        while (iterator.hasNext()) {
            val snapshot = iterator.next()
            if (matches(snapshot)) match = snapshot
        }
        if (match != null) {
            mostRecent = match
            return match.reusableLayout
        }
        return null
    }

    fun measure(
        sourceText: String,
        displayText: String,
        widthPx: Int,
        heightPx: Int,
        columns: Int,
        settings: ReaderSettings,
        density: Density,
        typeface: Typeface? = null,
        map: SourceDisplayMap? = null,
    ): PageLayoutSnapshot {
        val safeColumns = columns.coerceIn(1, 2)
        val maxContentWidth = with(density) { (if (safeColumns == 2) 1200.dp else 760.dp).toPx() }.roundToInt()
        val horizontalPadding = with(density) { settings.horizontalPaddingDp.dp.toPx() }.roundToInt() * 2
        val verticalPadding = with(density) { settings.verticalPaddingDp.dp.toPx() }.roundToInt() * 2
        val boundedWidth = minOf(widthPx, maxContentWidth).coerceAtLeast(1)
        val gap = if (safeColumns == 2) with(density) { 28.dp.toPx() }.roundToInt() else 0
        val columnWidth = ((boundedWidth - horizontalPadding - gap) / safeColumns).coerceAtLeast(1)
        val contentHeight = (heightPx - verticalPadding).coerceAtLeast(1)
        val spec = ReaderTypographySpec.from(settings)
        val sourceHash = sourceText.hashCode()
        val displayHash = if (sourceText === displayText) sourceHash else displayText.hashCode()
        val key = PageLayoutKey(
            textHash = 31 * sourceHash + displayHash,
            widthPx = columnWidth,
            heightPx = contentHeight,
            typographyFingerprint = 31 * spec.fingerprint + settings.emphasizeHeadings.hashCode(),
            columns = safeColumns,
        )
        get(key)?.let { return it }

        val cjk = ReaderCjkTypography.containsCjk(displayText)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = with(density) { spec.fontSizeSp.sp.toPx() }
            letterSpacing = spec.letterSpacingEm
            this.typeface = typeface ?: Typeface.create(Typeface.SANS_SERIF, when (spec.weight) {
                ReaderFontWeight.NORMAL -> Typeface.NORMAL
                ReaderFontWeight.MEDIUM, ReaderFontWeight.SEMIBOLD -> Typeface.BOLD
            })
            if (cjk) textLocale = ReaderCjkTypography.localeFor(displayText, settings.chineseMode)
        }
        val layoutText = spec.androidLayoutText(displayText, density, settings.emphasizeHeadings)
        // A line cannot be shorter than the paint's font box. Capping layout construction to the
        // maximum number of lines that could physically intersect the viewport keeps exact page
        // boundaries while preventing measurement/draw work for text that is guaranteed off-screen.
        val minimumLineHeight = (paint.fontMetricsInt.descent - paint.fontMetricsInt.ascent).coerceAtLeast(1)
        val maxVisibleLines = ((contentHeight + minimumLineHeight - 1) / minimumLineHeight + 2).coerceAtLeast(2)
        fun buildLayout(text: CharSequence): StaticLayout? {
            if (text.isEmpty()) return null
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, columnWidth)
                .setIncludePad(false)
                .setMaxLines(maxVisibleLines)
                .setLineSpacing(0f, spec.lineHeightMultiplier.coerceAtLeast(1f))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                // Keep the proven fixed-cost strategy on the page-turn hot path. API 33+ can still
                // apply CJK punctuation/phrase rules without switching to a costlier break strategy.
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            if (Build.VERSION.SDK_INT >= 33 && cjk) {
                builder.setLineBreakConfig(
                    LineBreakConfig.Builder()
                        .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NORMAL)
                        .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                        .build(),
                )
            }
            if (spec.alignment == ReaderTextAlignment.JUSTIFY) builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
            return builder.build()
        }
        fun endFor(layout: StaticLayout?, textLength: Int): Int {
            if (layout == null || layout.lineCount <= 0) return 0
            val line = layout.getLineForVertical((contentHeight - 1).coerceAtLeast(0))
            return layout.getLineEnd(line.coerceIn(0, layout.lineCount - 1)).coerceIn(0, textLength)
        }

        val firstLayout = buildLayout(layoutText)
        val firstEnd = endFor(firstLayout, layoutText.length)
        val secondEnd = if (safeColumns == 2 && firstEnd < layoutText.length) {
            val secondText = layoutText.subSequence(firstEnd, layoutText.length)
            firstEnd + endFor(buildLayout(secondText), secondText.length)
        } else firstEnd
        val displayedEnd = secondEnd.coerceIn(0, displayText.length)
        val displayedPoints = displayText.codePointCount(0, displayedEnd).toLong()
        val projection = map ?: SourceDisplayMap.between(sourceText, displayText)
        val sourcePoints = projection.sourceForDisplay(displayedPoints)
        val reusable = if (safeColumns == 1 && firstLayout != null) firstLayout else null
        val reusableVisible = if (reusable != null) displayText.substring(0, displayedEnd) else ""
        val reusableHasHeadingStyle = reusable != null && settings.emphasizeHeadings &&
            reusableVisible.lineSequence().any { ReaderHeadingClassifier.isHeading(it.trim()) }
        return PageLayoutSnapshot(
            displayedEndUtf16 = displayedEnd,
            displayedCodePoints = displayedPoints,
            sourceCodePoints = sourcePoints,
            firstColumnEndUtf16 = firstEnd,
            reusableVisibleText = reusableVisible,
            reusableWidthPx = if (reusable != null) columnWidth else 0,
            reusableHeightPx = if (reusable != null) contentHeight else 0,
            reusableLayout = reusable,
            reusableHasHeadingStyle = reusableHasHeadingStyle,
        ).also { put(key, it) }
    }
}
