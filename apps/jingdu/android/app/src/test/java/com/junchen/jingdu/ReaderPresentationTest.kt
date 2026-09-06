package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPresentationTest {
    @Test fun chapterTitleUsesSameChinesePresentationAsReaderText() {
        val settings = ReaderSettings(chineseMode = ChineseDisplayMode.TRADITIONAL)
        val source = "这本书的第一章"
        assertEquals(ReaderTextPresentation.display(source, settings), ReaderTextPresentation.chapterTitle(source, settings))
        assertTrue(ReaderTextPresentation.chapterTitle(source, settings).contains("這"))
    }

    @Test fun lengthChangingOverrideMapsSpokenRangeBackToSource() {
        val source = "重庆欢迎你"
        val presented = ReaderTextPresentation.present(source, ChineseDisplayMode.TRADITIONAL, "重庆=>重慶市")
        val mapped = ReaderTextPresentation.sourceRangeForDisplayUtf16(presented.displayText, presented.projection, 0, presented.displayText.length)
        assertEquals(0L, mapped.first)
        assertTrue(mapped.last < source.codePointCount(0, source.length))
    }
}
