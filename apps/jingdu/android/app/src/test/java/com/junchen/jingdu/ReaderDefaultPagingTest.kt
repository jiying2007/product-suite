package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderDefaultPagingTest {
    @Test
    fun newReaderProfilesPreferInstantPageTurns() {
        assertEquals(ReaderPageAnimation.NONE, ReaderSettings().pageAnimation)
    }

    @Test
    fun explicitSlidePreferenceRemainsAvailable() {
        assertEquals(
            ReaderPageAnimation.SLIDE,
            ReaderSettings(pageAnimation = ReaderPageAnimation.SLIDE).pageAnimation,
        )
    }
}
