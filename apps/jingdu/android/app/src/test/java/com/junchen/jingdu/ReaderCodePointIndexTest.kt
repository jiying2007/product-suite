package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderCodePointIndexTest {
    @Test
    fun slicesAsciiWithoutPrefixRescanSemanticsChanging() {
        val text = buildString { repeat(2048) { append(('a'.code + (it % 26)).toChar()) } }
        val index = ReaderCodePointIndex.build(text, stride = 32)

        assertEquals(text.substring(777, 999), index.slice(777, 222))
        assertEquals(text.length, index.codePointCount)
    }

    @Test
    fun preservesSurrogatePairsAcrossAnchorBoundaries() {
        val text = buildString {
            repeat(80) { position ->
                append(if (position % 3 == 0) "😀" else "字")
            }
        }
        val index = ReaderCodePointIndex.build(text, stride = 7)
        val start = 19
        val count = 31
        val expectedStart = text.offsetByCodePoints(0, start)
        val expectedEnd = text.offsetByCodePoints(expectedStart, count)

        assertEquals(text.substring(expectedStart, expectedEnd), index.slice(start, count))
        assertEquals(text.codePointCount(0, text.length), index.codePointCount)
    }

    @Test
    fun clampsSliceAtWindowEnd() {
        val text = "甲乙😀丙丁"
        val index = ReaderCodePointIndex.build(text, stride = 2)

        assertEquals("😀丙丁", index.slice(2, 99))
        assertEquals("", index.slice(index.codePointCount, 1))
    }
}
