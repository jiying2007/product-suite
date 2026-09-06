#!/usr/bin/env bash
set -euo pipefail

require_file() {
  local path="$1"
  test -f "$path" || { echo "Reader asset missing: $path" >&2; exit 1; }
}

require_literal() {
  local path="$1" literal="$2" label="${3:-$2}"
  grep -F -q -- "$literal" "$path" || { echo "Reader contract missing [$label] in $path" >&2; exit 1; }
}

forbid_literal() {
  local path="$1" literal="$2" label="${3:-$2}"
  if grep -F -q -- "$literal" "$path"; then
    echo "Reader forbidden contract [$label] remains in $path" >&2
    exit 1
  fi
}

required=(
  docs/PRODUCTION_READINESS.md
  apps/jingdu/android/readerproto/src/main/proto/reader_settings.proto
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BookRepository.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSelectionController.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSkimController.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPanelSurface.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSmartChaptersPanel.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartTocCacheStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderInsightsPanels.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderFoundationsTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
  apps/jingdu/android/app/src/benchmark/AndroidManifest.xml
  apps/jingdu/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
  apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
  apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
  scripts/check-android-performance-slo.py
  scripts/test-android-performance-slo.py
  scripts/run-android-macrobenchmark-ci.sh
  platform/text/native/src/index_cache.h
  platform/text/native/src/index_cache.cpp
  platform/text/native/src/core_api_cached.cpp
  platform/text/native/tests/core_api_test.cpp
  platform/text/native/tests/core_performance_gate_test.cpp
)
for path in "${required[@]}"; do require_file "$path"; done

prefs=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPreferences.kt
screen=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
engine=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
controller=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderController.kt
book_repository=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BookRepository.kt
pipeline=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
annotations=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
stats=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
settings=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
app=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/JingduApp.kt
activity=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt
quick_panel=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt
panel_surface=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPanelSurface.kt
smart_panel=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSmartChaptersPanel.kt
smart_toc_cache=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartTocCacheStore.kt
hot_controls=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderHotControls.kt
fast_text=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt
service=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
player=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
navigator=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
proto=apps/jingdu/android/readerproto/src/main/proto/reader_settings.proto
foundations=apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderFoundationsTest.kt
motion=apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
journey=apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
baseline=apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
fixture=apps/jingdu/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
benchmark_manifest=apps/jingdu/android/app/src/benchmark/AndroidManifest.xml
macrobenchmark_gradle=apps/jingdu/android/macrobenchmark/build.gradle
benchmark_runner=scripts/run-android-macrobenchmark-ci.sh
smart_toc=apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartToc.kt
index_cache=platform/text/native/src/index_cache.cpp
cached_core=platform/text/native/src/core_api_cached.cpp
core_test=platform/text/native/tests/core_api_test.cpp

require_literal "$prefs" 'DataStore<ReaderSettingsProto>' 'typed settings datastore'
require_literal "$prefs" 'reader-settings.pb' ' settings store'
require_literal "$prefs" 'pending.debounce(350L)' 'settings write debounce'
require_literal "$prefs" 'ReaderPreset.LOW_VISION' 'low vision preset'
require_literal "$prefs" 'namedThemes' 'named themes'
require_literal "$prefs" 'extraDim' 'extra dim'
require_literal "$prefs" 'ReaderGestureAction' 'gesture actions'
require_literal "$proto" 'center_tap_action' 'center tap proto'
require_literal "$proto" 'double_tap_action' 'double tap proto'
forbid_literal "$prefs" 'preferencesDataStore' 'legacy preferencesDataStore'
forbid_literal "$prefs" 'jingdu_reader_v2' 'legacy reader v2 settings'

require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt 'class TextProjection' 'text projection'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt 'bestCost == Int.MAX_VALUE' 'projection bounded fallback'
require_literal "$engine" 'ReaderPresentationPipeline.present' 'shared presentation pipeline'
require_literal "$pipeline" 'SourceDisplayMap.compose' 'projection composition'
require_literal "$engine" 'typographyFingerprint = 31 * spec.fingerprint + settings.emphasizeHeadings.hashCode()' 'heading-aware typography fingerprint'
require_literal "$engine" 'androidLayoutText(displayText, density, settings.emphasizeHeadings)' 'heading-aware android pagination layout text'
require_literal "$engine" 'BREAK_STRATEGY_SIMPLE' 'bounded line breaking'
require_literal "$controller" 'WINDOW_CHARS = 1536' 'paged window'
require_literal "$controller" 'PAGE_CACHE_CHARS = 64 * 1024L' 'page cache size'
require_literal "$controller" 'PAGE_CACHE_PREFETCH_MARGIN_CHARS = 8192L' 'page prefetch margin'
require_literal "$controller" 'jingdu-page-prefetch' 'page prefetch worker'
require_literal "$engine" 'ReaderController(false)' 'continuous isolated controller'
require_literal "$engine" 'CONTINUOUS_WINDOW_CHARS = 4096L' '4K continuous window'
require_literal "$engine" 'CONTINUOUS_ALIGN_CHARS = 1024L' 'continuous alignment'
require_literal "$engine" 'CONTINUOUS_BACK_BUFFER_CHARS = 1024L' 'continuous back buffer'
require_literal "$book_repository" 'prewarmChapterIndex(book)' 'import-time chapter index prewarm'
require_literal "$book_repository" 'prewarmChapterIndex(updated)' 'redecode chapter index prewarm'
require_literal "$book_repository" 'source.chapters()' 'authoritative Core chapter prewarm'
require_literal "$smart_toc" 'MIN_CORE_CHAPTERS_FOR_COMPLETE_TOC = 20' 'sparse TOC threshold'
require_literal "$smart_toc" 'if (merged.size < MIN_CORE_CHAPTERS_FOR_COMPLETE_TOC)' 'sparse-only TOC enrichment'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt 'PARAGRAPH_SPACER' 'paragraph spacing sentinel'

