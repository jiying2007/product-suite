@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.junchen.jingdu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class SelectionPayload(val range: ReaderSelectionRange, val clearNative: () -> Unit)

private data class ReaderPreparedPage(
    val snapshot: PageLayoutSnapshot,
    val annotated: AnnotatedString,
)

internal object ReaderChromeRuntime {
    var panelOpen: Boolean = false
        private set
    private var wakeRequest: (() -> Unit)? = null

    fun markPanelOpen() {
        panelOpen = true
    }

    fun markPanelClosedAndWake() {
        panelOpen = false
        wakeRequest?.invoke()
    }

    fun bindWakeRequest(request: () -> Unit) {
        wakeRequest = request
    }

    fun unbindWakeRequest(request: () -> Unit) {
        if (wakeRequest === request) wakeRequest = null
        panelOpen = false
    }
}

@Composable
internal fun ReaderScreen(
    state: AppUiState,
    actions: JingduActions,
    snackbar: SnackbarHostState,
    adaptiveLayout: ReaderAdaptiveLayout,
    panelState: State<ReaderPanel?>,
    canLocationBack: Boolean,
    canLocationForward: Boolean,
    onLocationBack: () -> Unit,
    onLocationForward: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context as? Activity
    val book = state.currentBook ?: return
    val settings = state.settings
    val pageDirection = state.pageTurnDirection
    val haptics = LocalHapticFeedback.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    val touchExploration = accessibility.isTouchExplorationEnabled
    val fontFamily = rememberReaderFontFamily(context, settings)
    val stats = remember(context) { ReaderStatsStore(context) }
    val skim = remember(book.id) { ReaderSkimController(context, book.id) }
    val controlsVisibility = rememberSaveable(book.id) { mutableStateOf(true) }
    var controlsVisible by controlsVisibility
    var chromeInteractionEpoch by remember { mutableLongStateOf(0L) }
    var readerContentReady by remember(book.id, settings.readingMode) {
        mutableStateOf(settings.readingMode != ReaderMode.PAGED)
    }
    SideEffect { ReaderInteractionRuntime.controlsVisible = controlsVisible }
    var more by remember { mutableStateOf(false) }
    var selection by remember(book.id) { mutableStateOf<SelectionPayload?>(null) }
    var twoStageAnchor by remember(book.id) { mutableStateOf<ReaderSelectionRange?>(null) }
    var twoStageDirection by remember(book.id) { mutableIntStateOf(0) }
    var noteDraft by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var skimFraction by remember { mutableFloatStateOf(if (state.length <= 0) 0f else state.position.toFloat() / state.length.toFloat()) }
    var skimDragging by remember { mutableStateOf(false) }
    var skimPreview by remember { mutableStateOf<ReaderSkimPreview?>(null) }
    var skimOrigin by remember { mutableLongStateOf(state.position) }
    var showSkimReturn by remember { mutableStateOf(false) }
    val wakeChrome = remember(book.id) {
        {
            controlsVisibility.value = true
            chromeInteractionEpoch++
            Unit
        }
    }
    DisposableEffect(wakeChrome) {
        ReaderChromeRuntime.bindWakeRequest(wakeChrome)
        onDispose { ReaderChromeRuntime.unbindWakeRequest(wakeChrome) }
    }

    val fraction = if (state.length <= 0) 0f else (state.position.toDouble() / state.length.toDouble()).toFloat().coerceIn(0f, 1f)
    val currentChapterIndex = remember(state.chapters, state.position) { state.chapters.indexOfLast { it.offset <= state.position } }
    val currentChapter = state.chapters.getOrNull(currentChapterIndex)?.title?.let { ReaderTextPresentation.chapterTitle(it, settings) }
    val latestStatusState = rememberUpdatedState(state)
    val statusStateProvider = remember { { latestStatusState.value } }

    DisposableEffect(book.id) {
        onDispose { skim.close() }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                val attrs = window.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attrs
            }
            if (activity != null && !activity.isChangingConfigurations && !activity.isFinishing) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    LaunchedEffect(activity, settings.orientation) {
        activity?.requestedOrientation = when (settings.orientation) {
            ReaderOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    LaunchedEffect(activity, settings.useSystemBrightness, settings.readerBrightness) {
        activity?.window?.let { window ->
            val attrs = window.attributes
            attrs.screenBrightness = if (settings.useSystemBrightness) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.readerBrightness.coerceIn(0.03f, 1f)
            window.attributes = attrs
        }
    }
    LaunchedEffect(activity, state.motion) {
        activity?.window?.let { window ->
            if (state.motion == ReaderMotionState.IDLE) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(activity, panelState) {
        snapshotFlow { panelState.value }
            .distinctUntilChanged()
            .collectLatest { panel ->
                activity?.window?.let { window ->
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (panel != null) controller.show(WindowInsetsCompat.Type.systemBars())
                    else controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
    }
    LaunchedEffect(
        readerContentReady, controlsVisible, more, selection, skimDragging,
        chromeInteractionEpoch, settings.controlsAutoHideMs,
    ) {
        if (readerChromeCanAutoHide(
                readerContentReady, controlsVisible, ReaderChromeRuntime.panelOpen,
                more, selection != null, skimDragging,
            )
        ) {
            delay(settings.controlsAutoHideMs)
            if (readerChromeCanAutoHide(
                    readerContentReady, controlsVisibility.value, ReaderChromeRuntime.panelOpen,
                    more, selection != null, skimDragging,
                )
            ) {
                controlsVisibility.value = false
            }
        }
    }
    LaunchedEffect(hudText) {
        if (hudText != null) { delay(1_050L); hudText = null }
    }
    LaunchedEffect(fraction, skimDragging) {
        if (!skimDragging) skimFraction = fraction
    }
    LaunchedEffect(skimDragging, skimFraction, state.chapters, settings) {
        if (!skimDragging) return@LaunchedEffect
        delay(70L)
        try {
            skimPreview = skim.preview(skimFraction, skimOrigin, settings, state.chapters, stats.charsPerMinute())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            skimPreview = null
        }
    }

    fun publishVisibleChars(chars: Long) {
        if (!readerContentReady) readerContentReady = true
        actions.onVisibleCharsChanged(chars)
    }
    fun keepChromeAlive() {
        controlsVisible = true
        chromeInteractionEpoch++
    }
    fun toggleChrome() {
        controlsVisible = !controlsVisible
        if (controlsVisible) chromeInteractionEpoch++
    }
    fun tick() { if (settings.hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun previous() {
        selection?.clearNative?.invoke(); selection = null
        actions.onNavigatePrevious()
    }
    fun next() {
        selection?.clearNative?.invoke(); selection = null
        actions.onNavigateNext()
    }
    fun updateBrightness(delta: Float) {
        val value = (settings.readerBrightness + delta).coerceIn(0.03f, 1f)
        if (abs(value - settings.readerBrightness) >= 0.005f) {
            hudText = resources.getString(R.string.reader_brightness_hud, (value * 100).roundToInt())
            actions.onSettingsChanged(settings.copy(useSystemBrightness = false, readerBrightness = value))
        }
    }
    fun resizeFont(zoom: Float) {
        if (!settings.pinchFontEnabled || abs(zoom - 1f) < 0.04f) return
        val value = (settings.fontSizeSp * zoom).coerceIn(14f, 40f)
        if (abs(value - settings.fontSizeSp) >= 0.5f) {
            hudText = resources.getString(R.string.reader_font_hud, value.roundToInt())
            actions.onSettingsChanged(settings.copy(fontSizeSp = value, preset = ReaderPreset.CUSTOM, activeThemeId = ""))
        }
    }
    fun acceptSelection(payload: SelectionPayload?) {
        if (payload == null) { selection = null; return }
        val anchor = twoStageAnchor
        if (anchor != null && twoStageDirection != 0) {
            val merged = ReaderSelectionController.extendAcrossBoundary(
                anchor,
                if (twoStageDirection < 0) payload.range.sourceStart else payload.range.sourceEnd,
                twoStageDirection < 0,
            ).copy(excerpt = listOf(anchor.excerpt, payload.range.excerpt).filter { it.isNotBlank() }.joinToString(" … ").take(800))
            selection = SelectionPayload(merged, payload.clearNative)
            twoStageAnchor = null
            twoStageDirection = 0
        } else selection = payload
        keepChromeAlive()
    }

    val snackbarControlsShiftPx = with(LocalDensity.current) { 88.dp.toPx() }
    val background = readerBackground(settings.palette)
    val textColor = readerTextColor(settings.palette)
    Box(Modifier.fillMaxSize().background(background)) {
        if (settings.readingMode == ReaderMode.CONTINUOUS && !state.cleanMode) {
            ContinuousReaderPage(
                state, actions, fontFamily, textColor, touchExploration,
                ::previous, ::next, ::toggleChrome, ::updateBrightness, ::resizeFont,
                { tick(); actions.onAddBookmark() }, ::acceptSelection,
            )
        } else {
            val slideAnimation = settings.pageAnimation == ReaderPageAnimation.SLIDE && settings.preset != ReaderPreset.LOW_VISION && pageDirection != 0
            if (slideAnimation) {
                val direction = pageDirection
                key(state.position, state.pageText) {
                    val pageVisible = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = pageVisible,
                        enter = slideInHorizontally { direction * (it / 12) } + fadeIn(),
                        exit = fadeOut(),
                        label = "reader-page-in",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PagedReaderPage(
                            state.position, state.pageText, settings, state.annotations, state.tts, adaptiveLayout, fontFamily, textColor, touchExploration,
                            ::publishVisibleChars, ::previous, ::next, ::toggleChrome,
                            ::updateBrightness, ::resizeFont, { tick(); actions.onAddBookmark() }, ::acceptSelection,
                        )
                    }
                }
            } else {
                PagedReaderPage(
                    state.position, state.pageText, settings, state.annotations, state.tts, adaptiveLayout, fontFamily, textColor, touchExploration,
                    ::publishVisibleChars, ::previous, ::next, ::toggleChrome,
                    ::updateBrightness, ::resizeFont, { tick(); actions.onAddBookmark() }, ::acceptSelection,
                )
            }
        }

        if (settings.extraDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.extraDim.coerceIn(0f, 0.80f))))

        if (settings.focusRulerLines > 0) Box(
            Modifier.fillMaxWidth().height((settings.fontSizeSp * settings.lineHeightMultiplier * settings.focusRulerLines).dp)
                .align(Alignment.Center).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
        )

        if (controlsVisible) {
            Box(Modifier.align(Alignment.TopCenter)) {
                ReaderTopBar(book.name, currentChapter, actions, { more = true }, ::keepChromeAlive)
            }
            if (more) Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp),
            ) { ReaderMoreMenu(actions) { more = false } }
            Box(Modifier.align(Alignment.BottomCenter)) {
                ReaderBottomBar(
                    chapters = state.chapters,
                    length = state.length,
                    autoPaging = state.autoPaging,
                    ttsPlaying = state.ttsPlaying,
                    fraction = skimFraction,
                    skimPreview = skimPreview,
                    skimDragging = skimDragging,
                    showSkimReturn = showSkimReturn,
                    canLocationBack = canLocationBack,
                    canLocationForward = canLocationForward,
                    onLocationBack = onLocationBack,
                    onLocationForward = onLocationForward,
                    onBookmarks = { actions.onOpenPanel(ReaderPanel.BOOKMARKS) },
                    onTts = actions.onToggleTts,
                    onAutoPage = actions.onToggleAutoPaging,
                    onFractionChange = { value ->
                        if (!skimDragging) { skimOrigin = state.position; skimDragging = true; showSkimReturn = false }
                        skimFraction = value
                    },
                    onFractionCommit = {
                        skimDragging = false
                        showSkimReturn = true
                        actions.onSeekFraction(skimFraction)
                    },
                    onReturnSkim = {
                        actions.onJump(skimOrigin)
                        skimPreview = null
                        showSkimReturn = false
                    },
                    onInteraction = ::keepChromeAlive,
                )
            }
        }
        if (settings.showReadingStatus) ReaderReadingStatusHost(
            controlsVisibility = controlsVisibility,
            stateProvider = statusStateProvider,
            color = textColor,
            background = background,
            stats = stats,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (state.autoScrolling) AutoScrollLiveControl(
            settings, actions,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp).graphicsLayer {
                translationY = if (controlsVisible) READER_HIDDEN_LAYER_OFFSET_PX.toFloat() else 0f
                alpha = if (controlsVisible) 0f else 1f
            },
        )

        selection?.let { selected ->
            ReaderSelectionBar(
                selection = selected.range,
                settings = settings,
                onHighlight = { style ->
                    actions.onAddAnnotation(selected.range.sourceStart, selected.range.sourceEnd, ReaderAnnotationKind.HIGHLIGHT, style, "", selected.range.excerpt)
                    selected.clearNative(); selection = null
                },
                onNote = { noteDraft = ""; showNoteDialog = true },
                onCopy = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Jingdu", selected.range.excerpt))
                    selected.clearNative(); selection = null
                },
                onShare = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, selected.range.excerpt) }, null))
                    selected.clearNative(); selection = null
                },
                onLookup = {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_PROCESS_TEXT).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_PROCESS_TEXT, selected.range.excerpt)
                            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                        }, null))
                    }
                },
                onExtendPrevious = if (settings.twoStageSelectionEnabled && settings.readingMode == ReaderMode.PAGED) ({
                    twoStageAnchor = selected.range; twoStageDirection = -1; selected.clearNative(); selection = null; previous()
                }) else null,
                onExtendNext = if (settings.twoStageSelectionEnabled && settings.readingMode == ReaderMode.PAGED) ({
                    twoStageAnchor = selected.range; twoStageDirection = 1; selected.clearNative(); selection = null; next()
                }) else null,
                onDismiss = { selected.clearNative(); selection = null },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        hudText?.let { text -> ReaderHud(text, Modifier.align(Alignment.Center)) }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).graphicsLayer {
                translationY = if (controlsVisible) -snackbarControlsShiftPx else 0f
            },
        )
    }

    if (showNoteDialog && selection != null) AlertDialog(
        onDismissRequest = { showNoteDialog = false },
        title = { Text(stringResource(R.string.reader_note)) },
        text = { OutlinedTextField(noteDraft, { noteDraft = it.take(2000) }, label = { Text(stringResource(R.string.reader_note_hint)) }) },
        confirmButton = { TextButton(onClick = {
            selection?.let { selected ->
                actions.onAddAnnotation(selected.range.sourceStart, selected.range.sourceEnd, ReaderAnnotationKind.NOTE, ReaderHighlightStyle.YELLOW, noteDraft, selected.range.excerpt)
                selected.clearNative()
            }
            selection = null; showNoteDialog = false
        }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton({ showNoteDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PagedReaderPage(
    sourceStart: Long,
    sourceText: String,
    settings: ReaderSettings,
    annotations: List<ReaderAnnotation>,
    tts: TtsPlaybackModel,
    adaptiveLayout: ReaderAdaptiveLayout,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    textColor: Color,
    touchExploration: Boolean,
    onVisibleCharsChanged: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onSelection: (SelectionPayload?) -> Unit,
) {
    val context = LocalContext.current
    val spec = remember(settings) { ReaderTypographySpec.from(settings) }
    val style = spec.composeTextStyle(textColor, fontFamily)
    val typeface = remember(settings.typeface, settings.customFontId, settings.fontWeight) { spec.androidTypeface(context) }
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)
    val systemRight = WindowInsets.systemGestures.getRight(density, layoutDirection)
    val columns = when (settings.wideColumns) {
        ReaderWideColumns.SINGLE -> 1
        ReaderWideColumns.DOUBLE -> if (adaptiveLayout.width >= ReaderAdaptiveWidth.MEDIUM && !adaptiveLayout.tabletop) 2 else 1
        ReaderWideColumns.AUTO -> if (adaptiveLayout.prefersTwoColumns) 2 else 1
    }

    // One worker result owns projection, pagination and selection metadata. The previous two-stage
    // presented -> snapshot publication forced multiple Reader recompositions for every page turn.
    val prepared by produceState<ReaderPreparedPage?>(
        null,
        sourceStart,
        sourceText,
        settings,
        annotations,
        tts,
        widthPx,
        heightPx,
        columns,
        spec.fingerprint,
    ) {
        if (widthPx <= 0 || heightPx <= 0 || sourceText.isEmpty()) return@produceState
        value = withContext(Dispatchers.Default) {
            val presented = ReaderPresentationPipeline.present(sourceText, settings)
            val snapshot = ReaderPageLayoutCache.measure(
                sourceText,
                presented.displayText,
                widthPx,
                heightPx,
                columns,
                settings,
                density,
                typeface,
                presented.map,
            )
            val visibleEnd = snapshot.displayedEndUtf16.coerceIn(0, presented.displayText.length)
            val visibleText = if (visibleEnd <= 0) "" else presented.displayText.substring(0, visibleEnd)
            val visual = readerAnnotatedText(sourceStart, visibleText, presented.map, annotations, tts, settings)
            ReaderPreparedPage(
                snapshot = snapshot,
                annotated = ReaderSelectionController.annotatedForSelection(sourceStart, visual, presented.map),
            )
        }
    }
    val preparedValue = prepared
    LaunchedEffect(preparedValue?.snapshot?.sourceCodePoints) {
        preparedValue?.snapshot?.sourceCodePoints
            ?.takeIf { it >= ReaderController.MIN_PAGE_CHARS }
            ?.let(onVisibleCharsChanged)
    }

    val selectionState = rememberSelectionState()
    var fastSelectionMode by remember(sourceStart) { mutableStateOf(false) }
    var sawFastSelection by remember(sourceStart) { mutableStateOf(false) }
    LaunchedEffect(selectionState.selectedTexts) {
        val range = ReaderSelectionController.fromSelectedTexts(selectionState.selectedTexts)
        if (range != null) sawFastSelection = true
        else if (sawFastSelection) { fastSelectionMode = false; sawFastSelection = false }
        onSelection(range?.let { SelectionPayload(it) { selectionState.clear() } })
    }
    val semantics = Modifier.readerAccessibilityActions(
        onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous),
        stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls),
        stringResource(R.string.reader_access_bookmark),
    )
    val gestures = if (touchExploration) Modifier else Modifier
        .readerGestures(settings, widthPx, heightPx, systemLeft, systemRight, fastSelectionMode, onPrevious, onNext, onToggleControls, onBrightnessDelta, onResizeFont, onBookmark)

    val pageContent: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        val ready = preparedValue ?: return@Box
        val annotated = ready.annotated
        if (columns == 2 && annotated.isNotEmpty()) {
            val firstEnd = ready.snapshot.firstColumnEndUtf16.coerceIn(0, annotated.length)
            Row(
                Modifier.widthIn(max = readerAdaptiveTwoColumnWidth(settings.fontSizeSp)).fillMaxHeight().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text(
                    annotated.subSequence(0, firstEnd), Modifier.weight(1f).fillMaxHeight(), style = style,
                    overflow = TextOverflow.Clip, selectionMode = fastSelectionMode,
                    onRequestSelection = { fastSelectionMode = true },
                )
                Text(
                    annotated.subSequence(firstEnd, annotated.length), Modifier.weight(1f).fillMaxHeight(), style = style,
                    overflow = TextOverflow.Clip, selectionMode = fastSelectionMode,
                    onRequestSelection = { fastSelectionMode = true },
                )
            }
        } else if (annotated.isNotEmpty()) {
            Text(
                annotated,
                Modifier.fillMaxHeight().widthIn(max = readerAdaptiveTextWidth(settings.fontSizeSp)).padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp),
                style = style,
                overflow = TextOverflow.Clip,
                selectionMode = fastSelectionMode,
                onRequestSelection = { fastSelectionMode = true },
            )
        }
        }
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (fastSelectionMode) {
            SelectionContainer(state = selectionState) { pageContent() }
        } else {
            pageContent()
        }
    }
}

