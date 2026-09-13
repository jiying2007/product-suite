package com.junchen.jingdu

import kotlin.math.ceil

internal fun readerRemainingMinutes(position: Long, end: Long, charsPerMinute: Double): Int? {
    if (end <= position || charsPerMinute <= 0.0 || !charsPerMinute.isFinite()) return null
    return ceil((end - position).toDouble() / charsPerMinute.coerceAtLeast(1.0))
        .toInt()
        .coerceAtLeast(1)
}

internal data class ReaderMapAnnotationCounts(
    val bookmarks: Int = 0,
    val highlights: Int = 0,
    val notes: Int = 0,
) {
    fun add(kind: ReaderAnnotationKind): ReaderMapAnnotationCounts = when (kind) {
        ReaderAnnotationKind.BOOKMARK -> copy(bookmarks = bookmarks + 1)
        ReaderAnnotationKind.HIGHLIGHT -> copy(highlights = highlights + 1)
        ReaderAnnotationKind.NOTE -> copy(notes = notes + 1)
    }

    val total: Int get() = bookmarks + highlights + notes
}

/** Aggregate map markers once instead of filtering the full annotation list for every chapter row. */
internal fun readerMapAnnotationCounts(
    chapters: List<ChapterModel>,
    annotations: List<ReaderAnnotation>,
): Map<Long, ReaderMapAnnotationCounts> {
    if (chapters.isEmpty() || annotations.isEmpty()) return emptyMap()
    val offsets = chapters.map(ChapterModel::offset)
    val counts = HashMap<Long, ReaderMapAnnotationCounts>()
    annotations.forEach { annotation ->
        var low = 0
        var high = offsets.lastIndex
        var match = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (offsets[mid] <= annotation.sourceStart) {
                match = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (match >= 0) {
            val key = offsets[match]
            counts[key] = (counts[key] ?: ReaderMapAnnotationCounts()).add(annotation.kind)
        }
    }
    return counts
}
