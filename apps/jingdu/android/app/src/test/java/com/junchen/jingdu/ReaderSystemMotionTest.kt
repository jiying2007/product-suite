package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSystemMotionTest {
    @Test fun systemReducedMotionDisablesPageAnimationWithoutChangingSavedChoice() {
        val saved = ReaderPageAnimation.SLIDE
        assertEquals(ReaderPageAnimation.NONE, readerEffectivePageAnimation(saved, systemAnimationsEnabled = false))
        assertEquals(ReaderPageAnimation.SLIDE, readerEffectivePageAnimation(saved, systemAnimationsEnabled = true))
    }
}