@Composable
private fun ContinuousReaderPage(
    state: AppUiState,
    actions: JingduActions,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    textColor: Color,
    touchExploration: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onSelection: (SelectionPayload?) -> Unit,
) {
    val context = LocalContext.current
    val book = state.currentBook ?: return
    val settings = state.settings
    val engine = remember(book.id) { ReaderViewportEngine(context, book.id) }
    val scrollModel = remember(book.id) { ReaderContinuousScrollModel() }
    val settleEvents = remember(book.id) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)
    val systemRight = WindowInsets.systemGestures.getRight(density, layoutDirection)
    var window by remember(book.id) { mutableStateOf<ReaderDisplayWindow?>(null) }
    var layoutResult by remember(book.id) { mutableStateOf<ReaderContinuousLayout?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    var loading by remember(book.id) { mutableStateOf(false) }
    var lastCommitted by remember(book.id) { mutableLongStateOf(state.position) }
    val localPosition = remember(book.id) { AtomicLong(state.position) }

    suspend fun loadAround(target: Long) {
        if (loading) return
        loading = true
        try {
            val next = withContext(Dispatchers.IO) { engine.readAround(target, settings) }
            window = next
            localPosition.set(target.coerceIn(0L, (next.documentLength - 1).coerceAtLeast(0L)))
            layoutResult = null
        } finally { loading = false }
    }
    DisposableEffect(engine) { onDispose { engine.close() } }
    LaunchedEffect(book.id, settings.chineseMode, settings.chineseOverrides, settings.compressBlankLines, settings.paragraphSpacingEm) {
        withContext(Dispatchers.IO) { engine.clear() }
        loadAround(state.position)
        withContext(Dispatchers.IO) { engine.prefetch(state.position, settings) }
    }
    LaunchedEffect(state.tts.offset, state.tts.active) {
        if (state.tts.active && state.tts.offset >= 0 && abs(state.tts.offset - localPosition.get()) > 128) loadAround(state.tts.offset)
    }
    LaunchedEffect(window, layoutResult) {
        val w = window ?: return@LaunchedEffect
        val layout = layoutResult ?: return@LaunchedEffect
        if (w.displayText.isEmpty()) return@LaunchedEffect
        val utf = utf16Index(w.displayText, w.map.displayForSource((localPosition.get() - w.start).coerceAtLeast(0)))
        val line = layout.getLineForOffset(utf.coerceIn(0, (w.displayText.length - 1).coerceAtLeast(0)))
        scrollModel.setOffset(layout.getLineTop(line))
    }
    fun absoluteAtContinuousOffset(y: Int): Long? {
        val currentWindow = window ?: return null
        val layout = layoutResult ?: return null
        if (currentWindow.displayText.isEmpty() || layout.lineCount <= 0) return null
        val line = layout.getLineForVerticalPosition(y.toFloat()).coerceIn(0, layout.lineCount - 1)
        val utf = layout.getLineStart(line).coerceIn(0, currentWindow.displayText.length)
        val displayedCodePoints = currentWindow.displayText.codePointCount(0, utf).toLong()
        return (currentWindow.start + currentWindow.map.sourceForDisplay(displayedCodePoints))
            .coerceIn(0L, (currentWindow.documentLength - 1).coerceAtLeast(0L))
    }

    suspend fun settleContinuousPosition(y: Int, auto: Boolean) {
        val currentWindow = window ?: return
        val absolute = absoluteAtContinuousOffset(y) ?: return
        localPosition.set(absolute)
        val shouldCommit = if (auto) {
            abs(absolute - lastCommitted) >= AUTO_SCROLL_COMMIT_CHARS
        } else {
            absolute != lastCommitted
        }
        if (shouldCommit) {
            lastCommitted = absolute
            actions.onSyncTtsPosition(absolute)
        }
        val edge = (viewportHeight * 0.25f).roundToInt()
        val nearTop = y <= edge && currentWindow.start > 0
        val nearBottom = scrollModel.maxOffsetPx > 0f &&
            scrollModel.maxOffsetPx - y.toFloat() <= edge.toFloat() &&
            currentWindow.start + currentWindow.map.sourceCodePoints < currentWindow.documentLength - 1
        if (!loading && (nearTop || nearBottom)) loadAround(absolute)
    }

    // A manual swipe can emit dozens of scroll deltas. Position/source mapping is not visual work;
    // perform it once when the gesture settles instead of on every delta/frame.
    LaunchedEffect(settleEvents, layoutResult, window, viewportHeight) {
        settleEvents.collect { settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = false) }
    }
    LaunchedEffect(state.autoScrolling, settings.autoScrollSpeedDpPerSecond, window) {
        if (!state.autoScrolling) return@LaunchedEffect
        var lastFrame = 0L
        var lastPositionSample = 0L
        while (isActive && state.autoScrolling) {
            var samplePosition = false
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val seconds = (now - lastFrame).toDouble() / 1_000_000_000.0
                    val deltaPx = with(density) { (settings.autoScrollSpeedDpPerSecond * seconds).dp.toPx() }
                    scrollModel.setOffset(scrollModel.offsetPx + deltaPx)
                }
                if (lastPositionSample == 0L || now - lastPositionSample >= AUTO_SCROLL_POSITION_SAMPLE_NS) {
                    lastPositionSample = now
                    samplePosition = true
                }
                lastFrame = now
            }
            if (samplePosition) settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = true)
            val currentWindow = window ?: continue
            if (scrollModel.maxOffsetPx > 0f && scrollModel.offsetPx >= scrollModel.maxOffsetPx - 1f &&
                currentWindow.start + currentWindow.map.sourceCodePoints >= currentWindow.documentLength - 1) {
                actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)); break
            }
        }
    }

    val w = window
    val display = w?.displayText.orEmpty()
    val start = w?.start ?: state.position
    val map = w?.map ?: SourceDisplayMap.between("", "")
    val spec = remember(settings) { ReaderTypographySpec.from(settings) }
    val style = spec.composeTextStyle(textColor, fontFamily)
    val annotated = remember(start, display, state.annotations, state.tts, settings.emphasizeHeadings, spec.fingerprint) {
        ReaderSelectionController.annotatedForSelection(
            start,
            readerAnnotatedText(start, display, map, state.annotations, state.tts, settings),
            map,
        )
    }
    val selectionState = rememberSelectionState()
    var fastSelectionMode by remember(start) { mutableStateOf(false) }
    var sawFastSelection by remember(start) { mutableStateOf(false) }
    LaunchedEffect(selectionState.selectedTexts) {
        val range = ReaderSelectionController.fromSelectedTexts(selectionState.selectedTexts)
        if (range != null) sawFastSelection = true
        else if (sawFastSelection) { fastSelectionMode = false; sawFastSelection = false }
        onSelection(range?.let { SelectionPayload(it) { selectionState.clear() } })
    }
    val semantics = Modifier.readerAccessibilityActions(onPrevious, onNext, onToggleControls, onBookmark,
        stringResource(R.string.reader_surface), stringResource(R.string.reader_access_previous), stringResource(R.string.reader_access_next), stringResource(R.string.reader_access_controls), stringResource(R.string.reader_access_bookmark))

    SelectionContainer(state = selectionState) {
        Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; viewportHeight = it.height }.then(semantics), contentAlignment = Alignment.TopCenter) {
            Text(
                annotated,
                Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = settings.verticalPaddingDp.dp).widthIn(max = readerAdaptiveTextWidth(settings.fontSizeSp)),
                style = style,
                overflow = TextOverflow.Clip,
                scrollModel = scrollModel,
                settings = settings,
                systemLeftInsetPx = systemLeft,
                systemRightInsetPx = systemRight,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleControls = onToggleControls,
                onBrightnessDelta = onBrightnessDelta,
                onResizeFont = onResizeFont,
                onBookmark = onBookmark,
                onAnyTouch = { if (state.autoScrolling) actions.onSettingsChanged(settings.copy(autoScrollEnabled = false)) },
                onScrollSettled = { settleEvents.tryEmit(Unit) },
                selectionMode = fastSelectionMode,
                onRequestSelection = { fastSelectionMode = true },
                onTextLayout = { layoutResult = it },
            )
            if (loading && display.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

private fun readerAnnotatedText(
    sourceStart: Long,
    displayText: String,
    map: SourceDisplayMap,
    annotations: List<ReaderAnnotation>,
    tts: TtsPlaybackModel,
    settings: ReaderSettings,
): AnnotatedString = buildAnnotatedString {
    append(displayText)
    if (displayText.isEmpty()) return@buildAnnotatedString
    fun displayIndex(sourceAbsolute: Long): Int = utf16Index(displayText, map.displayForSource((sourceAbsolute - sourceStart).coerceAtLeast(0))).coerceIn(0, displayText.length)
    val sourceEnd = sourceStart + map.sourceCodePoints
    annotations.forEach { annotation ->
        if (annotation.sourceEnd <= sourceStart || annotation.sourceStart >= sourceEnd) return@forEach
        val a = displayIndex(annotation.sourceStart)
        val b = displayIndex(annotation.sourceEnd).coerceAtLeast(a)
        if (b > a) addStyle(SpanStyle(background = highlightColor(annotation.style)), a, b)
    }
    if (tts.active && tts.rangeEnd > tts.rangeStart) {
        val a = displayIndex(tts.rangeStart)
        val b = displayIndex(tts.rangeEnd).coerceAtLeast(a)
        if (b > a) addStyle(SpanStyle(background = Color(0x5558A67A)), a, b)
    }
    if (settings.emphasizeHeadings) {
        var cursor = 0
        displayText.lineSequence().forEach { line ->
            val end = (cursor + line.length).coerceAtMost(displayText.length)
            if (ReaderHeadingClassifier.isHeading(line.trim())) addStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), cursor, end)
            cursor = (end + 1).coerceAtMost(displayText.length)
        }
    }
    if (settings.paragraphSpacingEm > 0f) {
        val gap = (settings.fontSizeSp * settings.paragraphSpacingEm).coerceAtLeast(1f).sp
        displayText.forEachIndexed { index, char -> if (char == ReaderTypographySpec.PARAGRAPH_SPACER) addStyle(ParagraphStyle(lineHeight = gap), index, index + 1) }
    }
}

