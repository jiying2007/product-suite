package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

class ReaderFoundationsTest {
    @Test
    fun tocCurrentIndexUsesNearestChapterAtOrBeforePosition() {
        val chapters = listOf(
            SmartChapter(0L, "One", "core", 100),
            SmartChapter(100L, "Two", "core", 100),
            SmartChapter(250L, "Three", "core", 100),
        )
        assertEquals(0, readerTocCurrentIndex(emptyList(), 999L))
        assertEquals(0, readerTocCurrentIndex(chapters, 99L))
        assertEquals(1, readerTocCurrentIndex(chapters, 100L))
        assertEquals(1, readerTocCurrentIndex(chapters, 249L))
        assertEquals(2, readerTocCurrentIndex(chapters, 999L))
    }

    @Test fun readerChromeAutoHideWaitsForRenderedContentAndPausesDuringActiveChromeWork() {
        assertEquals(false, readerChromeCanAutoHide(false, true, false, false, false, false))
        assertEquals(true, readerChromeCanAutoHide(true, true, false, false, false, false))
        assertEquals(false, readerChromeCanAutoHide(true, true, true, false, false, false))
        assertEquals(false, readerChromeCanAutoHide(true, true, false, true, false, false))
        assertEquals(false, readerChromeCanAutoHide(true, true, false, false, true, false))
        assertEquals(false, readerChromeCanAutoHide(true, true, false, false, false, true))
        assertEquals(false, readerChromeCanAutoHide(true, false, false, false, false, false))
    }

    @Test fun readerChromeRuntimeBlocksPanelsAndWakesOnCloseWithoutHotPanelSubscription() {
        var wakes = 0
        val wake = { wakes++; Unit }
        ReaderChromeRuntime.bindWakeRequest(wake)
        ReaderChromeRuntime.markPanelOpen()
        assertEquals(true, ReaderChromeRuntime.panelOpen)
        assertEquals(false, readerChromeCanAutoHide(true, true, ReaderChromeRuntime.panelOpen, false, false, false))
        ReaderChromeRuntime.markPanelClosedAndWake()
        assertEquals(false, ReaderChromeRuntime.panelOpen)
        assertEquals(1, wakes)
        ReaderChromeRuntime.unbindWakeRequest(wake)
    }

    @Test fun lastEnabledTouchPagingPathCannotBeDisabledInSettings() {
        assertEquals(false, canDisablePagingPath(currentEnabled = true, otherEnabled = false))
        assertEquals(true, canDisablePagingPath(currentEnabled = true, otherEnabled = true))
        assertEquals(true, canDisablePagingPath(currentEnabled = false, otherEnabled = false))
    }

    @Test fun equalLengthProjectionIsExactOneToOne() {
        val map = SourceDisplayMap.between("汉语龙门", "漢語龍門")
        for (offset in 0L..4L) {
            assertEquals(offset, map.displayForSource(offset))
            assertEquals(offset, map.sourceForDisplay(offset))
        }
    }

    @Test fun localizedDeletionDoesNotScaleUnchangedSuffix() {
        val map = SourceDisplayMap.between("abcXXXdef", "abcdef")
        assertEquals(0L, map.displayForSource(0))
        assertEquals(3L, map.displayForSource(3))
        assertEquals(3L, map.displayForSource(6))
        assertEquals(6L, map.displayForSource(9))
        assertEquals(9L, map.sourceForDisplay(6))
    }

    @Test fun projectionCompositionKeepsBoundariesMonotonic() {
        val first = TextProjection.between("a\n\n\n尾巴", "a\n\n尾巴")
        val second = TextProjection.between("a\n\n尾巴", "a\n\n尾巴")
        val map = SourceDisplayMap.compose(first, second)
        var last = 0L
        for (source in 0L..map.sourceCodePoints) {
            val display = map.displayForSource(source)
            assertTrue(display >= last)
            last = display
        }
    }

    @Test fun randomizedProjectionSoakRemainsBoundedAndMonotonic() {
        val random = Random(0x5EED_C0DE)
        val alphabet = intArrayOf('a'.code, 'b'.code, '中'.code, '文'.code, '。'.code, '\n'.code, 0x1F642)
        repeat(5_000) {
            val sourcePoints = IntArray(random.nextInt(1, 96)) { alphabet[random.nextInt(alphabet.size)] }.toMutableList()
            val displayPoints = sourcePoints.toMutableList()
            repeat(random.nextInt(1, 6)) {
                when (random.nextInt(3)) {
                    0 -> if (displayPoints.isNotEmpty()) displayPoints.removeAt(random.nextInt(displayPoints.size))
                    1 -> displayPoints.add(random.nextInt(displayPoints.size + 1), alphabet[random.nextInt(alphabet.size)])
                    else -> if (displayPoints.isNotEmpty()) displayPoints[random.nextInt(displayPoints.size)] = alphabet[random.nextInt(alphabet.size)]
                }
            }
            val source = String(sourcePoints.toIntArray(), 0, sourcePoints.size)
            val display = String(displayPoints.toIntArray(), 0, displayPoints.size)
            val map = SourceDisplayMap.between(source, display)

            // Leading insertions/deletions legitimately make the reverse boundary at zero ambiguous.
            // The document-end boundary remains exact and both projections must stay bounded/monotonic.
            assertEquals(displayPoints.size.toLong(), map.displayForSource(sourcePoints.size.toLong()))
            assertEquals(sourcePoints.size.toLong(), map.sourceForDisplay(displayPoints.size.toLong()))

            var lastDisplay = -1L
            for (sourceOffset in 0L..sourcePoints.size.toLong()) {
                val mapped = map.displayForSource(sourceOffset)
                assertTrue(mapped in 0L..displayPoints.size.toLong())
                assertTrue(mapped >= lastDisplay)
                lastDisplay = mapped
            }
            var lastSource = -1L
            for (displayOffset in 0L..displayPoints.size.toLong()) {
                val mapped = map.sourceForDisplay(displayOffset)
                assertTrue(mapped in 0L..sourcePoints.size.toLong())
                assertTrue(mapped >= lastSource)
                lastSource = mapped
            }
        }
    }

