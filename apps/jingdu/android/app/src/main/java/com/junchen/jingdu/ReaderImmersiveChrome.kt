@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun ReaderImmersiveTopBar(
    bookName: String,
    chapter: String?,
    actions: JingduActions,
    onMore: () -> Unit,
    onInteraction: () -> Unit,
) {
    val cleanBookName = bookName.removeSuffix(".txt").removeSuffix(".TXT")
    val title = chapter ?: cleanBookName
    val settingsDescription = stringResource(R.string.reading_settings)
    val latestMore = rememberUpdatedState(onMore)
    val latestInteraction = rememberUpdatedState(onInteraction)
    val openSettings = remember(actions) {
        { latestInteraction.value(); actions.onOpenPanel(ReaderPanel.QUICK_SETTINGS) }
    }
    val openChapters = remember(actions) {
        { latestInteraction.value(); actions.onOpenPanel(ReaderPanel.CHAPTERS) }
    }
    val openMore = remember {
        { latestInteraction.value(); latestMore.value() }
    }
    Surface(
        Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(actions.onBackToLibrary, Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_library))
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (chapter != null && chapter != cleanBookName) {
                    Text(
                        cleanBookName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = openSettings,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = settingsDescription },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Aa", style = MaterialTheme.typography.titleMedium) }
                IconButton(openChapters, Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.chapters))
                }
                IconButton(openMore, Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_reading_tools))
                }
            }
        }
    }
}

@Composable
internal fun ReaderImmersiveBottomDock(
    chapters: List<ChapterModel>,
    length: Long,
    autoPaging: Boolean,
    ttsPlaying: Boolean,
    fraction: Float,
    skimPreview: ReaderSkimPreview?,
    skimDragging: Boolean,
    showSkimReturn: Boolean,
    canLocationBack: Boolean,
    canLocationForward: Boolean,
    onLocationBack: () -> Unit,
    onLocationForward: () -> Unit,
    onBookmarks: () -> Unit,
    onTts: () -> Unit,
    onAutoPage: () -> Unit,
    onFractionChange: (Float) -> Unit,
    onFractionCommit: () -> Unit,
    onReturnSkim: () -> Unit,
    onInteraction: () -> Unit,
) {
    val progressDescription = stringResource(R.string.reading_progress)
    val displayFraction = if (skimDragging) fraction.coerceIn(0f, 1f) else readerPassiveProgressFraction(fraction)
    val percent = (displayFraction * 100f).roundToInt()

    val latestInteraction = rememberUpdatedState(onInteraction)
    val latestBookmarks = rememberUpdatedState(onBookmarks)
    val latestTts = rememberUpdatedState(onTts)
    val latestAutoPage = rememberUpdatedState(onAutoPage)
    val bookmarkAction = remember { { latestInteraction.value(); latestBookmarks.value() } }
    val ttsAction = remember { { latestInteraction.value(); latestTts.value() } }
    val autoPageAction = remember { { latestInteraction.value(); latestAutoPage.value() } }

    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (canLocationBack || canLocationForward) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (canLocationBack) AssistChip(
                            onClick = { onInteraction(); onLocationBack() },
                            label = { Text(stringResource(R.string.reader_location_back)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(17.dp)) },
                        )
                        if (canLocationBack && canLocationForward) Spacer(Modifier.width(8.dp))
                        if (canLocationForward) AssistChip(
                            onClick = { onInteraction(); onLocationForward() },
                            label = { Text(stringResource(R.string.reader_location_forward)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Redo, null, Modifier.size(17.dp)) },
                        )
                    }
                }

                if (skimDragging) {
                    ReaderSkimBubble(skimPreview, percent, displayFraction)
                } else if (showSkimReturn && skimPreview != null) {
                    ReaderSkimReturnRow(skimPreview, onReturn = { onInteraction(); onReturnSkim() })
                }

                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderProgressPercent(displayFraction)
                    Spacer(Modifier.weight(1f))
                    ReaderDockActions(
                        autoPaging = autoPaging,
                        ttsPlaying = ttsPlaying,
                        onBookmarks = bookmarkAction,
                        onTts = ttsAction,
                        onAutoPage = autoPageAction,
                    )
                }

                ReaderImmersiveProgressRail(
                    chapters = chapters,
                    length = length,
                    fraction = displayFraction,
                    contentDescription = progressDescription,
                    onFractionChange = onFractionChange,
                    onFractionCommit = onFractionCommit,
                    onInteraction = onInteraction,
                )
            }
        }
    }
}

