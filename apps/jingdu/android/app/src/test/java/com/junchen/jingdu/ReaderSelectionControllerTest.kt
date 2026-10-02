package com.junchen.jingdu

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Locale

class ReaderSelectionControllerTest {
    @Test
    fun directPagedSelectionMetadataPreservesDeletionProjection() {
        val source = "abXXXcd"
        val display = "abcd"
        val map = SourceDisplayMap.between(source, display)
        val annotated = ReaderSelectionController.annotatedForSelection(
            sourceBase = 100L,
            displayText = AnnotatedString(display),
            map = map,
        )

        val selected = annotated.subSequence(2, 4)
        val range = ReaderSelectionController.fromSelectedTexts(listOf(selected))
        assertNotNull(range)
        assertEquals(105L, range!!.sourceStart)
        assertEquals(107L, range.sourceEnd)
        assertEquals("cd", range.excerpt)
    }

    @Test
    fun continuousNativeLongPressMapsWordToExactSourceRange() {
        val display = "hello world"
        val map = SourceDisplayMap.between(display, display)
        val range = ReaderSelectionController.wordAt(
            sourceBase = 100L,
            displayText = display,
            displayUtf16 = 8,
            map = map,
            locale = Locale.ENGLISH,
        )
        assertNotNull(range)
        assertEquals(106L, range!!.sourceStart)
        assertEquals(111L, range.sourceEnd)
        assertEquals("world", range.excerpt)
    }

    @Test
    fun directPagedSelectionMetadataPreservesSupplementaryCodePointBoundaries() {
        val display = "A🙂B"
        val map = SourceDisplayMap.between(display, display)
        val annotated = ReaderSelectionController.annotatedForSelection(
            sourceBase = 50L,
            displayText = AnnotatedString(display),
            map = map,
        )

        val selected = annotated.subSequence(1, 3)
        val range = ReaderSelectionController.fromSelectedTexts(listOf(selected))
        assertNotNull(range)
        assertEquals(51L, range!!.sourceStart)
        assertEquals(52L, range.sourceEnd)
        assertEquals("🙂", range.excerpt)
    }
}