private fun Modifier.readerGestures(
    settings: ReaderSettings,
    widthPx: Int,
    heightPx: Int,
    systemLeftInsetPx: Int,
    systemRightInsetPx: Int,
    selectionActive: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onResizeFont: (Float) -> Unit,
    onBookmark: () -> Unit,
    onAnyTouch: () -> Unit = {},
): Modifier = pointerInput(settings, widthPx, heightPx, systemLeftInsetPx, systemRightInsetPx, selectionActive) {
    coroutineScope {
        val swipe = 52.dp.toPx()
        val tapSlop = 14.dp.toPx()
        val intentSlop = 12.dp.toPx()
        var lastCenterTapAt = 0L
        var pendingCenterTap: Job? = null

        fun dispatch(action: ReaderGestureAction) {
            when (action) {
                ReaderGestureAction.CONTROLS -> onToggleControls()
                ReaderGestureAction.BOOKMARK -> onBookmark()
                ReaderGestureAction.NEXT -> onNext()
                ReaderGestureAction.PREVIOUS -> onPrevious()
                ReaderGestureAction.NONE -> Unit
            }
        }
        fun cancelPendingCenterTap() {
            pendingCenterTap?.cancel()
            pendingCenterTap = null
            lastCenterTapAt = 0L
        }

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            ReaderInteractionRuntime.pagedGestureDowns += 1L
            onAnyTouch()
            var last = down
            var consumedByChild = down.isConsumed
            var maxPointers = 1
            var gestureIntent = 0 // 0 undecided, 1 horizontal paging, 2 vertical gesture.
            var initialPinchDistance: Float? = null
            var latestPinchDistance: Float? = null
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                maxPointers = maxOf(maxPointers, event.changes.size)
                if (event.changes.size >= 2) {
                    val first = event.changes[0].position
                    val second = event.changes[1].position
                    val distance = hypot(
                        (first.x - second.x).toDouble(),
                        (first.y - second.y).toDouble(),
                    ).toFloat()
                    if (distance > 0f) {
                        if (initialPinchDistance == null) initialPinchDistance = distance
                        latestPinchDistance = distance
                    }
                }
                if (event.changes.any { it.isConsumed }) consumedByChild = true
                event.changes.firstOrNull { it.id == down.id }?.let { last = it }
                if (gestureIntent == 0 && maxPointers == 1) {
                    val travel = last.position - down.position
                    if (travel.getDistance() >= intentSlop) {
                        gestureIntent = when {
                            abs(travel.x) > abs(travel.y) * 1.15f -> 1
                            abs(travel.y) > abs(travel.x) * 1.15f -> 2
                            else -> 0
                        }
                    }
                }
            } while (last.pressed)
            if (maxPointers > 1) {
                cancelPendingCenterTap()
                if (settings.pinchFontEnabled) {
                    val start = initialPinchDistance
                    val end = latestPinchDistance
                    if (start != null && end != null && start > 0f) {
                        val zoom = end / start
                        if (abs(zoom - 1f) >= 0.04f) onResizeFont(zoom)
                    }
                }
                return@awaitEachGesture
            }

            val delta = last.position - down.position
            val duration = last.uptimeMillis - down.uptimeMillis
            ReaderInteractionRuntime.lastPagedGestureDurationMs = duration
            ReaderInteractionRuntime.lastPagedGestureDistancePx = delta.getDistance()
            ReaderInteractionRuntime.lastPagedGestureConsumedByChild = consumedByChild
            val edgeGuard = 8.dp.toPx()
            if (gestureIntent != 1 && !consumedByChild && settings.brightnessGestureEnabled && widthPx > 0 &&
                down.position.x >= systemLeftInsetPx + edgeGuard &&
                down.position.x <= systemLeftInsetPx + widthPx * 0.14f &&
                abs(delta.y) > abs(delta.x) * 1.35f && abs(delta.y) >= swipe
            ) {
                cancelPendingCenterTap()
                onBrightnessDelta((-delta.y / heightPx.coerceAtLeast(1).toFloat()) * 0.8f)
                return@awaitEachGesture
            }

            if (gestureIntent != 2 && settings.swipePagingEnabled && widthPx > 0 &&
                down.position.x > systemLeftInsetPx + edgeGuard &&
                down.position.x < widthPx - systemRightInsetPx - edgeGuard &&
                ReaderGesturePolicy.allowsPageSwipe(consumedByChild, selectionActive, duration, delta.x, delta.y, swipe)
            ) {
                cancelPendingCenterTap()
                var forward = delta.x < 0
                if (settings.reversePagingGestures) forward = !forward
                if (forward) onNext() else onPrevious()
                return@awaitEachGesture
            }

            if (duration <= 360 && delta.getDistance() <= tapSlop && widthPx > 0) {
                ReaderInteractionRuntime.pagedTapCandidates += 1L
                if (down.position.x <= systemLeftInsetPx + edgeGuard ||
                    down.position.x >= widthPx - systemRightInsetPx - edgeGuard
                ) return@awaitEachGesture
                val edge = when (settings.tapZonePreset) {
                    ReaderTapZonePreset.BALANCED, ReaderTapZonePreset.CUSTOM -> widthPx * settings.tapZoneEdgeFraction
                    ReaderTapZonePreset.RIGHT_HANDED -> widthPx * 0.22f
                    ReaderTapZonePreset.LEFT_HANDED -> widthPx * 0.32f
                }
                when {
                    down.position.x < edge && settings.tapPagingEnabled -> {
                        cancelPendingCenterTap()
                        if (settings.reversePagingGestures) onNext() else onPrevious()
                    }
                    down.position.x > widthPx - edge && settings.tapPagingEnabled -> {
                        cancelPendingCenterTap()
                        if (settings.reversePagingGestures) onPrevious() else onNext()
                    }
                    else -> {
                        ReaderInteractionRuntime.pagedCenterDispatches += 1L
                        val centerAction = if (settings.advancedGestureCustomizationEnabled) settings.centerTapAction else ReaderGestureAction.CONTROLS
                        val doubleAction = if (settings.advancedGestureCustomizationEnabled) settings.doubleTapAction
                        else if (settings.doubleTapBookmarkEnabled) ReaderGestureAction.BOOKMARK else ReaderGestureAction.NONE
                        val tapAt = last.uptimeMillis
                        if (doubleAction == ReaderGestureAction.NONE) {
                            cancelPendingCenterTap()
                            dispatch(centerAction)
                        } else if (ReaderGesturePolicy.isDoubleTap(lastCenterTapAt, tapAt)) {
                            pendingCenterTap?.cancel()
                            pendingCenterTap = null
                            lastCenterTapAt = 0L
                            dispatch(doubleAction)
                        } else {
                            pendingCenterTap?.cancel()
                            lastCenterTapAt = tapAt
                            pendingCenterTap = launch {
                                delay(330L)
                                if (lastCenterTapAt == tapAt) {
                                    lastCenterTapAt = 0L
                                    pendingCenterTap = null
                                    dispatch(centerAction)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.readerAccessibilityActions(
    previous: () -> Unit, next: () -> Unit, controls: () -> Unit, bookmark: () -> Unit,
    surfaceLabel: String, previousLabel: String, nextLabel: String, controlsLabel: String, bookmarkLabel: String,
): Modifier = semantics {
    contentDescription = surfaceLabel
    customActions = listOf(
        CustomAccessibilityAction(previousLabel) { previous(); true },
        CustomAccessibilityAction(nextLabel) { next(); true },
        CustomAccessibilityAction(controlsLabel) { controls(); true },
        CustomAccessibilityAction(bookmarkLabel) { bookmark(); true },
    )
}

@Composable
private fun ReaderTopBar(bookName: String, chapter: String?, actions: JingduActions, onMore: () -> Unit, onInteraction: () -> Unit) {
    ReaderImmersiveTopBar(bookName, chapter, actions, onMore, onInteraction)
}

private enum class ReaderMoreMenuPage { MAIN, TEXT_TOOLS, MORE_TOOLS }

@Composable
private fun ReaderMoreMenu(actions: JingduActions, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(ReaderMoreMenuPage.MAIN) }
    DropdownMenu(true, onDismissRequest = onDismiss) {
        fun close(action: () -> Unit) { onDismiss(); action() }
        when (page) {
            ReaderMoreMenuPage.MAIN -> {
                DropdownMenuItem({ Text(stringResource(R.string.full_text_search)) }, { close { actions.onOpenPanel(ReaderPanel.SEARCH) } }, leadingIcon = { Icon(Icons.Default.Search, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_annotations)) }, { close { actions.onOpenPanel(ReaderPanel.ANNOTATIONS) } }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_reading_history)) }, { close { actions.onOpenPanel(ReaderPanel.READING_HISTORY) } }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_text_tools)) }, { page = ReaderMoreMenuPage.TEXT_TOOLS }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_more_tools)) }, { page = ReaderMoreMenuPage.MORE_TOOLS }, leadingIcon = { Icon(Icons.Default.MoreHoriz, null) })
            }
            ReaderMoreMenuPage.TEXT_TOOLS -> {
                DropdownMenuItem({ Text(stringResource(R.string.reader_more_menu_back)) }, { page = ReaderMoreMenuPage.MAIN }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) })
                DropdownMenuItem({ Text(stringResource(R.string.txt_doctor)) }, { close { actions.onOpenPanel(ReaderPanel.DOCTOR) } }, leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null) })
                DropdownMenuItem({ Text(stringResource(R.string.smart_clean4)) }, { close { actions.onOpenPanel(ReaderPanel.SMART_CLEAN_LAB) } }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) })
                DropdownMenuItem({ Text(stringResource(R.string.clean)) }, { close { actions.onOpenPanel(ReaderPanel.CLEAN) } }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) })
            }
            ReaderMoreMenuPage.MORE_TOOLS -> {
                DropdownMenuItem({ Text(stringResource(R.string.reader_more_menu_back)) }, { page = ReaderMoreMenuPage.MAIN }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) })
                DropdownMenuItem({ Text(stringResource(R.string.bookmarks)) }, { close { actions.onOpenPanel(ReaderPanel.BOOKMARKS) } }, leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) })
                DropdownMenuItem({ Text(stringResource(R.string.reader_reading_map)) }, { close { actions.onOpenPanel(ReaderPanel.READING_MAP); actions.onEnsureChapters() } }, leadingIcon = { Icon(Icons.Outlined.Map, null) })
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
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
    ReaderImmersiveBottomDock(
        chapters = chapters,
        length = length,
        autoPaging = autoPaging,
        ttsPlaying = ttsPlaying,
        fraction = fraction,
        skimPreview = skimPreview,
        skimDragging = skimDragging,
        showSkimReturn = showSkimReturn,
        canLocationBack = canLocationBack,
        canLocationForward = canLocationForward,
        onLocationBack = onLocationBack,
        onLocationForward = onLocationForward,
        onBookmarks = onBookmarks,
        onTts = onTts,
        onAutoPage = onAutoPage,
        onFractionChange = onFractionChange,
        onFractionCommit = onFractionCommit,
        onReturnSkim = onReturnSkim,
        onInteraction = onInteraction,
    )
}

