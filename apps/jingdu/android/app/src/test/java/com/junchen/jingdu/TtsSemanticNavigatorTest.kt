package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsSemanticNavigatorTest {
    @Test
    fun paragraphNavigationUsesCodePointOffsetsAndSkipsSeparatorWhitespace() {
        val text = "第一段🙂。\n\n   第二段。\n\n第三段。"
        val next = TtsSemanticNavigator.nextParagraphOffset(text)
        val expectedSecond = text.codePointCount(0, text.indexOf("第二段")).toLong()
        assertEquals(expectedSecond, next)

        val previous = TtsSemanticNavigator.previousParagraphOffset(text)
        val expectedThird = text.codePointCount(0, text.indexOf("第三段")).toLong()
        assertEquals(expectedThird, previous)
    }

    @Test
    fun sentenceNavigationIsBoundedAndUnicodeSafe() {
        val text = "Hello🙂 world. Second sentence! Third?"
        val next = TtsSemanticNavigator.nextSentenceOffset(text, Locale.US)
        assertTrue(next in 1 until text.codePointCount(0, text.length).toLong())
        val previous = TtsSemanticNavigator.previousSentenceOffset(text, Locale.US)
        assertTrue(previous in 0 until text.codePointCount(0, text.length).toLong())
    }

    @Test
    fun emptyTextHasStableZeroBoundaries() {
        assertEquals(0L, TtsSemanticNavigator.previousSentenceOffset("", Locale.US))
        assertEquals(0L, TtsSemanticNavigator.nextSentenceOffset("", Locale.US))
        assertEquals(0L, TtsSemanticNavigator.previousParagraphOffset(""))
        assertEquals(0L, TtsSemanticNavigator.nextParagraphOffset(""))
    }
}
