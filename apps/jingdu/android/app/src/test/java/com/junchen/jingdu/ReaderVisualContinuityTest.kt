package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderVisualContinuityTest {
    @Test fun reflowKeepsOldVisualCenterNearNewVisualCenter() {
        val oldTop = 10_000L
        val oldVisible = 1_200L
        val newVisible = 800L
        val anchor = ReaderVisualContinuity.centerAnchor(oldTop, oldVisible, 100_000)
        val newTop = ReaderVisualContinuity.topForCenter(anchor, newVisible, 100_000)
        assertEquals(anchor, ReaderVisualContinuity.centerAnchor(newTop, newVisible, 100_000))
    }

    @Test fun centerAndTopClampAtDocumentEdges() {
        assertEquals(99L, ReaderVisualContinuity.centerAnchor(90, 100, 100))
        assertEquals(0L, ReaderVisualContinuity.topForCenter(10, 100, 100))
    }

    @Test fun layoutKeyChangesForReaderGeometryButNotBrightness() {
        val base = ReaderSettings()
        assertNotEquals(ReaderVisualContinuity.layoutKey(base), ReaderVisualContinuity.layoutKey(base.copy(fontSizeSp = 24f)))
        assertEquals(ReaderVisualContinuity.layoutKey(base), ReaderVisualContinuity.layoutKey(base.copy(readerBrightness = 0.2f)))
    }
}
