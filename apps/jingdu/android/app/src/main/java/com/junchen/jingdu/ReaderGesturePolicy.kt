package com.junchen.jingdu

import kotlin.math.abs

/**
 * Pure gesture arbitration shared by the pointer runtime and regression tests. Interaction
 * correctness owns this boundary: one Reader pointer owner observes the complete stream, including
 * multi-touch pinch, while SelectionContainer may still consume slow selection drags. A parallel
 * transform detector is intentionally forbidden because it can steal real single-finger taps.
 * Reader chrome follows the same rule: visual placement, hit testing and accessibility must share
 * one authoritative visibility state. Hosted journeys therefore inject real input and accept only
 * visibly on-screen Reader chrome as proof. Performance optimizations must never make paging/taps unreachable.
 */
internal object ReaderGesturePolicy {
    private const val DOUBLE_TAP_MIN_MS = 40L
    private const val DOUBLE_TAP_MAX_MS = 320L

    /**
     * A paged Reader owns an intentional horizontal drag until native text selection is explicitly
     * active. Text/layout nodes can report Final-pass consumption for ordinary drags, so neither
     * consumption nor swipe duration is allowed to make horizontal paging unreachable.
     */
    @Suppress("UNUSED_PARAMETER")
    fun allowsPageSwipe(
        consumedByChild: Boolean,
        selectionActive: Boolean,
        durationMs: Long,
        deltaX: Float,
        deltaY: Float,
        thresholdPx: Float,
    ): Boolean {
        if (selectionActive) return false
        return abs(deltaX) >= thresholdPx && abs(deltaX) > abs(deltaY) * 1.20f
    }

    fun isDoubleTap(previousTapAt: Long, tapAt: Long): Boolean =
        previousTapAt > 0L && tapAt - previousTapAt in DOUBLE_TAP_MIN_MS..DOUBLE_TAP_MAX_MS
}
