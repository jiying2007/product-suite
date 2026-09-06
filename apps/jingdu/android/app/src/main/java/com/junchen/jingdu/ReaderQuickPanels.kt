@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.junchen.jingdu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val QUICK_PALETTES = listOf(
    ReaderPalette.PAPER,
    ReaderPalette.SEPIA,
    ReaderPalette.LIGHT,
    ReaderPalette.NIGHT,
    ReaderPalette.OLED,
)

/**
 * Deliberately uses real Compose controls. The former Canvas control map was fast but brittle:
 * visual state, hit targets and accessibility could drift apart after the first interaction.
 * This panel is kept resident by JingduApp, so native controls remain cheap to reopen while every
 * visible affordance is also the real pointer/semantics target.
 */
@Composable
internal fun ReaderQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    fun visual(value: ReaderSettings) = actions.onSettingsChanged(value.copy(preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    fun setPaged() = actions.onSettingsChanged(s.copy(readingMode = ReaderMode.PAGED, autoScrollEnabled = false))
    fun setContinuous() = actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS))
    fun font(delta: Float) = visual(s.copy(fontSizeSp = (s.fontSizeSp + delta).coerceIn(14f, 40f)))
    fun speed(delta: Float) = actions.onSettingsChanged(s.copy(autoScrollSpeedDpPerSecond = (s.autoScrollSpeedDpPerSecond + delta).coerceIn(12f, 320f)))

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_quick_settings), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.reader_quick_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(actions.onClosePanel) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QUICK_PALETTES.forEach { palette ->
                    val selected = s.palette == palette
                    val label = quickPaletteLabel(palette)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .background(quickPaletteSwatch(palette), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { visual(s.copy(palette = palette)) }
                                .semantics {
                                    contentDescription = label
                                    this.selected = selected
                                },
                        )
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            QuickSettingRow(stringResource(R.string.font_size)) {
                FilledTonalButton({ font(-1f) }, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("−") }
                Text("${s.fontSizeSp.roundToInt()}sp", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton({ font(1f) }, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("+") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.line_spacing), style = MaterialTheme.typography.labelLarge)
                    Text("%.2f×".format(s.lineHeightMultiplier), color = MaterialTheme.colorScheme.primary)
                }
                ReaderLinearSlider(
                    value = s.lineHeightMultiplier,
                    valueRange = 1.15f..2.20f,
                    onValueChange = { visual(s.copy(lineHeightMultiplier = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    contentDescription = stringResource(R.string.line_spacing),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reader_brightness), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (s.useSystemBrightness) stringResource(R.string.system_default) else "${(s.readerBrightness * 100).roundToInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ReaderLinearSlider(
                    value = s.readerBrightness,
                    valueRange = 0.03f..1f,
                    onValueChange = { actions.onSettingsChanged(s.copy(useSystemBrightness = false, readerBrightness = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    contentDescription = stringResource(R.string.reader_brightness),
                )
                if (!s.useSystemBrightness) TextButton(
                    onClick = { actions.onSettingsChanged(s.copy(useSystemBrightness = true)) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.reader_system_brightness)) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.readingMode == ReaderMode.PAGED,
                    onClick = ::setPaged,
                    label = { Text(stringResource(R.string.reader_mode_paged)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = s.readingMode == ReaderMode.CONTINUOUS,
                    onClick = ::setContinuous,
                    label = { Text(stringResource(R.string.reader_mode_continuous)) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (s.readingMode == ReaderMode.CONTINUOUS) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.reader_auto_scroll_speed_value, s.autoScrollSpeedDpPerSecond.roundToInt()),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton({ speed(-8f) }) { Text(stringResource(R.string.reader_auto_scroll_slow)) }
                        TextButton({ speed(8f) }) { Text(stringResource(R.string.reader_auto_scroll_fast)) }
                    }
                    Button(
                        onClick = {
                            actions.onClosePanel()
                            actions.onSettingsChanged(s.copy(readingMode = ReaderMode.CONTINUOUS, autoScrollEnabled = !state.autoScrolling))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (state.autoScrolling) R.string.reader_stop_auto_scroll else R.string.reader_start_auto_scroll))
                    }
                }
            }
            OutlinedButton(
                onClick = { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reader_all_settings))
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun QuickSettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Fixed-cost slider retained for the reader progress hot path and small quick-setting sliders. */
@Composable
internal fun ReaderLinearSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    contentDescription: String? = null,
) {
    val latestChange = rememberUpdatedState(onValueChange)
    val latestFinished = rememberUpdatedState(onValueChangeFinished)
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range <= 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier.height(44.dp).pointerInput(valueRange.start, valueRange.endInclusive) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun update(x: Float) {
                    latestChange.value(
                        valueRange.start + range * (x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    )
                }
                update(down.position.x)
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    event.changes.firstOrNull()?.let { change ->
                        update(change.position.x)
                        pressed = change.pressed
                        change.consume()
                    } ?: run { pressed = false }
                }
                latestFinished.value()
            }
        }.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
            progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
            setProgress { target ->
                latestChange.value(target.coerceIn(valueRange))
                latestFinished.value()
                true
            }
        },
    ) {
        val y = size.height / 2f
        drawRoundRect(
            track,
            Offset(0f, y - 2.dp.toPx()),
            androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()),
            CornerRadius(2.dp.toPx()),
        )
        val x = size.width * fraction
        if (x > 0f) drawRoundRect(
            primary,
            Offset(0f, y - 2.dp.toPx()),
            androidx.compose.ui.geometry.Size(x, 4.dp.toPx()),
            CornerRadius(2.dp.toPx()),
        )
        drawCircle(primary, 9.dp.toPx(), Offset(x, y))
    }
}

@Composable
internal fun ReaderGestureCoach(settings: ReaderSettings, actions: JingduActions) {
    if (settings.gestureCoachDismissed) return
    AlertDialog(
        onDismissRequest = { actions.onSettingsChanged(settings.copy(gestureCoachDismissed = true)) },
        title = { Text(stringResource(R.string.reader_gesture_coach_title)) },
        text = { Text(stringResource(R.string.reader_gesture_coach_body)) },
        confirmButton = {
            TextButton({ actions.onSettingsChanged(settings.copy(gestureCoachDismissed = true)) }) {
                Text(stringResource(R.string.reader_gesture_coach_done))
            }
        },
    )
}

@Composable
private fun quickPaletteLabel(palette: ReaderPalette): String = when (palette) {
    ReaderPalette.PAPER -> stringResource(R.string.paper)
    ReaderPalette.SEPIA -> stringResource(R.string.reader_theme_sepia)
    ReaderPalette.LIGHT -> stringResource(R.string.light)
    ReaderPalette.NIGHT -> stringResource(R.string.night)
    ReaderPalette.OLED -> stringResource(R.string.reader_oled)
}

private fun quickPaletteSwatch(palette: ReaderPalette): Color = when (palette) {
    ReaderPalette.PAPER -> Color(0xFFF7F0DE)
    ReaderPalette.SEPIA -> Color(0xFFF3E5C8)
    ReaderPalette.LIGHT -> Color(0xFFFFFBFF)
    ReaderPalette.NIGHT -> Color(0xFF151713)
    ReaderPalette.OLED -> Color.Black
}
