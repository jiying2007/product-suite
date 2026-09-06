package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadingPresetsTest {
    @Test fun paperBookUsesIndentWithTightParagraphSpacing() {
        val value = ReaderSettings().applyProductPreset(ReaderPreset.COMFORT)
        assertEquals(2f, value.firstLineIndentEm)
        assertTrue(value.paragraphSpacingEm <= 0.2f)
        assertEquals(ReaderTypeface.SERIF, value.typeface)
    }

    @Test fun webNovelKeepsParagraphSpacingWithoutForcedIndent() {
        val value = ReaderSettings().applyProductPreset(ReaderPreset.STANDARD)
        assertEquals(0f, value.firstLineIndentEm)
        assertTrue(value.paragraphSpacingEm >= 0.4f)
    }

    @Test fun lowVisionKeepsLargeHeavyFocusedPresentation() {
        val value = ReaderSettings().applyProductPreset(ReaderPreset.LOW_VISION)
        assertTrue(value.fontSizeSp >= 34f)
        assertEquals(ReaderFontWeight.SEMIBOLD, value.fontWeight)
        assertEquals(5, value.focusRulerLines)
    }
}