require_literal "$screen" 'val slideAnimation = settings.pageAnimation == ReaderPageAnimation.SLIDE' 'conditional slide animation'
require_literal "$screen" 'val pageDirection = state.pageTurnDirection' 'state-owned slide direction'
require_literal "$activity" 'render(pageTurnDirection = 1)' 'next page direction publication'
require_literal "$activity" 'render(pageTurnDirection = -1)' 'previous page direction publication'
require_literal "$activity" 'override fun dispatchKeyEvent(event: KeyEvent)' 'pre-system Reader paging-key dispatch'
require_literal "$activity" '@SuppressLint("RestrictedApi") // Intercept Reader paging keys before system/focused-view handling.' 'documented ComponentActivity paging-key dispatch lint suppression'
require_literal "$activity" 'handleReaderPageKey(keyCode)' 'unified Reader paging-key route'
forbid_literal "$activity" 'override fun onKeyDown(keyCode: Int' 'late Reader paging-key callback residue'
require_literal "$screen" 'SelectionContainer(state = selectionState)' 'paged selection container'
require_literal "$screen" 'if (fastSelectionMode) {' 'selection container activation gate'
require_literal "$screen" 'Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures)' 'outer paged gesture observer'
require_literal "$screen" 'fun toggleChrome()' 'interaction-reset controls toggle helper'
require_literal "$screen" '::toggleChrome' 'interaction-reset controls toggle callback'
require_literal "$app" 'ReaderChromeRuntime.markPanelOpen()' 'panel lifecycle enters non-reactive chrome guard'
require_literal "$app" 'ReaderChromeRuntime.markPanelClosedAndWake()' 'panel close wakes reader chrome'
require_literal "$screen" 'snapshotFlow { panelState.value }' 'panel system-bar observation outside root composition'
forbid_literal "$screen" 'LaunchedEffect(activity, state.panel)' 'stale Reader-local panel system-bar subscription'
forbid_literal "$screen" 'LaunchedEffect(readerContentReady, controlsVisible, state.panel' 'stale Reader-local auto-hide subscription'
forbid_literal "$screen" '{ controlsVisibility.value = !controlsVisibility.value }' 'legacy direct controls toggle callback'
forbid_literal "$screen" '{ controlsVisible = !controlsVisible }' 'stale captured controls toggle callback'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/UiModels.kt 'val pageTurnDirection: Int = 0' 'page direction UI state'
require_literal "$screen" 'state.position, state.pageText, settings, state.annotations, state.tts, adaptiveLayout' 'paged route minimal source'
require_literal "$screen" 'private data class ReaderPreparedPage' 'single prepared page state'
require_literal "$screen" 'val presented = ReaderPresentationPipeline.present(sourceText, settings)' 'prepared page presentation'
require_literal "$screen" 'val snapshot = ReaderPageLayoutCache.measure' 'prepared page pagination'
require_literal "$screen" 'val visibleText = if (visibleEnd <= 0)' 'prepared visible prefix'
require_literal "$screen" 'readerAnnotatedText(sourceStart, visibleText, presented.map, annotations, tts, settings)' 'annotation prepared after pagination'
forbid_literal "$screen" 'produceState<ReaderPresentedText?>' 'two-stage paged presentation state'
forbid_literal "$screen" 'produceState<PageLayoutSnapshot?>' 'two-stage paged layout state'
forbid_literal "$screen" 'annotated.subSequence(0, visibleEnd)' 'post-annotation pagination'
forbid_literal "$screen" 'rememberScrollableState' 'Compose continuous scroll state'
forbid_literal "$fast_text" 'scrollable(scrollableState, Orientation.Vertical)' 'Compose continuous scrollable layer'
forbid_literal "$screen" 'snapshotFlow { scrollableState.isScrollInProgress }' 'Compose scroll progress observer'
require_literal "$fast_text" 'override fun onTouchEvent(event: MotionEvent)' 'native continuous gesture ownership'
require_literal "$fast_text" 'OverScroller(context)' 'native continuous fling'
require_literal "$fast_text" 'postOnAnimation(applyPendingScroll)' 'vsync-coalesced continuous scroll'
forbid_literal "$fast_text" 'ReaderPagedTextView' 'paged native wrapper that bypasses FrameTiming redraw'
require_literal "$fast_text" 'Canvas(Modifier.fillMaxSize())' 'paged observable Canvas replay'
require_literal "$fast_text" 'ReaderStaticLayoutBitmapTileSet' 'continuous worker-rasterized bitmap tiles'
require_literal "$fast_text" 'Bitmap.Config.ARGB_8888' 'continuous bounded raster format'
require_literal "$fast_text" 'bitmap.prepareToDraw()' 'continuous raster draw preparation'
require_literal "$fast_text" 'tileSet?.draw(canvas, scrollY, height)' 'visible continuous tile replay'
require_literal "$fast_text" 'canvas.drawBitmap(tile.bitmap' 'continuous cached bitmap replay'
require_literal "$fast_text" 'viewportHeightPx,' 'viewport-height raster state key'
require_literal "$fast_text" 'withContext(Dispatchers.Default)' 'off-main continuous raster build'
forbid_literal "$fast_text" 'canvas.drawRenderNode(tile.node)' 'continuous text display-list replay residue'
forbid_literal "$fast_text" 'RenderNode("ReaderContinuousTile' 'continuous RenderNode tile residue'
require_literal "$fast_text" 'renderedOffsetPx = next' 'vsync-coalesced viewport offset'
require_literal "$fast_text" 'postInvalidateOnAnimation()' 'real viewport frame invalidation'
forbid_literal "$fast_text" 'ReaderFramePulseView' 'synthetic compositor pulse residue'
forbid_literal "$fast_text" 'ReaderPageFramePulse' 'synthetic paged pulse residue'
forbid_literal "$fast_text" 'content.translationY = translation' 'whole-window translated child'
forbid_literal "$fast_text" 'content.setLayerType(View.LAYER_TYPE_HARDWARE, null)' 'oversized rasterized continuous layer'
forbid_literal "$fast_text" 'content.buildLayer()' 'oversized raster layer prebuild'
forbid_literal "$fast_text" 'scrollTo(0, pendingScrollY)' 'continuous ViewGroup scroll traversal'
forbid_literal "$fast_text" 'fun consumeDelta(delta: Float)' 'obsolete Compose scroll adapter'
require_literal "$fast_text" 'model.setOffset(model.offsetPx + (lastY - event.y))' 'native direct scroll property update'
require_literal "$screen" 'MutableSharedFlow<Unit>(extraBufferCapacity = 1)' 'settle-only continuous event channel'
require_literal "$screen" 'settleEvents.collect' 'settle-only continuous mapping'
require_literal "$engine" 'reusableHasHeadingStyle' 'heading-aware measured layout reuse'
forbid_literal "$screen" 'snapshotFlow { scrollOffsetPx.roundToInt()' 'per-delta continuous source mapping'
require_literal "$screen" 'settleContinuousPosition(scrollModel.offsetPx.roundToInt(), auto = false)' 'manual continuous settle'
require_literal "$screen" 'AUTO_SCROLL_POSITION_SAMPLE_NS = 250_000_000L' 'coarse auto-scroll position cadence'
require_literal "$screen" 'AUTO_SCROLL_COMMIT_CHARS = 512L' 'auto-scroll commit bound'
forbid_literal "$screen" 'abs(absolute - lastCommitted) >= 192' 'old noisy commit threshold'
require_literal "$screen" 'MAX_CHAPTER_TICKS = 96' 'chapter tick bound'
require_literal "$screen" 'take(MAX_CHAPTER_TICKS)' 'bounded chapter ticks'
require_literal "$screen" 'ReaderTopBar(book.name, currentChapter' 'top bar minimal state'
require_literal "$screen" 'chapters = state.chapters' 'bottom bar explicit chapters'
require_literal "$screen" 'autoPaging = state.autoPaging' 'bottom bar explicit motion'
forbid_literal "$screen" 'private fun ReaderTopBar(state: AppUiState' 'whole-state top bar subscription'
require_literal "$hot_controls" 'CenterAlignedTopAppBar' 'flattened reader top bar'
forbid_literal "$hot_controls" 'ReaderHotLine' 'Canvas-only reader chrome text'
require_literal "$quick_panel" 'real Compose controls' 'native quick settings controls'
forbid_literal "$quick_panel" 'ReaderCanvasPanel(' 'quick settings Canvas hit map'
require_literal "$smart_panel" 'LazyColumn(' 'scrolling chapters list'
require_literal "$smart_panel" 'rememberLazyListState(' 'chapter list state'
forbid_literal "$smart_panel" 'CHAPTER_WINDOW_ROWS' 'manual chapter pagination'
forbid_literal "$smart_panel" 'ReaderCanvasPanel(' 'chapter Canvas hit map'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderGesturePolicy.kt 'allowsPageSwipe' 'selection-aware paging policy'
require_literal apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderGesturePolicyTest.kt 'fastHorizontalSwipeCanPassSelectionConsumption' 'gesture arbitration regression test'
forbid_literal "$app" 'if (state.screen == AppScreen.READER && state.currentBook != null && !state.chaptersLoaded) actions.onEnsureChapters()' 'eager chapter UI-state preload'

