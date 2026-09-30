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
}