@Composable
private fun ReaderProgressPercent(fraction: Float) {
    Text(
        "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}%",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ReaderDockActions(
    autoPaging: Boolean,
    ttsPlaying: Boolean,
    onBookmarks: () -> Unit,
    onTts: () -> Unit,
    onAutoPage: () -> Unit,
) {
    var moreOpen by remember { mutableStateOf(false) }
    IconButton(onBookmarks, Modifier.size(48.dp)) {
        Icon(Icons.Outlined.Bookmarks, stringResource(R.string.bookmarks), Modifier.size(21.dp))
    }
    if (ttsPlaying) {
        FilledTonalIconButton(onTts, Modifier.size(48.dp)) {
            Icon(Icons.Default.Pause, stringResource(R.string.pause_read_aloud), Modifier.size(21.dp))
        }
    } else {
        IconButton(onTts, Modifier.size(48.dp)) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.start_read_aloud), Modifier.size(21.dp))
        }
    }
    if (autoPaging) {
        FilledTonalIconButton(onAutoPage, Modifier.size(48.dp)) {
            Icon(Icons.Default.Pause, stringResource(R.string.stop_auto_page), Modifier.size(21.dp))
        }
    } else {
        Box {
            IconButton({ moreOpen = true }, Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreHoriz, stringResource(R.string.more_reading_tools), Modifier.size(21.dp))
            }
            DropdownMenu(moreOpen, onDismissRequest = { moreOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.start_auto_page)) },
                    onClick = { moreOpen = false; onAutoPage() },
                    leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                )
            }
        }
    }
}