    @Test fun semanticTtsNavigationPureCoreSoakIsBounded() {
        val samples = listOf(
            "第一句。第二句！第三句？\n\n下一段。",
            "One sentence. Second sentence! Third?\n\nNext paragraph.",
            "混合 text one. 第二句。\r\n\r\n尾段。",
        )
        val locales = listOf(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.ENGLISH)
        repeat(20_000) { index ->
            val text = samples[index % samples.size]
            val locale = locales[index % locales.size]
            val total = text.codePointCount(0, text.length).toLong()
            val previousSentence = TtsSemanticNavigator.previousSentenceOffset(text, locale)
            val nextSentence = TtsSemanticNavigator.nextSentenceOffset(text, locale)
            val previousParagraph = TtsSemanticNavigator.previousParagraphOffset(text)
            val nextParagraph = TtsSemanticNavigator.nextParagraphOffset(text)
            assertTrue(previousSentence in 0L..total)
            assertTrue(nextSentence in 0L..total)
            assertTrue(previousParagraph in 0L..total)
            assertTrue(nextParagraph in 0L..total)
            assertTrue(nextSentence > 0L)
            assertTrue(nextParagraph > 0L)
        }
    }

    @Test fun wordSelectionMapsThroughProjection() {
        val source = "hello XXX world"
        val display = "hello world"
        val map = SourceDisplayMap.between(source, display)
        assertEquals(10L, map.sourceForDisplay(display.indexOf("world").toLong()))
        val selected = ReaderSelectionController.wordAt(
            sourceBase = 100,
            displayText = display,
            displayUtf16 = display.indexOf("world") + 1,
            map = map,
            locale = Locale.ENGLISH,
        ) ?: error("selection missing")
        assertEquals("world", selected.excerpt)
        assertEquals(110L, selected.sourceStart)
        assertEquals(115L, selected.sourceEnd)
    }

    @Test fun typographyFingerprintCoversPaginationInputs() {
        val base = ReaderSettings()
        val fingerprint = ReaderTypographySpec.from(base).fingerprint
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(fontSizeSp = 24f)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(fontWeight = ReaderFontWeight.SEMIBOLD)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(firstLineIndentEm = 2f)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(paragraphSpacingEm = 1f)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(textAlignment = ReaderTextAlignment.START)).fingerprint)
        assertNotEquals(fingerprint, ReaderTypographySpec.from(base.copy(typeface = ReaderTypeface.SERIF)).fingerprint)
    }

    @Test fun readingHistoryBucketsByLocalCivilDay() {
        val instant = Instant.parse("2026-08-30T16:30:00Z")
        val singapore = ZoneId.of("Asia/Singapore")
        val utc = ZoneId.of("UTC")
        assertEquals(LocalDate.of(2026, 8, 31).toEpochDay(), readerDayEpoch(instant.toEpochMilli(), singapore))
        assertEquals(LocalDate.of(2026, 8, 30).toEpochDay(), readerDayEpoch(instant.toEpochMilli(), utc))
        assertNotEquals(readerDayEpoch(instant.toEpochMilli(), singapore), readerDayEpoch(instant.toEpochMilli(), utc))
    }

    @Test fun pagedModeCannotDisableEveryTouchPagingPath() {
    val repaired = ReaderSettings(
        readingMode = ReaderMode.PAGED,
        tapPagingEnabled = false,
        swipePagingEnabled = false,
    ).withReachablePagedNavigation()
    assertTrue(repaired.tapPagingEnabled)
    assertTrue(!repaired.swipePagingEnabled)

    val continuous = ReaderSettings(
        readingMode = ReaderMode.CONTINUOUS,
        tapPagingEnabled = false,
        swipePagingEnabled = false,
    ).withReachablePagedNavigation()
    assertTrue(!continuous.tapPagingEnabled)
    assertTrue(!continuous.swipePagingEnabled)
}

    @Test fun lowVisionPresetIsLegibleAndFocused() {
        val lowVision = ReaderSettings().applyPreset(ReaderPreset.LOW_VISION)
        assertTrue(lowVision.fontSizeSp >= 30f)
        assertTrue(lowVision.lineHeightMultiplier >= 1.7f)
        assertEquals(ReaderTextAlignment.START, lowVision.textAlignment)
        assertTrue(lowVision.focusRulerLines >= 3)
    }
}
