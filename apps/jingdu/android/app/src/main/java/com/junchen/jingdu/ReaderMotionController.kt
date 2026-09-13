package com.junchen.jingdu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToLong

enum class ReaderMotionState { IDLE, AUTO_SCROLL, AUTO_PAGE, TTS }

/** Non-Compose process-local motion state used by hot-path correctness logic. */
internal object ReaderMotionRuntime {
    @Volatile var state: ReaderMotionState = ReaderMotionState.IDLE
        private set

    fun publish(next: ReaderMotionState) {
        state = next
    }
}

/** Observable projection used by Reader chrome to label the active automatic motion. */
internal object ReaderMotionUiRuntime {
    var readingMode by mutableStateOf(ReaderMode.PAGED)
        private set
    var motion by mutableStateOf(ReaderMotionState.IDLE)
        private set

    fun publish(mode: ReaderMode, nextMotion: ReaderMotionState) {
        if (readingMode != mode) readingMode = mode
        if (motion != nextMotion) motion = nextMotion
    }
}

/**
 * Pace learning is intentionally narrower than session tracking. Only idle forward movement within
 * one source window is a trustworthy human-reading sample. Automatic motion, TTS, reverse
 * navigation and larger seek/search jumps must not train CPM.
 */
internal fun readerShouldLearnPace(
    motion: ReaderMotionState,
    sourceDelta: Long,
): Boolean = motion == ReaderMotionState.IDLE &&
    sourceDelta in 64L..ReaderController.WINDOW_CHARS

/**
 * Single authority for mutually-exclusive reader motion. Runtime motion is deliberately not
 * persisted: reopening a reader always starts idle and requires explicit user intent.
 */
internal class ReaderMotionController {
    var state: ReaderMotionState = ReaderMotionState.IDLE
        private set

    fun start(target: ReaderMotionState): ReaderMotionState {
        state = target
        ReaderMotionRuntime.publish(state)
        return state
    }

    fun stop(expected: ReaderMotionState? = null): ReaderMotionState {
        if (expected == null || state == expected) state = ReaderMotionState.IDLE
        ReaderMotionRuntime.publish(state)
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