require_literal "$activity" 'progressWorkers: ExecutorService' 'progress IO worker'
require_literal "$activity" 'tocWorkers: ExecutorService' 'TOC worker'
require_literal "$activity" 'THREAD_PRIORITY_BACKGROUND' 'background TOC priority'
require_literal "$activity" 'publishPositionOnly(book, bounded)' 'lightweight continuous position commit'
require_literal "$activity" 'SmartTocCacheStore(this)' 'revision TOC cache'
require_literal "$activity" 'smartTocCache.load(book.id, revision, length)' 'TOC cache read'
require_literal "$activity" 'smartTocCache.save(book.id, revision, length, report)' 'TOC cache write'
require_literal "$activity" 'val chapterModels = report.chapters.map' 'chapter projection off main thread'
require_literal "$activity" 'chapters = chapterModels' 'prebuilt chapter publication'
require_literal "$smart_toc_cache" 'cacheFile(bookId, revision, sourceLength)' 'revision-keyed TOC cache key'

require_literal "$app" 'val latestPosition = rememberUpdatedState(state.position)' 'stable position closure'
require_literal "$app" 'val trackedActions = remember(actions)' 'stable reader actions'
require_literal "$app" 'val panelState = rememberUpdatedState(state.panel)' 'stable ordinary panel state object'
require_literal "$app" 'val hotPanelState: State<ReaderPanel?>' 'stable hot panel state object'
require_literal "$app" 'readerState, trackedActions, snackbar, panelState,' 'ordinary panel state forwarded to Reader route'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt 'panelState: State<ReaderPanel?>' 'Reader route ordinary panel state boundary'
forbid_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt 'hotPanelState: State<ReaderPanel?>' 'hot panel state forwarded through Reader route'
forbid_literal "$screen" 'hotPanelState: State<ReaderPanel?>' 'hot panel state subscribed by Reader root'
require_literal "$app" 'ReaderHotPanelBackHandler(hotPanelState' 'isolated hot panel BackHandler'
require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.QUICK_SETTINGS, quickPanelState)' 'cached quick panel visibility'
require_literal "$app" 'PersistentReaderPanelLayer(hotPanelState, ReaderPanel.CHAPTERS, chaptersPanelState, keepDrawWarm = true)' 'cached chapters panel pre-record visibility'
forbid_literal "$app" 'PersistentReaderPanelLayer(panel ==' 'composition-owned panel visibility'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt 'val hotPanel: StateFlow<ReaderPanel?>' 'hot panel state flow'
require_literal "$activity" 'readerViewModel.openHotPanel(panel)' 'hot panel publication boundary'
require_literal "$activity" 'hotPanel = readerViewModel.hotPanel' 'hot panel flow injection'
require_literal "$app" 'ReaderPanel.QUICK_SETTINGS, ReaderPanel.CHAPTERS -> Unit' 'persistent panel route ownership'
require_literal "$screen" 'if (controlsVisible) {' 'visible-only reader chrome composition gate'
require_literal "$screen" 'ReaderTopBar(book.name, currentChapter, actions,' 'visible reader top chrome'
require_literal "$screen" 'ReaderBottomBar(' 'visible reader bottom chrome'
forbid_literal "$screen" 'private fun Modifier.readerControlLayer(' 'resident reader chrome accessibility isolation'
forbid_literal "$screen" '.readerControlLayer(' 'resident reader chrome placement wiring'
forbid_literal "$screen" 'hideFromAccessibility()' 'reader chrome accessibility hiding'
forbid_literal "$screen" 'translationY = if (controlsVisible) 0f else -READER_HIDDEN_LAYER_OFFSET_PX.toFloat()' 'graphics-only top control hiding'
forbid_literal "$screen" 'translationY = if (controlsVisible) 0f else READER_HIDDEN_LAYER_OFFSET_PX.toFloat()' 'graphics-only bottom control hiding'
require_literal "$screen" 'READER_HIDDEN_LAYER_OFFSET_PX' 'auto-scroll live-control offset'
require_literal "$screen" 'LaunchedEffect(' 'reader chrome auto-hide effect'
require_literal "$screen" 'ReaderChromeRuntime.panelOpen' 'non-reactive panel guard checked by auto-hide'
forbid_literal "$screen" 'ReaderChromeAutoHideSnapshot(' 'hot-panel snapshot collector regression'
forbid_literal "$screen" 'LaunchedEffect(panelState, hotPanelState' 'hot-panel state subscription in reader root'
require_literal "$screen" 'mutableStateOf(settings.readingMode != ReaderMode.PAGED)' 'paged chrome waits for rendered content'
require_literal "$screen" 'if (!readerContentReady) readerContentReady = true' 'visible chars arm initial chrome auto-hide'
require_literal "$screen" '::publishVisibleChars' 'paged visible-char readiness route'
require_literal "$screen" 'val controlsVisibility = rememberSaveable(book.id)' 'stable controls visibility state object'
require_literal "$screen" 'ReaderReadingStatusHost(' 'isolated reading status restart group'
forbid_literal "$screen" 'if (settings.showReadingStatus) ReaderReadingStatus(' 'root reading-status page subscription'
forbid_literal "$screen" 'padding(bottom = if (controlsVisible)' 'controls visibility composition padding'
forbid_literal "$app" 'awaitPointerEvent(PointerEventPass.Initial)' 'hidden panel swallowing reader input'
require_literal "$app" '.layout { measurable, constraints ->' 'placement-phase hot-panel visibility'
require_literal "$app" 'placeable.place(' 'hot-panel hit-test placement without full-screen graphics layer'
forbid_literal "$app" 'placeable.placeWithLayer(' 'persistent full-screen hot-panel graphics layer'
require_literal "$app" 'y = if (visible || warming) 0 else READER_PANEL_HIDDEN_OFFSET_PX' 'offscreen hidden hot-panel hit isolation after bounded pre-record'
forbid_literal "$app" 'hideFromAccessibility()' 'dynamic hot-panel accessibility subtree rebuild'
require_literal apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt 'hiddenHotPanelsRemainPhysicallyOffscreen' 'hidden hot-panel offscreen UI verification'
forbid_literal "$app" 'translationY = if (visible) 0f else READER_PANEL_HIDDEN_OFFSET_PX.toFloat()' 'graphics-only hot-panel hit isolation'
require_literal "$quick_panel" 'ReaderPanelSurface(onDismiss = actions.onClosePanel)' 'quick panel surface'
require_literal "$smart_panel" 'ReaderPanelSurface(onDismiss = actions.onClosePanel)' 'chapters panel surface'
require_literal "$smart_panel" 'TocPanelCache' 'bounded panel cache'
require_literal "$smart_panel" 'prewarmReaderSmartChaptersPanel' 'reader-open panel cache promotion'
require_literal "$activity" 'prewarmReaderSmartChaptersPanel(' 'reader-open Chapters prewarm'
require_literal "$smart_panel" 'derivedCache.load(book.id, book.normalizedSha256, state.length)' 'derived TOC reuse'
require_literal "$smart_panel" 'A revision-cache hit is authoritative for this panel' 'cache hit avoids global chapter hydration'
require_literal "$smart_panel" 'Import/re-decode prewarms this revision cache' 'cache-first Chapters panel'
require_literal "$smart_panel" 'if (cachedBase != null)' 'cache-first Chapters branch'
require_literal "$smart_panel" 'val overrides = withContext(Dispatchers.IO)' 'panel override IO off main'
require_literal "$smart_panel" 'withContext(Dispatchers.Default) { store.apply(computedBase, overrides) }' 'panel override projection off main'
require_literal "$smart_panel" 'SmartToc.evaluate(state.chapters.map' 'bounded cache-eviction fallback'
forbid_literal "$smart_panel" 'chapters.map { ReaderTextPresentation.chapterTitle' 'eager all-chapter title presentation'
require_literal "$smart_panel" 'val displayTitle = ReaderTextPresentation.chapterTitle(chapter.title, state.settings)' 'viewport-only chapter title presentation'
require_literal "$smart_panel" 'if (editing && quality.isNotBlank())' 'TOC quality metadata edit-only'
require_literal "$smart_panel" '.then(editSemantics)' 'TOC default navigation skips edit semantics'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPanelCanvasText.kt 'TextUtils.ellipsize' 'TOC navigation single-line canvas ellipsis'
forbid_literal "$smart_panel" 'else MaterialTheme.colorScheme.surface,' 'redundant unselected chapter row surface draw'
require_literal "$smart_panel" 'CustomAccessibilityAction(hideActionLabel)' 'chapter hide custom accessibility action'
require_literal "$smart_panel" 'Box(Modifier.clearAndSetSemantics {})' 'deduplicated chapter delete semantics'
forbid_literal "$smart_panel" '"${stringResource(R.string.toc_hide_heading)}: ${displayTitle}"' 'nested delete icon content description'
require_literal "$smart_panel" 'val mid = (low + high) ushr 1' 'logarithmic current chapter lookup'
require_literal "$smart_panel" 'initialFirstVisibleItemIndex = initialIndex' 'cache-hit TOC initial position before first visible frame'
require_literal "$smart_panel" 'mutableStateOf(initialChapters.isNotEmpty())' 'cache-hit skips post-show TOC scroll'
forbid_literal "$smart_panel" 'val listState = rememberLazyListState()' 'post-show default TOC positioning regression'
require_literal "$baseline" 'visibleBounds(By.desc("Reading settings")) ?: visibleBounds(By.text("Aa"))' 'profile semantic settings selector'
forbid_literal "$smart_panel" 'SmartToc.analyze(reader)' 'full scan inside panel'
require_literal "$panel_surface" 'same composition tree' 'single composition panel surface'
test ! -e apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderHotPanels.kt || { echo 'Reader superseded hot panels remain' >&2; exit 1; }

