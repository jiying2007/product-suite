package com.junchen.jingdu

internal data class ReaderContentInsetsDp(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Stable Reader content safe-area policy. Chrome may overlay transiently, but book text itself
 * must never live under a display cutout/home gesture or the persistent hidden reading-status pill.
 */
internal fun readerContentInsetsDp(
    cutoutLeftDp: Float,
    cutoutTopDp: Float,
    cutoutRightDp: Float,
    bottomGestureDp: Float,
    showReadingStatus: Boolean,
    fontScale: Float = 1f,
): ReaderContentInsetsDp {
    val gutter = 8f
    return ReaderContentInsetsDp(
        left = cutoutLeftDp.coerceAtLeast(0f) + gutter,
        top = cutoutTopDp.coerceAtLeast(0f) + gutter,
        right = cutoutRightDp.coerceAtLeast(0f) + gutter,
        bottom = bottomGestureDp.coerceAtLeast(0f) + gutter +
            if (showReadingStatus) {
                READER_READING_STATUS_BASE_RESERVE_DP * fontScale.coerceIn(1f, 2.5f)
            } else 0f,
    )
}

internal const val READER_READING_STATUS_BASE_RESERVE_DP = 34f
