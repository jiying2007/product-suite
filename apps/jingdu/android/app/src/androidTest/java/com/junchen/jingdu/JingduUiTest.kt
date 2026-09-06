package com.junchen.jingdu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JingduUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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
