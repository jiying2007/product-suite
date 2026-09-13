package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderReadingMetricsTest {
    @Test fun remainingMinutesUsesFinitePositivePace() {
        assertEquals(10, readerRemainingMinutes(1_000, 6_000, 500.0))
        assertEquals(1, readerRemainingMinutes(999, 1_000, 500.0))
        assertNull(readerRemainingMinutes(1_000, 1_000, 500.0))
        assertNull(readerRemainingMinutes(0, 1_000, 0.0))
        assertNull(readerRemainingMinutes(0, 1_000, Double.NaN))
    }

    @Test fun mapAnnotationCountsAssignEachMarkerToItsOwningChapterOnce() {
        val chapters = listOf(
            ChapterModel(0, "A"),
            ChapterModel(1_000, "B"),
            ChapterModel(2_000, "C"),
        )
        val annotations = listOf(
            ReaderAnnotation(id = "a", bookId = "book", sourceStart = 50, sourceEnd = 50, kind = ReaderAnnotationKind.BOOKMARK),
            ReaderAnnotation(id = "b", bookId = "book", sourceStart = 1_100, sourceEnd = 1_150, kind = ReaderAnnotationKind.HIGHLIGHT),
            ReaderAnnotation(id = "c", bookId = "book", sourceStart = 1_900, sourceEnd = 1_910, kind = ReaderAnnotationKind.NOTE, note = "note"),
            ReaderAnnotation(id = "d", bookId = "book", sourceStart = 2_500, sourceEnd = 2_500, kind = ReaderAnnotationKind.BOOKMARK),
        )

        val counts = readerMapAnnotationCounts(chapters, annotations)
        assertEquals(1, counts[0]?.bookmarks)
        assertEquals(1, counts[1_000]?.highlights)
        assertEquals(1, counts[1_000]?.notes)
        assertEquals(1, counts[2_000]?.bookmarks)
    }
}