require_literal "$index_cache" 'kMagicV1 = "JDX1"' 'JDX1 detector'
require_literal "$index_cache" 'kMagicV2 = "JDX2"' 'JDX2 detector'
require_literal "$index_cache" 'load_chapter_cache' 'chapter cache load'
require_literal "$index_cache" 'save_index_cache_with_chapters' 'chapter cache save'
require_literal "$cached_core" 'jd_chapters_uncached_internal' 'authoritative uncached chapter scan'
require_literal "$cached_core" 'load_chapter_cache(document->path' 'cached chapter load path'
require_literal "$cached_core" 'save_index_cache_with_chapters' 'chapter cache upgrade path'
require_literal "$core_test" 'first chapter scan upgrades cache to JDX2' 'chapter cache upgrade test'
require_literal "$core_test" 'JDX2 chapters preserve authoritative output' 'chapter cache correctness test'

require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt '@Database' 'Room database'
require_literal "$annotations" 'ReaderAnnotationEntity' 'Room annotation entity'
require_literal "$annotations" 'reanchor(item' 'annotation reanchor'
require_literal "$stats" 'ReaderSessionEntity' 'Room session entity'
forbid_literal "$annotations" 'reader-v2-annotations.json' 'legacy annotation JSON'
forbid_literal "$stats" 'reader-v2-stats.json' 'legacy stats JSON'

