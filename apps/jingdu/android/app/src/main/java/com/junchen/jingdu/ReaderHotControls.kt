package com.junchen.jingdu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fixed-cost progress scrubber used on the frame-critical reader bottom bar. Advanced settings keep
 * the normal Material slider overload; only this exact signature resolves here.
 */
@Composable
internal fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ReaderLinearSlider(
        value = value,
        valueRange = 0f..1f,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = { onValueChangeFinished?.invoke() },
    )
}

/**
 * Lightweight reader top-bar geometry without replacing Text with Canvas. Real Material Text keeps
 * selectable font scaling, semantics and accessibility while this container avoids a deep app-bar
 * measure tree on page turns.
 */
@Composable
internal fun CenterAlignedTopAppBar(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().height(64.dp)) {
        Box(Modifier.align(Alignment.CenterStart), contentAlignment = Alignment.Center) { navigationIcon() }
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 148.dp),
            contentAlignment = Alignment.Center,
        ) { title() }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
