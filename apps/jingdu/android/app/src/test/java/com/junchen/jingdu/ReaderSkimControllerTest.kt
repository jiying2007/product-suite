package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSkimControllerTest {
    @Test
    fun previewAnchorAccountsForRemovedParagraphSpacers() {
        val spacer = ReaderTypographySpec.PARAGRAPH_SPACER
        val text = "A".repeat(50) + spacer + "B".repeat(60) + "TARGET" + "C".repeat(200)
        val sourceUtf = text.indexOf("TARGET")
        val clean = text.replace(spacer.toString(), "")
        val adjustedUtf = sourceUtf - 1
        val expected = clean.substring(
            (adjustedUtf - 90).coerceAtLeast(0),
            (adjustedUtf + 180).coerceAtMost(clean.length),
        ).replace(Regex("\\s+"), " ").trim()

        val actual = readerSkimPreviewAround(text, sourceUtf)

        assertEquals(expected, actual)
        assertTrue(actual.contains("TARGET"))
    }

    @Test
    fun previewKeepsSupplementaryCharactersWholeAtSliceBoundaries() {
        val text = "😀".repeat(80) + "目标" + "😀".repeat(140)
        val targetUtf = text.indexOf("目标")

        val preview = readerSkimPreviewAround(text, targetUtf)

        assertTrue(preview.contains("目标"))
        assertFalse(preview.isNotEmpty() && Character.isLowSurrogate(preview.first()))
        assertFalse(preview.isNotEmpty() && Character.isHighSurrogate(preview.last()))
    }
}