require_literal "$screen" 'rememberSelectionState' 'selection state'
require_literal "$screen" 'SelectionContainer(state = selectionState)' 'selection container'
require_literal "$screen" 'ReaderSelectionController.fromSelectedTexts' 'selection projection'
require_literal "$screen" 'extendAcrossBoundary' 'two-stage selection'
require_literal "$screen" 'ReaderSkimController' 'skim controller'
require_literal "$screen" 'ReaderSkimPreviewCard' 'skim preview'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewModel.kt 'MutableStateFlow' 'UDF state flow'
require_literal "$app" 'ReaderSettingsScreen' 'settings route'
require_literal "$app" 'ReaderAnnotationsPanel' 'annotations route'
require_literal "$app" 'ReaderReadingMapPanel' 'reading map route'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt 'ReaderScreen' 'Reader route'

require_literal "$settings" 'ReaderPreset.entries' 'preset selector'
require_literal "$prefs" 'ReaderPreset.LOW_VISION' 'low vision settings'
require_literal "$settings" 'NamedThemes' 'named theme controls'
require_literal "$settings" 'extraDim' 'extra dim controls'
require_literal "$settings" 'twoStageSelectionEnabled' 'two-stage selection setting'
require_literal "$settings" 'dictionaryProcessTextEnabled' 'dictionary process-text setting'
require_literal "$settings" 'advancedGestureCustomizationEnabled' 'advanced gesture setting'
require_literal "$settings" 'centerTapAction' 'center tap setting'
require_literal "$settings" 'doubleTapAction' 'double tap setting'
require_literal "$screen" 'ReaderGestureAction' 'gesture runtime'
require_literal "$screen" 'ACTION_PROCESS_TEXT' 'dictionary runtime'
require_literal "$screen" 'WindowInsets.systemGestures' 'system gesture arbitration'
require_literal "$screen" 'if (maxPointers > 1) {' 'single-owner multi-touch arbitration'
require_literal "$screen" 'if (settings.pinchFontEnabled)' 'pinch stays inside reader gesture owner'
require_literal "$screen" 'onResizeFont(zoom)' 'single-owner pinch font resize'
forbid_literal "$screen" 'detectTransformGestures' 'competing transform gesture detector'
require_literal "$screen" 'ReaderGesturePolicy.allowsPageSwipe' 'selection-aware consumed drag arbitration'
require_literal "$screen" 'pendingCenterTap = launch' 'delayed center single-tap arbitration'
require_literal "$fast_text" 'selectionMode: Boolean? = null' 'selection fallback lifecycle ownership'

