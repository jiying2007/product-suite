package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            assertEquals(target, ReaderMotionRuntime.state)
        }
        controller.stop()
        assertEquals(ReaderMotionState.IDLE, controller.state)
        assertEquals(ReaderMotionState.IDLE, ReaderMotionRuntime.state)
    }

    @Test fun automaticChromeProjectionTracksModeAndMotion() {
        try {
            ReaderMotionUiRuntime.publish(ReaderMode.CONTINUOUS, ReaderMotionState.AUTO_SCROLL)
            assertEquals(ReaderMode.CONTINUOUS, ReaderMotionUiRuntime.readingMode)
            assertEquals(ReaderMotionState.AUTO_SCROLL, ReaderMotionUiRuntime.motion)
            ReaderMotionUiRuntime.publish(ReaderMode.PAGED, ReaderMotionState.AUTO_PAGE)
            assertEquals(ReaderMode.PAGED, ReaderMotionUiRuntime.readingMode)
            assertEquals(ReaderMotionState.AUTO_PAGE, ReaderMotionUiRuntime.motion)
        } finally {
            ReaderMotionUiRuntime.publish(ReaderMode.PAGED, ReaderMotionState.IDLE)
        }
    }

    @Test fun paceLearningAcceptsOnlyIdlePageSizedForwardMovement() {
        assertTrue(readerShouldLearnPace(ReaderMotionState.IDLE, 64L))
        assertTrue(readerShouldLearnPace(ReaderMotionState.IDLE, 800L))
        assertTrue(readerShouldLearnPace(ReaderMotionState.IDLE, ReaderController.WINDOW_CHARS))
        assertFalse(readerShouldLearnPace(ReaderMotionState.IDLE, 63L))
        assertFalse(readerShouldLearnPace(ReaderMotionState.IDLE, -800L))
        assertFalse(readerShouldLearnPace(ReaderMotionState.IDLE, ReaderController.WINDOW_CHARS + 1L))
        assertFalse(readerShouldLearnPace(ReaderMotionState.AUTO_PAGE, 800L))
        assertFalse(readerShouldLearnPace(ReaderMotionState.AUTO_SCROLL, 800L))
        assertFalse(readerShouldLearnPace(ReaderMotionState.TTS, 800L))
    }

    @Test fun adaptiveAutoPageRemainsResponsiveAndBounded() {
        val controller = ReaderMotionController()
        val settings = ReaderSettings(autoPageMode = ReaderAutoPageMode.ADAPTIVE, autoPagePaceMultiplier = 1f)
        val fast = controller.adaptivePageDelayMs(300, 1_800.0, settings)
        val dense = controller.adaptivePageDelayMs(1_500, 500.0, settings)
        val slow = controller.adaptivePageDelayMs(5_000, 120.0, settings)
        assertTrue(fast >= ReaderMotionController.MIN_ADAPTIVE_PAGE_DELAY_MS)
        assertTrue(dense <= ReaderMotionController.MAX_ADAPTIVE_PAGE_DELAY_MS)
        assertTrue(slow <= ReaderMotionController.MAX_ADAPTIVE_PAGE_DELAY_MS)
        assertTrue(slow >= fast)
    }

    @Test fun adaptivePaceMultiplierStillChangesCadenceWithinHumanScaleBounds() {
        val controller = ReaderMotionController()
        // 300 chars at 1,800 CPM is a 10s base cadence: 0.5x reaches the 18s comfort cap while
        // 2x remains 5s, so this validates multiplier ordering instead of comparing two clamped
        // values at the same upper bound.
        val slower = controller.adaptivePageDelayMs(
            300,
            1_800.0,
            ReaderSettings(autoPageMode = ReaderAutoPageMode.ADAPTIVE, autoPagePaceMultiplier = 0.5f),
        )
        val faster = controller.adaptivePageDelayMs(
            300,
            1_800.0,
            ReaderSettings(autoPageMode = ReaderAutoPageMode.ADAPTIVE, autoPagePaceMultiplier = 2f),
        )
        assertTrue(faster < slower)
        assertEquals(5_000L, faster)
        assertEquals(ReaderMotionController.MAX_ADAPTIVE_PAGE_DELAY_MS, slower)
    }

    @Test fun fixedAutoPageUsesExplicitInterval() {
        val controller = ReaderMotionController()
        val settings = ReaderSettings(autoPageMode = ReaderAutoPageMode.FIXED, autoPageDelayMs = 42_000L)
        assertEquals(42_000L, controller.adaptivePageDelayMs(900, 600.0, settings))
    }
}
