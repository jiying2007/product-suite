package com.junchen.jingdu

import kotlin.math.abs

data class SmartChapter(
    val offset: Long,
    val title: String,
    val source: String = "core",
    val confidence: Int = 100,
)

data class TocQualityReport(
    val chapters: List<SmartChapter>,
    val score: Int,
    val duplicateTitles: Int,
    val numericGaps: Int,
    val suspiciousTitles: Int,
) {
    val anomalyCount: Int get() = duplicateTitles + numericGaps + suspiciousTitles
}

/**
 * Candidate-only TOC intelligence layered on top of Core offsets. The Core remains authoritative
 * for offsets; this layer augments sparse TOCs with special Chinese headings by exact native search
 * and evaluates structure quality without whole-document managed copies or arbitrary regex.
 */
internal object SmartToc {
    private const val MIN_CORE_CHAPTERS_FOR_COMPLETE_TOC = 20

    private val specialHeadings = listOf(
        "序章", "楔子", "引子", "前言", "序言", "后记", "後記", "尾声", "尾聲",
        "大结局", "大結局", "终章", "終章", "番外", "番外篇",
    )

    fun analyze(reader: ReaderController): TocQualityReport {
        val merged = linkedMapOf<Long, SmartChapter>()
        reader.chapters().forEach { chapter ->
            merged[chapter.offset] = SmartChapter(chapter.offset, chapter.title.trim(), "core", 100)
        }

        // Native Core already provides authoritative chapter offsets. Exact full-document searches
        // are only useful when that TOC is sparse; on normal novels they add repeated O(document)
        // work with no user-visible benefit.
        if (merged.size < MIN_CORE_CHAPTERS_FOR_COMPLETE_TOC) {
            specialHeadings.forEach { marker ->
                reader.search(marker).take(80).forEach { hit ->
                    val verified = verifyLineHeading(reader, hit.offset, marker) ?: return@forEach
                    merged.putIfAbsent(hit.offset, SmartChapter(hit.offset, verified, "special", 92))
                }
            }
        }

        return evaluate(merged.values)
    }

    fun evaluate(values: Collection<SmartChapter>): TocQualityReport {
        val chapters = values.distinctBy(SmartChapter::offset).sortedBy(SmartChapter::offset)
        val duplicateTitles = chapters.groupingBy { normalizeTitle(it.title) }.eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }
        val numeric = chapters.mapIndexedNotNull { index, chapter -> parseOrdinal(chapter.title)?.let { index to it } }
        var numericGaps = 0
        for (index in 1 until numeric.size) {
            val previous = numeric[index - 1]
            val current = numeric[index]
            if (current.first - previous.first > 3) continue
            val delta = current.second - previous.second
            if (delta > 1 && delta <= 20) numericGaps += delta - 1
        }
        val suspiciousTitles = chapters.count { chapter ->
            val length = chapter.title.codePointCount(0, chapter.title.length)
            length > 48 || chapter.title.count { it == '。' || it == '，' || it == ',' } >= 2
        }
        val base = when {
            chapters.size >= 20 -> 100
            chapters.size >= 5 -> 90
            chapters.isNotEmpty() -> 75
            else -> 45
        }
        val score = (base - duplicateTitles * 4 - numericGaps.coerceAtMost(10) * 2 - suspiciousTitles * 5).coerceIn(0, 100)
        return TocQualityReport(chapters, score, duplicateTitles, numericGaps, suspiciousTitles)
    }

    private fun verifyLineHeading(reader: ReaderController, offset: Long, marker: String): String? {
        val from = (offset - 4).coerceAtLeast(0)
        val raw = reader.readAt(from, 120)
        val markerIndex = raw.indexOf(marker)
        if (markerIndex < 0) return null
        val prefix = raw.substring(0, markerIndex)
        val lastBreak = maxOf(prefix.lastIndexOf('\n'), prefix.lastIndexOf('\r'))
        val before = prefix.substring(lastBreak + 1)
        if (before.any { !it.isWhitespace() && it !in "【[（(" }) return null
        val endCandidates = listOf(raw.indexOf('\n', markerIndex), raw.indexOf('\r', markerIndex)).filter { it >= 0 }
        val end = endCandidates.minOrNull() ?: raw.length
        val line = raw.substring(lastBreak + 1, end).trim().trim('【', '】', '[', ']', '（', '）', '(', ')')
        val length = line.codePointCount(0, line.length)
        if (length !in 2..60) return null
        if (!line.contains(marker)) return null
        return line
    }

    private fun normalizeTitle(value: String): String = value
        .trim()
        .replace(" ", "")
        .replace("\t", "")
        .lowercase()

    private fun parseOrdinal(title: String): Int? {
        val trimmed = title.trim()
        val chapterIndex = trimmed.indexOf('第')
        if (chapterIndex >= 0) {
            val suffixes = listOf('章', '回', '节', '節', '卷')
            val suffixIndex = suffixes.map { trimmed.indexOf(it, chapterIndex + 1) }.filter { it > chapterIndex }.minOrNull()
            if (suffixIndex != null) {
                val token = trimmed.substring(chapterIndex + 1, suffixIndex).trim()
                token.toIntOrNull()?.let { return it }
                chineseNumber(token)?.let { return it }
            }
        }
        val lower = trimmed.lowercase()
        if (lower.startsWith("chapter")) {
            lower.removePrefix("chapter").trim().takeWhile(Char::isDigit).toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun chineseNumber(raw: String): Int? {
        if (raw.isEmpty() || raw.length > 8) return null
        val digit = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '兩' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        val unit = mapOf('十' to 10, '百' to 100, '千' to 1000, '万' to 10000, '萬' to 10000)
        if (raw.all { it in digit }) return raw.fold(0) { acc, c -> acc * 10 + (digit[c] ?: 0) }
        var total = 0
        var current = 0
        var section = 0
        for (char in raw) {
            val d = digit[char]
            if (d != null) {
                current = d
                continue
            }
            val u = unit[char] ?: return null
            if (u == 10000) {
                section = (section + current).coerceAtLeast(1) * u
                total += section
                section = 0
                current = 0
            } else {
                section += (if (current == 0) 1 else current) * u
                current = 0
            }
        }
        val result = total + section + current
        return result.takeIf { it > 0 && abs(it) <= 1_000_000 }
    }
}
