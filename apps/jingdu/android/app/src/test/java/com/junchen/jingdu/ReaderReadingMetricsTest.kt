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
            ReaderAnnotation("a", "book", 50, 50, ReaderAnnotationKind.BOOKMARK, ReaderHighlightStyle.YELLOW, "", "", 0),
            ReaderAnnotation("b", "book", 1_100, 1_150, ReaderAnnotationKind.HIGHLIGHT, ReaderHighlightStyle.YELLOW, "", "", 0),
            ReaderAnnotation("c", "book", 1_900, 1_910, ReaderAnnotationKind.NOTE, ReaderHighlightStyle.YELLOW, "note", "", 0),
            ReaderAnnotation("d", "book", 2_500, 2_500, ReaderAnnotationKind.BOOKMARK, ReaderHighlightStyle.YELLOW, "", "", 0),
        )

        val counts = readerMapAnnotationCounts(chapters, annotations)
        assertEquals(1, counts[0]?.bookmarks)
        assertEquals(1, counts[1_000]?.highlights)
        assertEquals(1, counts[1_000]?.notes)
        assertEquals(1, counts[2_000]?.bookmarks)
    }
}