@Composable
private fun ReaderProgressRail(
    chapters: List<ChapterModel>,
    length: Long,
    fraction: Float,
    contentDescription: String,
    onFractionChange: (Float) -> Unit,
    onFractionCommit: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    val surface = MaterialTheme.colorScheme.surface
    val tickOffsets = remember(chapters, length) {
        if (length <= 0 || chapters.isEmpty()) emptyList()
        else {
            val stride = ((chapters.size + MAX_CHAPTER_TICKS - 1) / MAX_CHAPTER_TICKS).coerceAtLeast(1)
            chapters.filterIndexed { index, _ -> index % stride == 0 }.map { it.offset }.take(MAX_CHAPTER_TICKS)
        }
    }
    val scrubber = Modifier.pointerInput(onFractionChange, onFractionCommit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun publish(x: Float) {
                onFractionChange((x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f))
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
            onFractionCommit()
        }
    }
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).then(scrubber).semantics { this.contentDescription = contentDescription },
    ) {
        val y = size.height / 2f
        val progressX = fraction.coerceIn(0f, 1f) * size.width
        val trackStroke = 3.dp.toPx()
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = trackStroke, cap = StrokeCap.Round)
        drawLine(primary, Offset(0f, y), Offset(progressX, y), strokeWidth = trackStroke, cap = StrokeCap.Round)
        if (length > 0) tickOffsets.forEach { offset ->
            val x = (offset.toDouble() / length.toDouble()).toFloat().coerceIn(0f, 1f) * size.width
            drawLine(
                primary.copy(alpha = 0.36f),
                Offset(x, y - 3.dp.toPx()),
                Offset(x, y + 3.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(surface, radius = 6.5.dp.toPx(), center = Offset(progressX, y))
        drawCircle(primary, radius = 4.25.dp.toPx(), center = Offset(progressX, y))
    }
}

@Composable
private fun ReaderSkimPreviewCard(preview: ReaderSkimPreview?, showReturn: Boolean, onReturn: () -> Unit) {
    if (preview == null) return
    ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            preview.chapter?.let { Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(preview.preview, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reader_chapter_progress_value, preview.chapterProgressPercent), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.reader_book_progress_value, preview.bookProgressPercent), style = MaterialTheme.typography.labelSmall)
                preview.chapterRemainingMinutes?.let { Text(stringResource(R.string.reader_chapter_remaining, it), style = MaterialTheme.typography.labelSmall) }
            }
            if (showReturn) TextButton(onReturn, Modifier.align(Alignment.End)) { Text(stringResource(R.string.reader_skim_return)) }
        }
    }
}