require_literal "$service" 'class TtsPlaybackService : MediaSessionService' 'Media3 session service'
require_literal "$service" 'MediaSession.Builder' 'Media3 session'
require_literal "$player" 'class ReaderTtsPlayer' 'Reader TTS player'
require_literal "$player" 'SimpleBasePlayer' 'Media3 player base'
require_literal "$navigator" 'previousSentence' 'previous sentence'
require_literal "$navigator" 'nextSentence' 'next sentence'
require_literal "$navigator" 'previousParagraph' 'previous paragraph'
require_literal "$navigator" 'nextParagraph' 'next paragraph'
require_literal apps/jingdu/android/app/src/main/AndroidManifest.xml 'androidx.media3.session.MediaSessionService' 'Media3 manifest service'
forbid_literal "$service" 'android.media.session.MediaSession' 'platform MediaSession authority'

require_literal "$foundations" 'localizedDeletionDoesNotScaleUnchangedSuffix' 'localized projection deletion test'
require_literal "$foundations" 'randomizedProjectionSoakRemainsBoundedAndMonotonic' 'projection soak'
require_literal "$foundations" 'map.sourceForDisplay(display.indexOf("world").toLong())' 'projection exactness'
require_literal "$foundations" 'typographyFingerprintCoversPaginationInputs' 'typography fingerprint test'
require_literal "$foundations" 'semanticTtsNavigationPureCoreSoakIsBounded' 'TTS semantic soak'
require_literal "$motion" 'repeat(100_000)' 'motion soak size'
require_literal "$motion" 'ReaderMotionState.AUTO_SCROLL' 'auto-scroll motion test'
require_literal "$motion" 'ReaderMotionState.AUTO_PAGE' 'auto-page motion test'
require_literal "$motion" 'ReaderMotionState.TTS' 'TTS motion test'

