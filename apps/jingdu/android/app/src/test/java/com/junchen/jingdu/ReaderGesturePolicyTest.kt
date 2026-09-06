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
}
