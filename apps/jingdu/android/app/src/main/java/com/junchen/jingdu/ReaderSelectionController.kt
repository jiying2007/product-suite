package com.junchen.jingdu

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import java.text.BreakIterator
import java.util.Locale
import java.util.WeakHashMap

data class ReaderSelectionRange(
    val sourceStart: Long,
    val sourceEnd: Long,
    val excerpt: String,
) {
    init { require(sourceStart >= 0 && sourceEnd >= sourceStart) }
}

/** Pure bounded selection logic. UI handles only provide display offsets. */
internal object ReaderSelectionController {
    private const val SOURCE_TAG = "jingdu.source.relative-range"
    private const val SOURCE_BASE_TAG = "jingdu.source.base"
    private val preparedSelectionMaps = WeakHashMap<SourceDisplayMap, AnnotatedString>()

    /**
     * Builds the per-code-point source map on the same worker that prepares the display projection.
     * Selection remains exact, but normal reader frames no longer allocate thousands of annotations
     * on the main thread for every new page/window.
     */
    fun prewarmSelectionMap(displayText: String, map: SourceDisplayMap) {
        synchronized(preparedSelectionMaps) {
            if (preparedSelectionMaps.containsKey(map)) return
        }
        val prepared = selectionMap(displayText, map)
        synchronized(preparedSelectionMaps) { preparedSelectionMaps[map] = prepared }
    }

    fun annotatedForSelection(
        sourceBase: Long,
        displayText: AnnotatedString,
        map: SourceDisplayMap,
    ): AnnotatedString {
        if (displayText.isEmpty()) return displayText
        val prepared = synchronized(preparedSelectionMaps) { preparedSelectionMaps[map] }
            ?.takeIf { it.text.startsWith(displayText.text) }
            ?: selectionMap(displayText.text, map).also { value ->
                synchronized(preparedSelectionMaps) { preparedSelectionMaps[map] = value }
            }
        val selection = if (prepared.length == displayText.length) prepared else prepared.subSequence(0, displayText.length)
        return buildAnnotatedString {
            append(selection)
            displayText.spanStyles.forEach { range -> addStyle(range.item, range.start, range.end) }
            displayText.paragraphStyles.forEach { range -> addStyle(range.item, range.start, range.end) }
            addStringAnnotation(SOURCE_BASE_TAG, sourceBase.toString(), 0, displayText.length)
        }
    }

    private fun selectionMap(displayText: String, map: SourceDisplayMap): AnnotatedString = buildAnnotatedString {
        append(displayText)
        if (displayText.isEmpty()) return@buildAnnotatedString
        var utfStart = 0
        var displayCp = 0L
        while (utfStart < displayText.length) {
            val cp = Character.codePointAt(displayText, utfStart)
            val utfEnd = utfStart + Character.charCount(cp)
            val sourceStart = map.sourceForDisplay(displayCp)
            val sourceEnd = map.sourceForDisplay(displayCp + 1).coerceAtLeast(sourceStart + 1)
            addStringAnnotation(SOURCE_TAG, "$sourceStart:$sourceEnd", utfStart, utfEnd)
            utfStart = utfEnd
            displayCp++
        }
    }