require_literal "$benchmark_manifest" '<profileable android:shell="true"' 'profileable benchmark target'
require_literal "$macrobenchmark_gradle" 'signingConfig = signingConfigs.debug' 'benchmark debug signing'
require_literal "$fixture" 'Benchmark-build only' 'benchmark-only fixture'
require_literal "$fixture" 'Reader' ' fixture'
forbid_literal "$fixture" 'Reader V2' 'V2 fixture residue'
require_literal "$fixture" 'pageAnimation = ReaderPageAnimation.SLIDE' 'deterministic real page animation'
require_literal "$fixture" 'tapPagingEnabled = true' 'deterministic tap paging'
require_literal "$fixture" 'swipePagingEnabled = true' 'deterministic swipe paging'
require_literal "$fixture" 'advancedGestureCustomizationEnabled = false' 'deterministic fixture gestures'
require_literal "$fixture" 'centerTapAction = ReaderGestureAction.CONTROLS' 'deterministic fixture center tap'
require_literal "$fixture" 'volumeKeyMode = ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS' 'deterministic volume paging'
require_literal "$fixture" 'BODY_LINES_PER_CHAPTER = 256' 'representative chapter density'
require_literal "$journey" 'open10MiBTxt' '10MiB journey'
require_literal "$journey" 'open100MiBTxt' '100MiB journey'
require_literal "$journey" 'StartupTimingMetric' 'startup timing metric'
require_literal "$journey" 'FrameTimingMetric' 'frame timing metric'
require_literal "$journey" 'PAGE_FORWARD_TAP_X' 'hosted real page-turn tap input'
require_literal "$journey" 'KEYCODE_VOLUME_DOWN' 'physical Release volume page-turn input'
require_literal "$journey" 'jingdu.pageTurnInput' 'physical Release input selector'
require_literal "$journey" 'PHYSICAL_VOLUME_INPUT = "physical-volume"' 'physical Release volume mode'
require_literal "$journey" 'val before = readerPosition()' 'page-turn authoritative start position'
require_literal "$journey" 'after > before' 'page-turn authoritative advance assertion'
require_literal "$fixture" 'ReaderInteractionRuntime.foregroundPosition' 'benchmark authoritative position source'
require_literal "$journey" 'ensureTopControlsVisible' 'real panel controls'
require_literal "$baseline" 'BaselineProfileRule' 'baseline profile rule'
require_literal "$baseline" 'prepareProfileTarget("paged")' 'profile target provisioning before collect'
require_literal "$baseline" 'readerCriticalJourneys' 'critical profile journeys'
require_literal "$baseline" 'PAGE_FORWARD_TAP_X' 'hosted profile page turns'
forbid_literal "$baseline" 'KEYCODE_VOLUME_DOWN' 'emulator hardware-volume profile dependency'
require_literal "$baseline" 'ensureTopControlsVisible' 'profile panel controls'
forbid_literal "$baseline" 'reader-v2' 'V2 baseline residue'
require_literal "$benchmark_runner" ':app:assembleBenchmark' 'benchmark app assembly'
require_literal "$benchmark_runner" ':macrobenchmark:assembleBenchmark' 'benchmark test assembly'
require_literal "$benchmark_runner" 'pm path "$TARGET_PACKAGE"' 'target install verification'
require_literal "$benchmark_runner" 'TEST_APK' 'test APK path'
require_literal "$benchmark_runner" 'pm list instrumentation' 'instrumentation discovery'
require_literal "$benchmark_runner" 'shell am instrument -w -r' 'direct instrumentation'
require_literal "$benchmark_runner" 'additionalTestOutputDir' 'benchmark evidence directory'
require_literal "$benchmark_runner" 'no-isolated-storage true' 'benchmark evidence access'
require_literal "$benchmark_runner" 'androidx.benchmark.enabledRules' 'benchmark rule filter'
require_literal "$benchmark_runner" 'run_instrumentation Macrobenchmark' 'Macrobenchmark instrumentation'
require_literal "$benchmark_runner" 'run_instrumentation BaselineProfile' 'BaselineProfile instrumentation'
require_literal "$benchmark_runner" 'Android guest is unavailable before ${label} target installation' 'target-swap guest readiness'
require_literal "$benchmark_runner" 'benchmarkData.json' 'benchmarkData evidence'
require_literal "$benchmark_runner" 'baseline-prof.txt' 'baseline profile evidence'
require_literal "$benchmark_runner" 'startup-prof.txt' 'startup profile evidence'
forbid_literal "$benchmark_runner" 'connectedBenchmarkAndroidTest' 'connected benchmark shortcut'
forbid_literal "$benchmark_runner" 'connectedCheck' 'connected check shortcut'
require_literal "$benchmark_runner" 'test-android-performance-slo.py' 'SLO self-test'
require_literal scripts/check-android-performance-slo.py 'frameDurationCpuMs' 'real frame metric'
require_literal scripts/check-android-performance-slo.py 'JINGDU_FRAME_P95_MS' 'P95 threshold'
require_literal scripts/check-android-performance-slo.py 'JINGDU_FRAME_P99_MS' 'P99 threshold'
python3 -m py_compile scripts/check-android-performance-slo.py scripts/test-android-performance-slo.py
python3 scripts/test-android-performance-slo.py
bash -n "$benchmark_runner"

