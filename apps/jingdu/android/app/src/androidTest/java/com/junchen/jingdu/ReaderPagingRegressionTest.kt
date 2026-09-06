package com.junchen.jingdu

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderPagingRegressionTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun pagedReaderNaturalHorizontalSwipeAdvances() {
        var nextCount = 0
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER,
                    currentBook = sampleBook(),
                    pageText = "Chapter 1\nA stable body used for natural horizontal swipe paging verification.",
                    position = 500,
                    length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")),
                    chaptersLoaded = true,
                    settings = ReaderSettings(
                        gestureCoachDismissed = true,
                        tapPagingEnabled = false,
                        swipePagingEnabled = true,
                    ),
                ),
                noOpActions().copy(onNavigateNext = { nextCount++ }),
            )
        }
        composeRule.waitForIdle()
        val surface = composeRule.onNodeWithContentDescription(context.getString(R.string.reader_surface))
        val bounds = surface.fetchSemanticsNode().boundsInRoot
        surface.performTouchInput {
            swipe(
                start = Offset(bounds.width * 0.80f, bounds.height * 0.50f),
                end = Offset(bounds.width * 0.20f, bounds.height * 0.50f),
                durationMillis = 700L,
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, nextCount)
    }

    @Test fun leftEdgeVerticalBrightnessGestureNeverPages() {
        var previousCount = 0
        var nextCount = 0
        composeRule.setContent {
            JingduApp(
                AppUiState(
                    screen = AppScreen.READER,
                    currentBook = sampleBook(),
                    pageText = "Chapter 1\nA stable body used for left-edge vertical gesture arbitration verification.",
                    position = 500,
                    length = 10_000,
                    chapters = listOf(ChapterModel(0, "Chapter 1")),
                    chaptersLoaded = true,
                    settings = ReaderSettings(
                        gestureCoachDismissed = true,
                        tapPagingEnabled = false,
                        swipePagingEnabled = true,
                        brightnessGestureEnabled = true,
                    ),
                ),
                noOpActions().copy(
                    onNavigatePrevious = { previousCount++ },
                    onNavigateNext = { nextCount++ },
                ),
            )
        }
        composeRule.waitForIdle()
        val surface = composeRule.onNodeWithContentDescription(context.getString(R.string.reader_surface))
        val bounds = surface.fetchSemanticsNode().boundsInRoot
        surface.performTouchInput {
            swipe(
                start = Offset(bounds.width * 0.08f, bounds.height * 0.75f),
                end = Offset(bounds.width * 0.08f, bounds.height * 0.25f),
                durationMillis = 700L,
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, previousCount)
        assertEquals(0, nextCount)
    }

    @Test fun readerProgressRailIsDirectlyScrubbableWithoutExpansion() {
        var seekFraction = -1f
        composeRule.setContent {
            readerState(
                noOpActions().copy(onSeekFraction = { seekFraction = it }),
            )
        }
        composeRule.waitForIdle()
        val rail = composeRule.onNodeWithContentDescription(context.getString(R.string.reading_progress))
        val bounds = rail.fetchSemanticsNode().boundsInRoot
        rail.performTouchInput {
            click(Offset(bounds.width * 0.50f, bounds.height * 0.50f))
        }
        composeRule.waitForIdle()
        assertTrue("direct progress scrub should seek near the middle, got $seekFraction", seekFraction in 0.45f..0.55f)
    }

    @Test fun readerProgressSetProgressSemanticsSeeksAccurately() {
        var seekFraction = -1f
        composeRule.setContent {
            readerState(
                noOpActions().copy(onSeekFraction = { seekFraction = it }),
            )
        }
        composeRule.waitForIdle()
        val rail = composeRule.onNodeWithContentDescription(context.getString(R.string.reading_progress))
        rail.performSemanticsAction(SemanticsActions.SetProgress) { action -> action(0.75f) }
        composeRule.waitForIdle()
        assertTrue("accessible setProgress should seek to 75%, got $seekFraction", seekFraction in 0.74f..0.76f)
    }

    @Composable
    private fun readerState(actions: JingduActions) = JingduApp(
        AppUiState(
            screen = AppScreen.READER,
            currentBook = sampleBook(),
            pageText = "Chapter 1\nA stable body used for direct and accessible progress rail verification.",
            position = 500,
            length = 10_000,
            chapters = listOf(ChapterModel(0, "Chapter 1"), ChapterModel(5_000, "Chapter 2")),
            chaptersLoaded = true,
            settings = ReaderSettings(gestureCoachDismissed = true),
        ),
        actions,
    )

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
