package com.junchen.jingdu

/**
 * Precision-first, display-only paragraph repair for legacy TXT hard wraps.
 *
 * The source text remains authoritative. This helper only removes a newline when the bounded window
 * has strong evidence of fixed-width hard wrapping; ordinary paragraph-per-line files, headings,
 * indentation, dialogue turns and structural list/separator lines stay untouched.
 * ReaderPresentationPipeline builds the exact monotonic source projection after the transformation.
 */
internal object SmartLayout {
    data class Result(
        val text: String,
        val hardWrapDetected: Boolean,
        val joinedBreaks: Int,
    )

    fun present(source: String): Result {
        if (source.isBlank() || ('\n' !in source && '\r' !in source)) return Result(source, false, 0)
        // Normal well-formed TXT is the hot path. Inspect only a bounded prefix of lines first and
        // avoid allocating/splitting the whole Reader window unless hard-wrap evidence is strong.
        val sample = source.lineSequence().take(MAX_ANALYSIS_LINES).toList()
        if (!looksHardWrapped(sample)) return Result(source, false, 0)

        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val output = StringBuilder(normalized.length)
        var joined = 0
        var currentStructural = lines.firstOrNull()?.let(::isStructuralLine) ?: false
        for (index in lines.indices) {
            val current = lines[index]
            output.append(current)
            if (index == lines.lastIndex) continue
            val next = lines[index + 1]
            val nextStructural = isStructuralLine(next)
            if (!currentStructural && !nextStructural && canJoinInternal(current, next)) {
                if (needsLatinSpace(current, next)) output.append(' ')
                joined++
            } else {
                output.append('\n')
            }
            currentStructural = nextStructural
        }
        return Result(output.toString(), joined > 0, joined)
    }

    /**
     * Keep the normal page-turn evidence path equivalent to the pre-structure-protection path.
     * Structural protection is only needed after hard-wrap evidence is already strong, when we are
     * actually deciding which newlines to remove. That keeps ordinary Reader paging from paying for
     * list/separator classification while still preserving those lines in a detected hard-wrap TXT.
     */
    private fun looksHardWrapped(lines: List<String>): Boolean {
        val contentLengths = lines.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !ReaderHeadingClassifier.isHeading(it) }
            .map { it.codePointCount(0, it.length) }
            .filter { it in MIN_LINE_CP..MAX_LINE_CP }
            .toList()
        if (contentLengths.size < MIN_CONTENT_LINES) return false

        var plausible = 0
        var joinable = 0
        for (index in 0 until lines.lastIndex) {
            val a = lines[index].trim()
            val b = lines[index + 1].trim()
            if (a.isEmpty() || b.isEmpty()) continue
            if (a.codePointCount(0, a.length) !in MIN_LINE_CP..MAX_LINE_CP ||
                b.codePointCount(0, b.length) !in MIN_NEXT_CP..MAX_LINE_CP) continue
            if (ReaderHeadingClassifier.isHeading(a) || ReaderHeadingClassifier.isHeading(b)) continue
            plausible++
            if (canJoinInternal(lines[index], lines[index + 1])) joinable++
        }
        if (joinable < MIN_JOINABLE_BREAKS || plausible <= 0) return false