@Composable
private fun ReaderSkimBubble(preview: ReaderSkimPreview?, fallbackPercent: Int, fraction: Float) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val bubbleWidth = 196.dp
        val travel = (maxWidth - bubbleWidth).coerceAtLeast(0.dp)
        val x = travel * fraction.coerceIn(0f, 1f)
        Surface(
            Modifier.offset(x = x).widthIn(max = bubbleWidth),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    preview?.chapter ?: stringResource(R.string.reader_book_progress_value, preview?.bookProgressPercent ?: fallbackPercent),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                preview?.chapterRemainingMinutes?.let {
                    Text(stringResource(R.string.reader_chapter_remaining, it), style = MaterialTheme.typography.labelSmall)
                }
                if (preview?.chapter != null) {
                    Text(stringResource(R.string.reader_book_progress_value, preview.bookProgressPercent), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReaderSkimReturnRow(preview: ReaderSkimPreview, onReturn: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            preview.chapter?.let { Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(preview.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onReturn) { Text(stringResource(R.string.reader_skim_return)) }
    }
}

@Composable
internal fun ReaderImmersiveProgressRail(
    chapters: List<ChapterModel>,
    length: Long,
    fraction: Float,
    contentDescription: String,
    onFractionChange: (Float) -> Unit,
    onFractionCommit: () -> Unit,
    onInteraction: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    val surface = MaterialTheme.colorScheme.surface
    val latestChange = rememberUpdatedState(onFractionChange)
    val latestCommit = rememberUpdatedState(onFractionCommit)
    val latestInteraction = rememberUpdatedState(onInteraction)
    val safeFraction = readerPassiveProgressFraction(fraction)
    var visualFraction by remember { mutableFloatStateOf(safeFraction) }
    var dragging by remember { mutableStateOf(false) }
    var pendingFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(safeFraction, dragging) {
        if (!dragging) visualFraction = safeFraction
    }
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val pending = pendingFraction
            if (pending != null) {
                pendingFraction = null
                latestChange.value(pending)
            }
        }
    }

    val activeChapter = remember(chapters, length, visualFraction) {
        if (length <= 0) -1 else readerFindActiveChapterIndex(chapters, (length.toDouble() * visualFraction).toLong())
    }
    val progressPercent = (visualFraction * 100f).roundToInt()
    val progressState = chapters.getOrNull(activeChapter)?.title
        ?.takeIf { it.isNotBlank() }
        ?.let { "$progressPercent% · $it" }
        ?: "$progressPercent%"

    val scrubber = Modifier.pointerInput(length) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            latestInteraction.value()
            dragging = true
            fun publish(x: Float) {
                val value = (x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                visualFraction = value
                pendingFraction = value
            }
            publish(down.position.x)
            var active = down
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                active = change
                publish(change.position.x)
                change.consume()
            } while (active.pressed)
            pendingFraction = null
            latestChange.value(visualFraction)
            dragging = false
            latestCommit.value()
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(scrubber)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = progressState
                progressBarRangeInfo = ProgressBarRangeInfo(visualFraction, 0f..1f)
                setProgress { target ->
                    latestInteraction.value()
                    pendingFraction = null
                    visualFraction = target.coerceIn(0f, 1f)
                    latestChange.value(visualFraction)
                    latestCommit.value()
                    true
                }
            },
    ) {
        val y = size.height / 2f
        val progressX = visualFraction * size.width
        val trackStroke = 3.dp.toPx()
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = trackStroke, cap = StrokeCap.Round)
        drawLine(primary, Offset(0f, y), Offset(progressX, y), strokeWidth = trackStroke, cap = StrokeCap.Round)

        if (length > 0 && chapters.isNotEmpty()) {
            val minSpacing = 13.dp.toPx().coerceAtLeast(1f)
            val maxTicks = (size.width / minSpacing).toInt().coerceAtLeast(1)
            val stride = ((chapters.size + maxTicks - 1) / maxTicks).coerceAtLeast(1)

            fun drawTick(index: Int) {
                val chapter = chapters[index]
                val x = (chapter.offset.toDouble() / length.toDouble()).toFloat().coerceIn(0f, 1f) * size.width
                val current = index == activeChapter
                drawLine(
                    primary.copy(alpha = if (current) 0.92f else 0.30f),
                    Offset(x, y - (if (current) 5.dp else 3.dp).toPx()),
                    Offset(x, y + (if (current) 5.dp else 3.dp).toPx()),
                    strokeWidth = (if (current) 2.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }

            var index = 0
            while (index < chapters.size) {
                drawTick(index)
                index += stride
            }
            if (activeChapter >= 0 && activeChapter % stride != 0) drawTick(activeChapter)
        }

        drawCircle(surface, radius = 7.dp.toPx(), center = Offset(progressX, y))
        drawCircle(primary, radius = 4.25.dp.toPx(), center = Offset(progressX, y))
    }
}

