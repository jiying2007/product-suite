package com.junchen.jingdu

import kotlin.math.roundToLong

enum class ReaderMotionState { IDLE, AUTO_SCROLL, AUTO_PAGE, TTS }

/**
 * Single authority for mutually-exclusive reader motion. Runtime motion is deliberately not
 * persisted: reopening a reader always starts idle and requires explicit user intent.
 */
internal class ReaderMotionController {
    var state: ReaderMotionState = ReaderMotionState.IDLE
        private set

    fun start(target: ReaderMotionState): ReaderMotionState {
        state = target
        return state
    }

    fun stop(expected: ReaderMotionState? = null): ReaderMotionState {
        if (expected == null || state == expected) state = ReaderMotionState.IDLE
        return state
    }

    fun isActive(target: ReaderMotionState): Boolean = state == target

    fun adaptivePageDelayMs(
        visibleSourceChars: Long,
        charsPerMinute: Double,
        settings: ReaderSettings,
    ): Long {
        if (settings.autoPageMode == ReaderAutoPageMode.FIXED) return settings.autoPageDelayMs
        val safeChars = visibleSourceChars.coerceAtLeast(ReaderController.MIN_PAGE_CHARS)
        val safeCpm = charsPerMinute.coerceIn(120.0, 1800.0)
        val base = safeChars.toDouble() * 60_000.0 / safeCpm
        // A literal whole-page reading-time estimate can easily exceed one or two minutes for a
        // dense phone page. That made Auto Page look broken even though its timer was technically
        // running. Adaptive mode is a hands-free *page cadence*, so keep the learned pace signal but
        // bound it to a range where the next action is perceptible and still comfortable.
        return (base / settings.autoPagePaceMultiplier.coerceIn(0.5f, 2f))
            .roundToLong()
            .coerceIn(MIN_ADAPTIVE_PAGE_DELAY_MS, MAX_ADAPTIVE_PAGE_DELAY_MS)
    }

    companion object {
        const val MIN_ADAPTIVE_PAGE_DELAY_MS = 3_000L
        const val MAX_ADAPTIVE_PAGE_DELAY_MS = 18_000L
    }
}
