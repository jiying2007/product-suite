package com.junchen.jingdu

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContinuousWindowRegressionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun continuousReaderAdvancesAcrossMultipleBoundedWindows() {
        val book = ReaderInstrumentationFixture.book(context)
        val settings = ReaderSettings(readingMode = ReaderMode.CONTINUOUS)
        ReaderViewportEngine(context, book.id).use { engine ->
            val first = engine.readAround(0L, settings)
            assertTrue(first.documentLength > 8_192L)
            val middle = engine.readAround(first.documentLength / 2L, settings)
            val later = engine.readAround((first.documentLength * 3L) / 4L, settings)

            assertTrue("middle window must advance", middle.start > first.start)
            assertTrue("later window must advance", later.start > middle.start)

            listOf(first, middle, later).forEach { window ->
                val sourceCodePoints = window.sourceText.codePointCount(0, window.sourceText.length).toLong()
                assertTrue("continuous source window must remain bounded", sourceCodePoints <= 4_096L)
                assertEquals(sourceCodePoints, window.map.sourceCodePoints)
                assertEquals(sourceCodePoints, window.map.sourceForDisplay(window.map.displayCodePoints))
            }
        }
    }

    @Test
    fun continuousHandoffsKeepExactOverlappingSourceText() {
        val book = ReaderInstrumentationFixture.book(context)
        val settings = ReaderSettings(readingMode = ReaderMode.CONTINUOUS)
        ReaderViewportEngine(context, book.id).use { engine ->
            val first = engine.readAround(8_000L, settings)
            val nextTarget = (first.start + first.map.sourceCodePoints - 256L)
                .coerceAtMost(first.documentLength - 1)
            val second = engine.readAround(nextTarget, settings)

            assertTrue("handoff must advance to a later bounded window", second.start > first.start)
            val overlapStart = maxOf(first.start, second.start)
            val overlapEnd = minOf(
                first.start + first.sourceText.codePointCount(0, first.sourceText.length),
                second.start + second.sourceText.codePointCount(0, second.sourceText.length),
            )
            assertTrue("continuous windows must overlap at handoff", overlapEnd > overlapStart)

            fun slice(window: ReaderDisplayWindow): String {
                val fromPoints = (overlapStart - window.start).toInt()
                val toPoints = (overlapEnd - window.start).toInt()
                val from = window.sourceText.offsetByCodePoints(0, fromPoints)
                val to = window.sourceText.offsetByCodePoints(0, toPoints)
                return window.sourceText.substring(from, to)
            }
            assertEquals("overlapping source must be byte-for-byte/code-point identical", slice(first), slice(second))
        }
    }
}