        val sorted = contentLengths.sorted()
        val median = sorted[sorted.size / 2]
        val nearMedian = contentLengths.count { kotlin.math.abs(it - median) <= MAX_WRAP_VARIANCE_CP }
        val consistentWidth = median in MIN_MEDIAN_CP..MAX_MEDIAN_CP && nearMedian * 2 >= contentLengths.size
        return consistentWidth && joinable.toDouble() / plausible.toDouble() >= MIN_JOINABLE_RATIO
    }

    /**
     * Main-equivalent newline predicate. Structural classification is deliberately outside this
     * predicate and cached once per line during the final hard-wrap pass, avoiding duplicate work
     * for every adjacent pair while preserving the established evidence semantics exactly.
     */
    private fun canJoinInternal(rawCurrent: String, rawNext: String): Boolean {
        if (rawCurrent.isBlank() || rawNext.isBlank()) return false
        if (hasParagraphIndent(rawNext)) return false
        val current = rawCurrent.trimEnd()
        val next = rawNext.trimStart()
        if (current.isEmpty() || next.isEmpty()) return false
        if (ReaderHeadingClassifier.isHeading(current.trim()) || ReaderHeadingClassifier.isHeading(next.trim())) return false
        val currentCp = current.codePointCount(0, current.length)
        val nextCp = next.codePointCount(0, next.length)
        if (currentCp !in MIN_LINE_CP..MAX_LINE_CP || nextCp !in MIN_NEXT_CP..MAX_LINE_CP) return false
        if (endsParagraph(current) || startsFreshBlockForEvidence(next)) return false
        return true
    }

    private fun hasParagraphIndent(value: String): Boolean =
        value.startsWith('\t') || value.startsWith("\u3000") || value.startsWith("  ")

    private fun endsParagraph(value: String): Boolean {
        val last = value.lastOrNull() ?: return true
        return last in TERMINAL_PUNCTUATION
    }

    /** Keep hard-wrap evidence semantics identical to the established hot path. */
    private fun startsFreshBlockForEvidence(value: String): Boolean {
        val first = value.firstOrNull() ?: return true
        return first in BLOCK_OPENERS || value.startsWith("——") || value.startsWith("***") || value.startsWith("###")
    }

    /**
     * Protect structure that frequently appears inside downloaded Chinese web-novel TXT. This runs
     * only after the window has already been identified as hard-wrapped.
     */
    private fun isStructuralLine(value: String): Boolean {
        if (value.isEmpty()) return true
        var start = 0
        var end = value.length
        while (start < end && value[start].isWhitespace()) start++
        if (start >= end) return true
        while (end > start && value[end - 1].isWhitespace()) end--

        val first = value[start]
        if (first !in STRUCTURAL_PREFIXES) return false
        if (first in LIST_MARKERS || first == '—') return true
        if (startsRepeatedSeparator(value, start, end)) return true
        if (startsNumberedList(value, start, end)) return true
        return looksLikeSceneSeparator(value, start, end)
    }

    private fun startsRepeatedSeparator(value: String, start: Int, end: Int): Boolean {
        val remaining = end - start
        if (remaining < 2) return false
        val first = value[start]
        if (first == '—' && value[start + 1] == '—') return true
        if (remaining < 3) return false
        return when (first) {
            '-', '*', '#', '~', '～' -> value[start + 1] == first && value[start + 2] == first
            else -> false
        }
    }

    private fun startsNumberedList(value: String, start: Int, end: Int): Boolean {
        var cursor = start
        var digits = 0
        while (cursor < end && digits < 3 && value[cursor].isDigit()) {
            cursor++
            digits++
        }
        if (digits > 0 && cursor < end && value[cursor] in LIST_SUFFIXES) return true

        if (end - start >= 3 && value[start] in "（(" && value[start + 1].isDigit()) {
            var close = start + 2
            while (close < end && close <= start + 4) {
                if (value[close] in "）)") return true
                close++
            }
        }

        cursor = start
        var chineseDigits = 0
        while (cursor < end && chineseDigits < 4 && value[cursor] in CHINESE_NUMERALS) {
            cursor++
            chineseDigits++
        }
        return chineseDigits > 0 && cursor < end && value[cursor] in LIST_SUFFIXES
    }

    private fun looksLikeSceneSeparator(value: String, start: Int, end: Int): Boolean {
        val length = end - start
        if (length !in 3..24) return false
        var meaningful = 0
        for (index in start until end) {
            val char = value[index]
            if (char.isWhitespace()) continue
            if (char !in SCENE_SEPARATOR_MARKERS) return false
            meaningful++
        }
        return meaningful >= 3
    }

    private fun needsLatinSpace(current: String, next: String): Boolean {
        val a = current.lastOrNull() ?: return false
        val b = next.firstOrNull() ?: return false
        return a.isLetterOrDigit() && b.isLetterOrDigit() && a.code < 128 && b.code < 128
    }

    private const val MAX_ANALYSIS_LINES = 80
    private const val MIN_CONTENT_LINES = 5
    private const val MIN_JOINABLE_BREAKS = 4
    private const val MIN_LINE_CP = 10
    private const val MIN_NEXT_CP = 6
    private const val MAX_LINE_CP = 120
    private const val MIN_MEDIAN_CP = 18
    private const val MAX_MEDIAN_CP = 90
    private const val MAX_WRAP_VARIANCE_CP = 12
    private const val MIN_JOINABLE_RATIO = 0.55
    private const val TERMINAL_PUNCTUATION = ".。！？!?；;…：:。！？!?；;\"'”’」』》）)]】"
    private const val BLOCK_OPENERS = "“‘「『《（(【["
    private const val LIST_MARKERS = "•·※◆◇○●☆★▪▫►▶"
    private const val LIST_SUFFIXES = ".、)）"
    private const val CHINESE_NUMERALS = "零〇一二三四五六七八九十百"
    private const val SCENE_SEPARATOR_MARKERS = "*-—_=~～·※◆◇○●☆★"
    private const val STRUCTURAL_PREFIXES = LIST_MARKERS + "—-*#~～_=（(" + CHINESE_NUMERALS + "0123456789"
}