internal fun readerFindActiveChapterIndex(chapters: List<ChapterModel>, offset: Long): Int {
    var low = 0
    var high = chapters.lastIndex
    var answer = -1
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (chapters[middle].offset <= offset) {
            answer = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return answer
}

/**
 * Passive progress is intentionally displayed at 0.1% precision. A normal page turn in a long book
 * usually moves less than that, so the always-visible chrome stays composition/draw-stable while the
 * rail remains fully continuous during an actual drag via its local visualFraction.
 */
internal fun readerPassiveProgressFraction(fraction: Float): Float =
    ((fraction.coerceIn(0f, 1f) * 1000f).roundToInt() / 1000f).coerceIn(0f, 1f)

@Composable
internal fun ReaderCompactQuickSettingsSheet(state: AppUiState, actions: JingduActions) {
    val s = state.settings
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.72f).coerceIn(280f, 560f).dp
    val scrollState = rememberScrollState()
    val fontSizeLabel = stringResource(R.string.font_size)
    fun visual(value: ReaderSettings) = actions.onSettingsChanged(value.copy(preset = ReaderPreset.CUSTOM, activeThemeId = ""))
    fun setMode(mode: ReaderMode) = actions.onSettingsChanged(
        s.copy(readingMode = mode, autoScrollEnabled = if (mode == ReaderMode.PAGED) false else s.autoScrollEnabled),
    )

    ReaderPanelSurface(onDismiss = actions.onClosePanel) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_quick_settings), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(actions.onClosePanel) { Text(stringResource(R.string.reader_settings_done)) }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReaderPalette.entries.forEach { palette ->
                        val selected = s.palette == palette
                        val label = immersivePaletteLabel(palette)
                        Box(
                            Modifier
                                .size(48.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { visual(s.copy(palette = palette)) },
                                )
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(immersivePaletteSwatch(palette), CircleShape)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(fontSizeLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    FilledTonalButton(
                        onClick = { visual(s.copy(fontSizeSp = (s.fontSizeSp - 1f).coerceAtLeast(14f))) },
                        modifier = Modifier.semantics { contentDescription = "$fontSizeLabel −" },
                    ) { Text("−") }
                    Text(
                        "${s.fontSizeSp.roundToInt()}sp",
                        Modifier.padding(horizontal = 10.dp).semantics {
                            contentDescription = "$fontSizeLabel ${s.fontSizeSp.roundToInt()}sp"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FilledTonalButton(
                        onClick = { visual(s.copy(fontSizeSp = (s.fontSizeSp + 1f).coerceAtMost(40f))) },
                        modifier = Modifier.semantics { contentDescription = "$fontSizeLabel +" },
                    ) { Text("+") }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.readingMode == ReaderMode.PAGED,
                        onClick = { setMode(ReaderMode.PAGED) },
                        label = { Text(stringResource(R.string.reader_mode_paged)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = s.readingMode == ReaderMode.CONTINUOUS,
                        onClick = { setMode(ReaderMode.CONTINUOUS) },
                        label = { Text(stringResource(R.string.reader_mode_continuous)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            OutlinedButton(
                onClick = { actions.onClosePanel(); actions.onOpenPanel(ReaderPanel.SETTINGS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.reader_all_settings)) }
        }
    }
}

@Composable
internal fun readerAdaptiveTextWidth(fontSizeSp: Float): Dp =
    readerAdaptiveTextWidthDp(fontSizeSp, LocalConfiguration.current.screenWidthDp.toFloat()).dp

@Composable
internal fun readerAdaptiveTwoColumnWidth(fontSizeSp: Float): Dp =
    readerAdaptiveTwoColumnWidthDp(fontSizeSp, LocalConfiguration.current.screenWidthDp.toFloat()).dp

internal fun readerAdaptiveTextWidthDp(fontSizeSp: Float, windowWidthDp: Float): Float {
    val typographyWidth = (fontSizeSp * 32f).coerceIn(520f, 760f)
    val windowCap = (windowWidthDp * if (windowWidthDp < 600f) 0.94f else 0.82f).coerceAtLeast(1f)
    return min(typographyWidth, windowCap)
}

internal fun readerAdaptiveTwoColumnWidthDp(fontSizeSp: Float, windowWidthDp: Float): Float {
    val typographyWidth = (fontSizeSp * 60f + 28f).coerceIn(920f, 1200f)
    val windowCap = (windowWidthDp * 0.96f).coerceAtLeast(1f)
    return min(typographyWidth, windowCap)
}

@Composable
private fun immersivePaletteLabel(palette: ReaderPalette): String = when (palette) {
    ReaderPalette.PAPER -> stringResource(R.string.paper)
    ReaderPalette.SEPIA -> stringResource(R.string.reader_theme_sepia)
    ReaderPalette.LIGHT -> stringResource(R.string.light)
    ReaderPalette.NIGHT -> stringResource(R.string.night)
    ReaderPalette.OLED -> stringResource(R.string.reader_oled)
}

private fun immersivePaletteSwatch(palette: ReaderPalette) = when (palette) {
    ReaderPalette.PAPER -> androidx.compose.ui.graphics.Color(0xFFF7F0DE)
    ReaderPalette.SEPIA -> androidx.compose.ui.graphics.Color(0xFFF3E5C8)
    ReaderPalette.LIGHT -> androidx.compose.ui.graphics.Color(0xFFFFFBFF)
    ReaderPalette.NIGHT -> androidx.compose.ui.graphics.Color(0xFF242821)
    ReaderPalette.OLED -> androidx.compose.ui.graphics.Color.Black
}
