package com.junchen.jingdu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAdaptiveLayoutTest {
    @Test fun verticalHingePrefersBookLikeTwoColumnReading() {
        val layout = ReaderAdaptiveLayout(ReaderAdaptiveWidth.MEDIUM, hasHinge = true, tabletop = false)
        assertTrue(layout.bookPosture)
        assertTrue(layout.prefersTwoColumns)
        assertFalse(layout.prefersSideControls)
    }

    @Test fun tabletopDoesNotForceTwoPageBookPosture() {
        val layout = ReaderAdaptiveLayout(ReaderAdaptiveWidth.MEDIUM, hasHinge = true, tabletop = true)
        assertFalse(layout.bookPosture)
        assertFalse(layout.prefersTwoColumns)
    }

    @Test fun ordinaryExpandedWindowStillUsesTwoColumns() {
        assertTrue(ReaderAdaptiveLayout(ReaderAdaptiveWidth.EXPANDED, false, false).prefersTwoColumns)
    }
}
