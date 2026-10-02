package com.junchen.jingdu

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
internal fun ReaderReadingStatusHost(
    controlsVisibility: State<Boolean>,
    stateProvider: () -> AppUiState,
    color: Color,
    background: Color,
    stats: ReaderStatsStore,
    modifier: Modifier = Modifier,
) {
    if (!controlsVisibility.value) {
        ReaderReadingStatus(stateProvider(), color, background, stats, modifier)
    }
}

@Composable
private fun ReaderReadingStatus(state: AppUiState, color: Color, background: Color, stats: ReaderStatsStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(60_000) } }
    val locale = LocalConfiguration.current.locales[0]
    val clock = if (state.settings.showClock) SimpleDateFormat("HH:mm", locale).format(now) else null
    val battery = if (state.settings.showBattery) (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }?.let { "$it%" } else null
    val bookProgress = if (state.length <= 0) 0 else ((state.position.toDouble() / state.length.toDouble()) * 100).roundToInt().coerceIn(0, 100)
    val remaining = stats.remainingMinutes(state.position, state.length)
    val pieces = buildList {
        add(resources.getString(R.string.reader_book_progress_value, bookProgress))
        remaining?.let { add(resources.getString(R.string.reader_book_remaining, it)) }
        clock?.let(::add)
        battery?.let(::add)
    }
    Surface(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(bottom = 6.dp), color = background.copy(alpha = 0.80f), shape = MaterialTheme.shapes.small) {
        Text(pieces.joinToString(" · "), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
internal fun AutoScrollLiveControl(settings: ReaderSettings, actions: JingduActions, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 5.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ actions.onSettingsChanged(settings.copy(autoScrollSpeedDpPerSecond = (settings.autoScrollSpeedDpPerSecond - 8).coerceAtLeast(12f))) }) { Icon(Icons.Default.Remove, stringResource(R.string.reader_auto_scroll_slow)) }
            Text(stringResource(R.string.reader_auto_scroll_speed_value, settings.autoScrollSpeedDpPerSecond.roundToInt()), style = MaterialTheme.typography.labelMedium)
            IconButton({ actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)) }) { Icon(Icons.Default.Pause, stringResource(R.string.reader_stop_auto_scroll)) }
            IconButton({ actions.onSettingsChanged(settings.copy(autoScrollSpeedDpPerSecond = (settings.autoScrollSpeedDpPerSecond + 8).coerceAtMost(320f))) }) { Icon(Icons.Default.Add, stringResource(R.string.reader_auto_scroll_fast)) }
        }
    }
}

@Composable
internal fun ReaderSelectionBar(
    selection: ReaderSelectionRange,
    settings: ReaderSettings,
    onHighlight: (ReaderHighlightStyle) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onLookup: () -> Unit,
    onExtendPrevious: (() -> Unit)?,
    onExtendNext: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.padding(16.dp), shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(Modifier.widthIn(max = 460.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(selection.excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderHighlightStyle.entries.forEach { style -> TextButton({ onHighlight(style) }) { Text(highlightLabel(style)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onNote) { Text(stringResource(R.string.reader_note)) }
                TextButton(onCopy) { Text(stringResource(R.string.reader_copy)) }
                TextButton(onShare) { Text(stringResource(R.string.reader_share)) }
                if (settings.dictionaryProcessTextEnabled) TextButton(onLookup) { Text(stringResource(R.string.reader_lookup)) }
            }
            if (onExtendPrevious != null || onExtendNext != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onExtendPrevious?.let { TextButton(it) { Text(stringResource(R.string.reader_selection_extend_previous)) } }
                onExtendNext?.let { TextButton(it) { Text(stringResource(R.string.reader_selection_extend_next)) } }
            }
            TextButton(onDismiss, Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun highlightLabel(style: ReaderHighlightStyle): String = stringResource(when (style) {
    ReaderHighlightStyle.YELLOW -> R.string.reader_highlight_yellow
    ReaderHighlightStyle.GREEN -> R.string.reader_highlight_green
    ReaderHighlightStyle.BLUE -> R.string.reader_highlight_blue
    ReaderHighlightStyle.PINK -> R.string.reader_highlight_pink
})

@Composable
internal fun ReaderHud(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f), contentColor = MaterialTheme.colorScheme.inverseOnSurface, shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
    }
}


internal fun readerContinuousOffsetForSource(
    window: ReaderDisplayWindow,
    sourcePosition: Long,
    layout: ReaderContinuousLayout,
): Float {
    if (window.displayText.isEmpty() || layout.lineCount <= 0) return 0f
    val relative = (sourcePosition - window.start).coerceIn(0L, window.map.sourceCodePoints)
    val displayed = window.map.displayForSource(relative)
    val utf = utf16Index(window.displayText, displayed)
        .coerceIn(0, (window.displayText.length - 1).coerceAtLeast(0))
    return layout.getLineTop(layout.getLineForOffset(utf))
}

internal fun readerContinuousNeedsNextWindow(
    scrollOffsetPx: Float,
    maxOffsetPx: Float,
    viewportHeightPx: Int,
    windowEnd: Long,
    documentLength: Long,
): Boolean {
    if (documentLength <= 0 || windowEnd >= documentLength - 1) return false
    val edgePx = viewportHeightPx.coerceAtLeast(0) * 0.25f
    return maxOffsetPx.coerceAtLeast(0f) - scrollOffsetPx.coerceAtLeast(0f) <= edgePx
}

internal fun highlightColor(style: ReaderHighlightStyle): Color = when (style) {
    ReaderHighlightStyle.YELLOW -> Color(0x55FFD54F)
    ReaderHighlightStyle.GREEN -> Color(0x554CAF50)
    ReaderHighlightStyle.BLUE -> Color(0x5542A5F5)
    ReaderHighlightStyle.PINK -> Color(0x55EC407A)
}

internal fun readerChromeCanAutoHide(
    readerReady: Boolean,
    controlsVisible: Boolean,
    panelOpen: Boolean,
    menuOpen: Boolean,
    selectionActive: Boolean,
    skimDragging: Boolean,
): Boolean = readerReady && controlsVisible && !panelOpen && !menuOpen && !selectionActive && !skimDragging

internal fun utf16Index(text: String, codePoints: Long): Int = if (text.isEmpty()) 0 else text.offsetByCodePoints(0, codePoints.coerceIn(0, text.codePointCount(0, text.length).toLong()).toInt())
internal fun readerBackground(palette: ReaderPalette): Color = when (palette) { ReaderPalette.PAPER -> Color(0xFFF7F0DE); ReaderPalette.LIGHT -> Color(0xFFFFFBFF); ReaderPalette.SEPIA -> Color(0xFFF3E5C8); ReaderPalette.NIGHT -> Color(0xFF151713); ReaderPalette.OLED -> Color.Black }
internal fun readerTextColor(palette: ReaderPalette): Color = when (palette) { ReaderPalette.NIGHT -> Color(0xFFE8E5DA); ReaderPalette.OLED -> Color(0xFFE8E8E8); else -> Color(0xFF24241F) }

