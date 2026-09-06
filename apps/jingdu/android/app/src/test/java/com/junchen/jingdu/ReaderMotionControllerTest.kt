package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMotionControllerTest {
    @Test fun exclusiveMotionSurvivesLongTransitionSoak() {
        val controller = ReaderMotionController()
        repeat(100_000) { index ->
            val target = when (index % 4) {
                0 -> ReaderMotionState.AUTO_SCROLL
                1 -> ReaderMotionState.AUTO_PAGE
                2 -> ReaderMotionState.TTS
                else -> ReaderMotionState.IDLE
            }
            if (target == ReaderMotionState.IDLE) controller.stop() else controller.start(target)
            assertEquals(target, controller.state)
        }
        controller.stop()
        assertEquals(ReaderMotionState.IDLE, controller.state)
    }

    @Test fun adaptiveAutoPageRemainsBounded() {
        val controller = ReaderMotionController()
        val settings = ReaderSettings(autoPageMode = ReaderAutoPageMode.ADAPTIVE, autoPagePaceMultiplier = 1f)
        val fast = controller.adaptivePageDelayMs(300, 1_800.0, settings)
        val slow = controller.adaptivePageDelayMs(5_000, 120.0, settings)
        assertTrue(fast >= 2_500L)
        assertTrue(slow <= 120_000L)
        assertTrue(slow >= fast)
    }

    @Test fun fixedAutoPageUsesExplicitInterval() {
        val controller = ReaderMotionController()
        val settings = ReaderSettings(autoPageMode = ReaderAutoPageMode.FIXED, autoPageDelayMs = 42_000L)
        assertEquals(42_000L, controller.adaptivePageDelayMs(900, 600.0, settings))
    }
}
