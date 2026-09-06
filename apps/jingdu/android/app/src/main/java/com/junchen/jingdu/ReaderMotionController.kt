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
        return (base / settings.autoPagePaceMultiplier.coerceIn(0.5f, 2f))
            .roundToLong()
            .coerceIn(2_500L, 120_000L)
    }
}
