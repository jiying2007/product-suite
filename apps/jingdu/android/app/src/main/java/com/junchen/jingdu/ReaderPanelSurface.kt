package com.junchen.jingdu

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * In-tree reader panel shell kept in the same composition tree as Reader. The persistent parent
 * owns the cached flat scrim; this shell keeps outside-tap dismissal and the live panel surface so
 * interactive content is never captured into a stale display list.
 */
@Composable
internal fun ReaderPanelSurface(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                // The scrim is a visual/pointer affordance, not a giant unlabeled accessibility
                // button. Explicit close controls and Back remain the semantic dismiss routes.
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .clearAndSetSemantics { },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Make the sheet itself a pointer hit target. Without this shield, taps on blank
                // padding can fall through to the full-screen dismiss sibling behind the sheet.
                // Child controls still receive the same pointer stream and keep normal gestures.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            pressed = event.changes.firstOrNull { it.id == down.id }?.pressed == true
                        }
                    }
                }
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                )
                .navigationBarsPadding()
                .padding(top = 8.dp),
        ) { content() }
    }
}
