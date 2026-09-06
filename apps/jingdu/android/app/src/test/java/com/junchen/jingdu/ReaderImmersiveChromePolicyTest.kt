package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImmersiveChromePolicyTest {
    private val chapters = listOf(
        ChapterModel(0, "One"),
        ChapterModel(100, "Two"),
        ChapterModel(250, "Three"),
        ChapterModel(900, "Four"),
    )

    @Test fun activeChapterLookupFindsFloorOffsetWithBinarySearch() {
        assertEquals(-1, readerFindActiveChapterIndex(chapters, -1))
        assertEquals(0, readerFindActiveChapterIndex(chapters, 0))
        assertEquals(0, readerFindActiveChapterIndex(chapters, 99))
        assertEquals(1, readerFindActiveChapterIndex(chapters, 100))
        assertEquals(2, readerFindActiveChapterIndex(chapters, 899))
        assertEquals(3, readerFindActiveChapterIndex(chapters, 5_000))
    }

    @Test fun adaptiveSingleColumnWidthNeverExceedsWindowCap() {
        assertEquals(338.4f, readerAdaptiveTextWidthDp(18f, 360f), 0.1f)
        assertTrue(readerAdaptiveTextWidthDp(18f, 1_200f) <= 984f)
        assertTrue(readerAdaptiveTextWidthDp(40f, 1_200f) <= 760f)
    }

    @Test fun adaptiveTwoColumnWidthRespectsLargeScreenWindow() {
        assertEquals(768f, readerAdaptiveTwoColumnWidthDp(20f, 800f), 0.1f)
        assertTrue(readerAdaptiveTwoColumnWidthDp(20f, 1_400f) <= 1_200f)
    }

    @Test fun passiveProgressUsesStableTenthPercentBuckets() {
        assertEquals(0.123f, readerPassiveProgressFraction(0.1234f), 0.0001f)
        assertEquals(0.124f, readerPassiveProgressFraction(0.1236f), 0.0001f)
        assertEquals(0f, readerPassiveProgressFraction(-1f), 0f)
        assertEquals(1f, readerPassiveProgressFraction(2f), 0f)
    }
}
