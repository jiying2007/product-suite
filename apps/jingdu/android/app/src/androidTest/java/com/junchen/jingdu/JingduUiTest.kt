package com.junchen.jingdu

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JingduUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun findContinuousNativeViewport(root: View): View? {
        if (root.javaClass.simpleName == "ReaderContinuousViewportView") return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findContinuousNativeViewport(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun continuousNativeViewport(): View {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var result: View? = null
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .singleOrNull()
                ?: error("expected exactly one resumed Jingdu test activity")
            val viewport = findContinuousNativeViewport(activity.window.decorView)
                ?: error("continuous native viewport is not attached")
            check(viewport.isShown && viewport.width > 0 && viewport.height > 0) {
                "continuous native viewport is not visible"
            }
            result = viewport
        }
        return requireNotNull(result)
    }

    private fun continuousNativeSelectablePoint(viewport: View): Offset {
        val layoutField = viewport.javaClass.getDeclaredField("textLayout").apply { isAccessible = true }
        val renderedOffsetField = viewport.javaClass.getDeclaredField("renderedOffsetPx").apply { isAccessible = true }
        val layout = requireNotNull(layoutField.get(viewport) as? android.text.StaticLayout) {
            "continuous native text layout is unavailable"
        }
        val renderedOffset = renderedOffsetField.getInt(viewport)
        val line = (0 until layout.lineCount).firstOrNull { index ->
            layout.getLineVisibleEnd(index) > layout.getLineStart(index)
        } ?: error("continuous native layout has no selectable visible line")
        val start = layout.getLineStart(line)
        val end = layout.getLineVisibleEnd(line)
        val offset = (start + (end - start) / 2).coerceIn(start, (end - 1).coerceAtLeast(start))
        val x = layout.getPrimaryHorizontal(offset).coerceIn(1f, (viewport.width - 1).coerceAtLeast(1).toFloat())
        val y = ((layout.getLineTop(line) + layout.getLineBottom(line)) / 2f - renderedOffset)
            .coerceIn(1f, (viewport.height - 1).coerceAtLeast(1).toFloat())
        return Offset(x, y)
    }

    private fun continuousNativeLongPressTriggered(viewport: View): Boolean =
        viewport.javaClass.getDeclaredField("longPressTriggered").let { field ->
            field.isAccessible = true
            field.getBoolean(viewport)
        }

    @Test fun emptyLibraryExplainsValueAndImportActionInActiveLocale() {
        composeRule.setContent { JingduApp(AppUiState(), noOpActions()) }
        composeRule.onNodeWithText(context.getString(R.string.app_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.library_tagline_terminal)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.import_txt)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(context.getString(R.string.select_txt)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.select_multiple_txt)).assertIsDisplayed()
    }

    @Test fun readerKeepsPrimaryReadingChromeDiscoverable() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(),
                    pageText = "Chapter 1\nA stable body used for Reader UI smoke verification.",
                    position = 500, length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")), chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText("Long Novel").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.back_to_library)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.chapters)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.start_read_aloud)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.bookmarks)).assertIsDisplayed()
    }

    @Test fun readerChromeAutoHidesWhenContentReady() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(),
                    pageText = "Chapter 1\nA stable body used for chrome auto-hide verification.",
                    position = 500, length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")), chaptersLoaded = true,
                    settings = ReaderSettings(
                        gestureCoachDismissed = true,
                        controlsAutoHideMs = 600L,
                        readingMode = ReaderMode.CONTINUOUS,
                    ),
                ), noOpActions(),
            )
        }
        val settingsNode = composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings))
        settingsNode.assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 3_000L) { !settingsNode.isDisplayed() }
        settingsNode.assertIsNotDisplayed()
    }

    @Test fun pagedReaderCenterTapTogglesChromeWithoutDoubleTapDelay() {
        val stablePage = buildString {
            append("Chapter 1\n")
            repeat(24) { append("A stable paged Reader line keeps center-tap gesture verification deterministic. 中文标点。\n") }
        }
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(),
                    pageText = stablePage,
                    position = 500, length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")), chaptersLoaded = true,
                    settings = ReaderSettings(
                        gestureCoachDismissed = true,
                        controlsAutoHideMs = 60_000L,
                        doubleTapBookmarkEnabled = false,
                    ),
                ), noOpActions(),
            )
        }
        val settingsNode = composeRule.onNodeWithContentDescription(context.getString(R.string.reading_settings))
        val surface = composeRule.onNodeWithContentDescription(context.getString(R.string.reader_surface))
        settingsNode.assertIsDisplayed()
        surface.performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 2_000L) { !settingsNode.isDisplayed() }
        settingsNode.assertIsNotDisplayed()
        surface.performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 2_000L) { settingsNode.isDisplayed() }
        settingsNode.assertIsDisplayed()
    }

    @Test fun pagedReaderRightTapAlwaysHasAReachableNextPath() {
        var nextCount = 0
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER,
                    currentBook = sampleBook(),
                    pageText = "Chapter 1\nA stable body used for real edge-tap paging verification.",
                    position = 500,
                    length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")),
                    chaptersLoaded = true,
                    settings = ReaderSettings(
                        gestureCoachDismissed = true,
                        tapPagingEnabled = false,
                        swipePagingEnabled = false,
                    ).withReachablePagedNavigation(),
                ),
                noOpActions().copy(onNavigateNext = { nextCount++ }),
            )
        }
        val surface = composeRule.onNodeWithContentDescription(context.getString(R.string.reader_surface))
        val bounds = surface.fetchSemanticsNode().boundsInRoot
        surface.performTouchInput {
            click(androidx.compose.ui.geometry.Offset(bounds.width * 0.88f, bounds.height * 0.50f))
        }
        composeRule.waitForIdle()
        assertEquals(1, nextCount)
    }

    @Test fun readerChromePrioritizesAaAndContextualTools() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", position = 500, length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")), chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText("Aa").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_location_back)).assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_location_forward)).assertIsNotDisplayed()
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.more_reading_tools))[0].performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_text_tools)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_more_tools)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reading_settings)).assertIsNotDisplayed()
    }

    @Test fun readerSettingsUseFourGroupsPreviewAndProgressiveGestures() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", length = 10_000,
                    panel = ReaderPanel.SETTINGS, settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_settings_group_appearance)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_typography_preview)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reader_settings_back)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_settings_group_navigation)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_paging_path_required)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_more_gesture_options)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_brightness_gesture)).assertIsNotDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_more_gesture_options)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_brightness_gesture)).performScrollTo().assertIsDisplayed()
    }

    @Test fun quickReadingSettingsStayTouchableAcrossRepeatedStateChanges() {
        var latest = ReaderSettings(gestureCoachDismissed = true)
        composeRule.setContent {
            var settings by remember { mutableStateOf(latest) }
            val actions = noOpActions().copy(onSettingsChanged = { updated -> settings = updated; latest = updated })
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", length = 10_000,
                    panel = ReaderPanel.QUICK_SETTINGS, settings = settings,
                ), actions,
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_quick_settings)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_brightness)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_all_settings)).assertIsDisplayed()
        composeRule.onNodeWithText("+").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("+").performClick()
        composeRule.waitForIdle()
        assertEquals(22f, latest.fontSizeSp)
        composeRule.onNodeWithText(context.getString(R.string.reader_mode_continuous)).performClick()
        composeRule.waitForIdle()
        assertEquals(ReaderMode.CONTINUOUS, latest.readingMode)
    }

    @Test fun chapterRowsRemainTouchableWithNativeScrollingPanel() {
        var jumped = -1L
        val targetIndex = 15
        val targetTitle = "Panel Target Chapter"
        val chapters = (0 until 30).map { index ->
            ChapterModel(index * 1000L, if (index == targetIndex) targetTitle else "Chapter ${index + 1}")
        }
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body",
                    position = targetIndex * 1000L, length = 30_000,
                    panel = ReaderPanel.CHAPTERS, chapters = chapters, chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions().copy(onJump = { jumped = it }),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(targetTitle).assertIsDisplayed().performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(targetIndex * 1000L, jumped)
    }

    @Test fun continuousReaderLongPressSelectsOnFirstGesture() {
        val book = sampleBook()
        ReaderInteractionRuntime.continuousReady = false
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER,
                    currentBook = book,
                    pageText = "Body",
                    position = 0,
                    length = book.charCount.coerceAtLeast(10_000L),
                    chapters = listOf(ChapterModel(0, "Chapter 1")),
                    chaptersLoaded = true,
                    settings = ReaderSettings(
                        readingMode = ReaderMode.CONTINUOUS,
                        gestureCoachDismissed = true,
                        controlsAutoHideMs = 60_000L,
                    ),
                ),
                noOpActions(),
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) { ReaderInteractionRuntime.continuousReady }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-continuous-native").assertIsDisplayed()
        // Exercise the real native onTouchEvent path with one DOWN/UP gesture, but keep the
        // long-press duration in MotionEvent eventTime instead of sleeping on hosted wall-clock time.
        // The product contract is deadline-based: ACTION_UP must synchronously honor an eligible
        // gesture whose event timeline crossed Android's system long-press timeout even if the
        // posted timeout callback was starved. This removes scheduler noise without retrying or
        // weakening the selection assertion.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val viewport = continuousNativeViewport()
        val target = continuousNativeSelectablePoint(viewport)
        val downAt = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(
            downAt, downAt, android.view.MotionEvent.ACTION_DOWN, target.x, target.y, 0,
        )
        try {
            instrumentation.runOnMainSync {
                check(viewport.dispatchTouchEvent(down)) {
                    "continuous native viewport rejected ACTION_DOWN"
                }
            }
        } finally {
            down.recycle()
        }
        val upAt = downAt + android.view.ViewConfiguration.getLongPressTimeout().toLong() + 450L
        val up = android.view.MotionEvent.obtain(
            downAt, upAt, android.view.MotionEvent.ACTION_UP, target.x, target.y, 0,
        )
        try {
            instrumentation.runOnMainSync {
                check(viewport.dispatchTouchEvent(up)) {
                    "continuous native viewport rejected ACTION_UP"
                }
            }
        } finally {
            up.recycle()
        }
        instrumentation.waitForIdleSync()
        check(continuousNativeLongPressTriggered(viewport)) {
            "continuous native long press did not commit a selectable range"
        }
        // The gesture itself must still be the first and only long press. At 200% font on the
        // hosted API 36 image, the native AndroidView selection callback can cross multiple UI
        // loop turns under system load, so give that callback the same readiness budget as the
        // native viewport without retrying the gesture or weakening the selection assertion.
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            runCatching {
                composeRule.onNodeWithText(context.getString(R.string.reader_copy)).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_copy)).assertIsDisplayed()
    }

    @Test fun hiddenHotPanelsRemainPhysicallyOffscreen() {
        val hiddenTitle = "Hidden Panel Sentinel"
        val chapters = listOf(ChapterModel(0, "Chapter 1"), ChapterModel(1000, hiddenTitle))
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", position = 0, length = 10_000,
                    panel = null, chapters = chapters, chaptersLoaded = true,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(hiddenTitle).assertIsNotDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_quick_settings)).assertIsNotDisplayed()
    }

    @Test fun annotationsAreFirstClassLocalReaderAssets() {
        val book = sampleBook()
        val annotation = ReaderAnnotation(
            id = "note-1", bookId = book.id, sourceStart = 100, sourceEnd = 140,
            kind = ReaderAnnotationKind.HIGHLIGHT, style = ReaderHighlightStyle.YELLOW,
            excerpt = "Local highlight", createdAt = 1, updatedAt = 1,
        )
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = book, pageText = "Body", length = 10_000,
                    panel = ReaderPanel.ANNOTATIONS, annotations = listOf(annotation),
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.reader_annotations)).assertIsDisplayed()
        composeRule.onNodeWithText("Local highlight").assertIsDisplayed()
    }

    @Test fun cleanSheetLetsFreeUsersSeeSmartCleanValueBeforePaywallInActiveLocale() {
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER, currentBook = sampleBook(), pageText = "Body", length = 10_000,
                    panel = ReaderPanel.CLEAN, smartCleanAnalyzed = true,
                    noiseCandidates = listOf(NoiseCandidateModel(94, 326, "promo_repeated", "www.example.com", selected = true)),
                    proUnlocked = false, proPrice = "US$6.99", settings = ReaderSettings(gestureCoachDismissed = true),
                ), noOpActions(),
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.smart_clean)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.rescan_noise)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.noise_summary, 1, 1, 326)).fetchSemanticsNode()
        composeRule.onNodeWithText("www.example.com").fetchSemanticsNode()
    }

    @Test fun libraryPrioritizesContinueReadingAndConsolidatesManagementTools() {
        val reading = sampleBook().copy(name = "Reading Now.txt", progress = 4_000, charCount = 10_000, touchedAt = 20)
        val unread = sampleBook().copy(id = "c".repeat(64), name = "Later.txt", progress = 0, touchedAt = 10, normalizedSha256 = "d".repeat(64))
        composeRule.setContent { JingduApp(AppUiState(books = listOf(unread, reading)), noOpActions()) }
        composeRule.onNodeWithText(context.getString(R.string.continue_reading)).assertIsDisplayed()
        composeRule.onNodeWithText("Reading Now").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_more_actions)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.add_folder_library)).assertIsNotDisplayed()
    }

    @Test fun librarySearchMatchesBookTitles() {
        val first = sampleBook().copy(name = "Moon Reader.txt", touchedAt = 20)
        val second = sampleBook().copy(id = "c".repeat(64), name = "River Story.txt", touchedAt = 10, normalizedSha256 = "d".repeat(64))
        composeRule.setContent { JingduApp(AppUiState(books = listOf(first, second)), noOpActions()) }
        composeRule.onNodeWithText(context.getString(R.string.search_hint)).performTextInput("Moon")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Moon Reader").assertIsDisplayed()
        composeRule.onNodeWithText("River Story").assertIsNotDisplayed()
    }

    @Test fun libraryImportExplainsSingleAndBatchModesBeforeSystemPicker() {
        composeRule.setContent { JingduApp(AppUiState(), noOpActions()) }
        composeRule.onNodeWithText(context.getString(R.string.import_txt)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.select_txt)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.select_multiple_txt)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.batch_import_picker_hint)).assertIsDisplayed()
    }

    @Test fun txtHealthPanelScrollsToBottomActions() {
        val report = TxtDoctorReport(
            healthScore = 72,
            encodingScore = 88,
            tocScore = 70,
            cleanScore = 64,
            textScore = 92,
            chapterCount = 18,
            tocAnomalies = 3,
            noiseCandidates = 6,
            garbledWindows = 1,
            replacementCharacters = 2,
            hardWrapDetected = false,
            estimatedJoinedBreaks = 0,
            sizeBytes = 128_000,
            encoding = "UTF-8",
        )
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER,
                    currentBook = sampleBook(),
                    pageText = buildString {
                        repeat(24) { append("A wrapped TXT health preview line that makes the inspection sheet taller than one screen.\n") }
                    },
                    position = 500,
                    length = 10_000,
                    panel = ReaderPanel.TXT_HEALTH,
                    txtHealthReport = report,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ),
                noOpActions(),
            )
        }
        composeRule.waitForIdle()
        val healthList = composeRule.onNodeWithTag("txt-health-list")
        val recheck = composeRule.onNodeWithText(context.getString(R.string.txt_health_recheck))
        scrollUntilDisplayed(healthList, recheck)
        recheck.assertIsDisplayed()
        val details = composeRule.onNodeWithText(context.getString(R.string.txt_health_details))
        scrollUntilDisplayed(healthList, details)
        details.assertIsDisplayed()
    }

    @Test fun txtDoctorSheetScrollsToBottomActions() {
        val book = sampleBook()
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER,
                    currentBook = book,
                    pageText = "Body",
                    position = 500,
                    length = book.charCount.coerceAtLeast(10_000L),
                    panel = ReaderPanel.DOCTOR,
                    settings = ReaderSettings(gestureCoachDismissed = true),
                ),
                noOpActions(),
            )
        }
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            runCatching {
                composeRule.onNodeWithText(context.getString(R.string.doctor_encoding)).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        val doctorList = composeRule.onNodeWithTag("txt-doctor-list")
        val encoding = composeRule.onNodeWithText(context.getString(R.string.text_encoding))
        scrollUntilDisplayed(doctorList, encoding)
        encoding.assertIsDisplayed()
        val chapters = composeRule.onNodeWithText(context.getString(R.string.chapters))
        scrollUntilDisplayed(doctorList, chapters)
        chapters.assertIsDisplayed()
        val clean = composeRule.onNodeWithText(context.getString(R.string.smart_clean4))
        scrollUntilDisplayed(doctorList, clean)
        clean.assertIsDisplayed()
    }

    private fun scrollUntilDisplayed(
        list: androidx.compose.ui.test.SemanticsNodeInteraction,
        target: androidx.compose.ui.test.SemanticsNodeInteraction,
        attempts: Int = 12,
    ) {
        repeat(attempts) {
            if (target.isDisplayed()) return
            val bounds = list.fetchSemanticsNode().boundsInRoot
            list.performTouchInput {
                swipe(
                    start = Offset(bounds.width * 0.50f, bounds.height * 0.72f),
                    end = Offset(bounds.width * 0.50f, bounds.height * 0.38f),
                    durationMillis = 260L,
                )
            }
            composeRule.waitForIdle()
        }
    }

    private fun sampleBook() = ReaderInstrumentationFixture.book(context)

    private fun noOpActions() = JingduActions(
        onImport = {}, onBatchImport = {}, onOpenBook = {}, onDeleteLibraryBook = {},
        onToggleFavorite = {}, onSetBookTags = { _, _ -> }, onBackToLibrary = {},
        onNavigatePrevious = {}, onNavigateNext = {}, onSeekFraction = {}, onVisibleCharsChanged = {},
        onOpenPanel = {}, onClosePanel = {}, onSearchQueryChanged = {}, onSearch = {}, onJump = {},
        onSyncTtsPosition = {}, onEnsureChapters = {}, onAddBookmark = {}, onDeleteBookmark = {},
        onAddAnnotation = { _, _, _, _, _, _ -> }, onDeleteAnnotation = {}, onImportFont = {},
        onAddRule = { _, _, _ -> }, onDeleteRule = {}, onClearRules = {}, onAnalyzeSmartClean = {},
        onToggleNoiseCandidate = {}, onApplySmartClean = {}, onUndoSmartClean = {},
        onAddGlobalRule = { _, _, _ -> }, onDeleteGlobalRule = {}, onClearGlobalRules = {},
        onInstallRecommendedRules = {}, onExportGlobalRules = {}, onImportGlobalRules = {},
        onUpgradePro = {}, onRestorePro = {}, onExportBackup = {}, onImportBackup = {},
        onToggleCleanPreview = {}, onExportClean = {}, onEncodingSelected = {}, onSettingsChanged = {},
        onToggleTts = {}, onToggleAutoPaging = {}, onSleepTimer = {}, onRequestDeleteCurrent = {},
        onDismissDelete = {}, onConfirmDeleteCurrent = {}, onMessageConsumed = {},
    )
}
