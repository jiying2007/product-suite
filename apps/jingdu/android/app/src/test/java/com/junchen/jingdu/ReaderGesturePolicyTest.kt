package com.junchen.jingdu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGesturePolicyTest {
    @Test fun fastHorizontalSwipeCanPassSelectionConsumption() {
        assertTrue(ReaderGesturePolicy.allowsPageSwipe(true, false, 180, -120f, 12f, 52f))
        assertTrue(ReaderGesturePolicy.allowsPageSwipe(true, false, 700, -120f, 12f, 52f))
        assertTrue(ReaderGesturePolicy.allowsPageSwipe(true, false, 2_000, -120f, 12f, 52f))
        assertFalse(ReaderGesturePolicy.allowsPageSwipe(true, false, 180, -60f, 52f, 52f))
    }

    @Test fun activeTextSelectionOwnsHorizontalDrag() {
        assertFalse(ReaderGesturePolicy.allowsPageSwipe(false, true, 180, -120f, 12f, 52f))
        assertFalse(ReaderGesturePolicy.allowsPageSwipe(true, true, 2_000, -120f, 12f, 52f))
    }

    @Test fun unconsumedHorizontalSwipeKeepsNormalThreshold() {
        assertTrue(ReaderGesturePolicy.allowsPageSwipe(false, false, 900, 70f, 8f, 52f))
        assertFalse(ReaderGesturePolicy.allowsPageSwipe(false, false, 120, 40f, 2f, 52f))
    }

    @Test fun doubleTapWindowRejectsAccidentalSpacing() {
        assertTrue(ReaderGesturePolicy.isDoubleTap(1_000, 1_180))
        assertFalse(ReaderGesturePolicy.isDoubleTap(1_000, 1_360))
        assertFalse(ReaderGesturePolicy.isDoubleTap(0, 180))
    }

    @Test fun continuousScrollSinkDetachesByViewportOwner() {
        val model = ReaderContinuousScrollModel()
        val firstOwner = Any()
        val secondOwner = Any()
        var firstUpdates = 0
        var secondUpdates = 0

        model.attachScrollSink(firstOwner) { firstUpdates++ }
        assertTrue(model.hasScrollSinkFor(firstOwner))

        model.attachScrollSink(secondOwner) { secondUpdates++ }
        assertFalse(model.hasScrollSinkFor(firstOwner))
        assertTrue(model.hasScrollSinkFor(secondOwner))

        model.setRangeAndOffset(100, 50f)
        assertTrue(secondUpdates >= 2)
        val beforeDetach = secondUpdates
        model.detachScrollSink(secondOwner)
        assertFalse(model.hasScrollSinkFor(secondOwner))
        model.setOffset(75f)
        assertTrue(secondUpdates == beforeDetach)
        assertTrue(firstUpdates == 1)
    }

    @Test fun continuousTilePrefetchStaysViewportBounded() {
        assertTrue(readerContinuousTilePrefetchIndices(0, 1_000, 1_000, 12) == listOf(0, 1))
        assertTrue(readerContinuousTilePrefetchIndices(4_500, 1_200, 1_000, 12) == listOf(3, 4, 5, 6))

        val tallViewport = readerContinuousTilePrefetchIndices(
            scrollY = 6_000,
            viewportHeight = 3_200,
            tileHeight = 1_536,
            tileCount = 40,
        )
        assertTrue(tallViewport.size <= 5)
        assertTrue(tallViewport == tallViewport.distinct())
    }
}