    fun fromSelectedTexts(selectedTexts: List<AnnotatedString>): ReaderSelectionRange? {
        if (selectedTexts.isEmpty()) return null
        var start = Long.MAX_VALUE
        var end = -1L
        val excerpt = StringBuilder()
        selectedTexts.forEach { selected ->
            if (selected.isEmpty()) return@forEach
            if (excerpt.isNotEmpty()) excerpt.append('\n')
            excerpt.append(selected.text.replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), ""))
            val sourceBase = selected.getStringAnnotations(SOURCE_BASE_TAG, 0, selected.length)
                .firstOrNull()?.item?.toLongOrNull() ?: return@forEach
            selected.getStringAnnotations(SOURCE_TAG, 0, selected.length).forEach { annotation ->
                val parts = annotation.item.split(':', limit = 2)
                val a = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
                val b = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
                start = minOf(start, sourceBase + a)
                end = maxOf(end, sourceBase + b)
            }
        }
        if (start == Long.MAX_VALUE || end <= start) return null
        return ReaderSelectionRange(start, end, excerpt.toString().trim().take(MAX_EXCERPT))
    }

    fun wordAt(
        sourceBase: Long,
        displayText: String,
        displayUtf16: Int,
        map: SourceDisplayMap,
        locale: Locale,
    ): ReaderSelectionRange? {
        if (displayText.isEmpty()) return null
        val safe = displayUtf16.coerceIn(0, displayText.length - 1)
        val breaker = BreakIterator.getWordInstance(locale).apply { setText(displayText) }
        var start = breaker.preceding((safe + 1).coerceAtMost(displayText.length))
        if (start == BreakIterator.DONE) start = 0
        var end = breaker.following(safe)
        if (end == BreakIterator.DONE) end = displayText.length
        while (start < end && displayText[start].isWhitespace()) start++
        while (end > start && displayText[end - 1].isWhitespace()) end--
        if (end <= start) return null
        return rangeForDisplay(sourceBase, displayText, start, end, map)
    }

    fun rangeForDisplay(
        sourceBase: Long,
        displayText: String,
        displayStartUtf16: Int,
        displayEndUtf16: Int,
        map: SourceDisplayMap,
    ): ReaderSelectionRange? {
        if (displayText.isEmpty()) return null
        val a = minOf(displayStartUtf16, displayEndUtf16).coerceIn(0, displayText.length)
        val b = maxOf(displayStartUtf16, displayEndUtf16).coerceIn(a, displayText.length)
        if (b <= a) return null
        val displayStartCp = displayText.codePointCount(0, a).toLong()
        val displayEndCp = displayText.codePointCount(0, b).toLong()
        val sourceStart = sourceBase + map.sourceForDisplay(displayStartCp)
        val sourceEnd = sourceBase + map.sourceForDisplay(displayEndCp)
        if (sourceEnd <= sourceStart) return null
        return ReaderSelectionRange(sourceStart, sourceEnd, displayText.substring(a, b).replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), "").trim().take(MAX_EXCERPT))
    }

    fun updateBoundary(
        current: ReaderSelectionRange,
        moveStart: Boolean,
        sourceBase: Long,
        displayText: String,
        displayUtf16: Int,
        map: SourceDisplayMap,
    ): ReaderSelectionRange {
        val safeUtf = displayUtf16.coerceIn(0, displayText.length)
        val displayCp = displayText.codePointCount(0, safeUtf).toLong()
        val source = sourceBase + map.sourceForDisplay(displayCp)
        val nextStart = if (moveStart) minOf(source, current.sourceEnd - 1) else current.sourceStart
        val nextEnd = if (moveStart) current.sourceEnd else maxOf(source, current.sourceStart + 1)
        val localStart = (nextStart - sourceBase).coerceIn(0, map.sourceCodePoints)
        val localEnd = (nextEnd - sourceBase).coerceIn(localStart, map.sourceCodePoints)
        val displayStart = utf16ForCodePoints(displayText, map.displayForSource(localStart))
        val displayEnd = utf16ForCodePoints(displayText, map.displayForSource(localEnd))
        val excerpt = if (displayEnd > displayStart) displayText.substring(displayStart, displayEnd)
            .replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), "").trim().take(MAX_EXCERPT) else current.excerpt
        return ReaderSelectionRange(nextStart.coerceAtLeast(0), nextEnd.coerceAtLeast(nextStart + 1), excerpt)
    }

    /** Two-stage selection keeps a stable anchor while another page/window is opened. */
    fun extendAcrossBoundary(current: ReaderSelectionRange, newBoundary: Long, towardPrevious: Boolean): ReaderSelectionRange =
        if (towardPrevious) current.copy(sourceStart = newBoundary.coerceIn(0, current.sourceStart))
        else current.copy(sourceEnd = newBoundary.coerceAtLeast(current.sourceEnd))

    private fun utf16ForCodePoints(text: String, count: Long): Int {
        if (text.isEmpty() || count <= 0) return 0
        val total = text.codePointCount(0, text.length)
        return text.offsetByCodePoints(0, count.coerceIn(0, total.toLong()).toInt())
    }

    private const val MAX_EXCERPT = 800
}
