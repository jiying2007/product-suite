package com.junchen.jingdu

import androidx.compose.runtime.withFrameNanos as composeWithFrameNanos

/**
 * Same-package frame helper whose callback is suspend-aware.
 *
 * Compose's stock withFrameNanos callback is non-suspending. The continuous reader needs to
 * apply ScrollState.scrollBy after each frame; keeping the callback suspend-aware avoids raw
 * delta mutation while still preserving frame-paced auto-scroll.
 */
internal suspend fun withFrameNanos(onFrame: suspend (Long) -> Unit) {
    val frameTime = composeWithFrameNanos { it }
    onFrame(frameTime)
}