require_literal platform/text/native/CMakeLists.txt 'JINGDU_PERF_FIXTURE_MIB=960' 'near-1GiB fixture'
require_literal platform/text/native/CMakeLists.txt 'jingdu_core_near_1gib_rss_gate_test' 'near-1GiB CTest'
require_literal platform/text/native/tests/core_performance_gate_test.cpp 'rssMiB < 640L' 'native RSS SLO'

for legacy in \
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt \
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt \
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java \
  scripts/verify-reader-v2.sh; do
  test ! -e "$legacy" || { echo "Reader hard cut left legacy asset: $legacy" >&2; exit 1; }
done
if find apps/jingdu/android/app/src/main/res -name 'strings_reader_v2.xml' -print -quit | grep -q .; then
  echo 'Reader hard cut left legacy reader_v2 resource container' >&2
  exit 1
fi

if grep -F -q 'android.permission.INTERNET' apps/jingdu/android/app/src/main/AndroidManifest.xml; then
  echo 'Reader forbids INTERNET' >&2
  exit 1
fi
if git grep -n -E 'readAt\([^,]+,[[:space:]]*(Long\.MAX_VALUE|[0-9]+[[:space:]]*\*[[:space:]]*1024[[:space:]]*\*[[:space:]]*1024)' -- "$engine" "$screen"; then
  echo 'Reader must keep bounded document windows' >&2
  exit 1
fi

echo 'Reader prelaunch contract OK: correctness/Media3/Room/Proto/UDF/cache/hot-path/soak/Macrobenchmark/BaselineProfile/RSS gates aligned'

# Interactive hot panels use real controls/lists; only the reader text raster path may use display caching.
forbid_literal "$app" 'invisibleToUser()' 'deprecated hidden panel accessibility semantics'

# Reader controls must not drive OS inset animations on every show/hide.
require_literal "$screen" 'if (panel != null) controller.show(WindowInsetsCompat.Type.systemBars())' 'ordinary-panel-only system bars'
forbid_literal "$screen" 'if (visible || state.panel != null)' 'controls-driven system bar animation'

# Frame evidence itself is a contract: truncated two-frame traces can never satisfy the SLO.
require_literal scripts/check-android-performance-slo.py 'REQUIRED_MIN_SAMPLES' 'performance evidence minimums'
require_literal scripts/check-android-performance-slo.py '"pageTurn10MiB": 20' 'page-turn evidence floor'
require_literal scripts/check-android-performance-slo.py '"continuousScroll10MiB": 500' 'continuous evidence floor'
require_literal scripts/check-android-performance-slo.py '"chaptersAndSettings10MiB": 50' 'panel evidence floor'
require_literal scripts/test-android-performance-slo.py 'test_required_interaction_sample_counts_reject_truncated_evidence' 'performance evidence regression test'

require_literal "$smart_panel" 'ReaderPanelText(' 'native-canvas default TOC row text'
require_literal apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPanelCanvasText.kt 'Canvas(modifier.semantics { contentDescription = text })' 'canvas TOC text accessibility label'

require_literal "$app" 'keepDrawWarm = true' 'Chapters bounded draw pre-record opt-in'
require_literal "$app" 'var drawWarmReady by remember(recordKey, keepDrawWarm)' 'bounded hot-panel pre-record state'
require_literal "$app" 'withFrameNanos { _ -> Unit }' 'frame-bounded hot-panel pre-record'
require_literal "$app" 'Modifier.clearAndSetSemantics {}' 'pre-record accessibility suppression without panel-state semantics rebuild'
require_literal "$app" 'val warming = keepDrawWarm && !drawWarmReady && !visible' 'bounded hidden pre-record placement state'
require_literal "$app" 'warming -> READER_PANEL_PREWARM_Z' 'Chapters pre-record behind Reader'
forbid_literal "$app" '.graphicsLayer(' 'no full-screen hot-panel graphics layer'