@Composable
private fun ReaderReadingStatusHost(
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
    Surface(modifier.navigationBarsPadding().padding(bottom = 6.dp), color = background.copy(alpha = 0.80f), shape = MaterialTheme.shapes.small) {
        Text(pieces.joinToString(" · "), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun AutoScrollLiveControl(settings: ReaderSettings, actions: JingduActions, modifier: Modifier = Modifier) {
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
private fun ReaderSelectionBar(
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
private fun ReaderHud(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f), contentColor = MaterialTheme.colorScheme.inverseOnSurface, shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
    }
}

private const val MAX_CHAPTER_TICKS = 96
private const val AUTO_SCROLL_COMMIT_CHARS = 512L
private const val AUTO_SCROLL_POSITION_SAMPLE_NS = 250_000_000L
private const val READER_HIDDEN_LAYER_OFFSET_PX = 16_384

private fun highlightColor(style: ReaderHighlightStyle): Color = when (style) {
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

private fun utf16Index(text: String, codePoints: Long): Int = if (text.isEmpty()) 0 else text.offsetByCodePoints(0, codePoints.coerceIn(0, text.codePointCount(0, text.length).toLong()).toInt())
internal fun readerBackground(palette: ReaderPalette): Color = when (palette) { ReaderPalette.PAPER -> Color(0xFFF7F0DE); ReaderPalette.LIGHT -> Color(0xFFFFFBFF); ReaderPalette.SEPIA -> Color(0xFFF3E5C8); ReaderPalette.NIGHT -> Color(0xFF151713); ReaderPalette.OLED -> Color.Black }
internal fun readerTextColor(palette: ReaderPalette): Color = when (palette) { ReaderPalette.NIGHT -> Color(0xFFE8E5DA); ReaderPalette.OLED -> Color(0xFFE8E8E8); else -> Color(0xFF24241F) }
