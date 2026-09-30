package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMetadataSanitizerTest {
    @Test fun displayNameRemovesControlsAndBidiOverrides() {
        val value = TextMetadataSanitizer.displayName("  hello\n\u202Eevil.txt\u0000  ")
        assertEquals("hello evil.txt", value)
        assertFalse(value.contains('\n'))
        assertFalse(value.contains('\u202E'))
    }

    @Test fun displayNameBoundsCodePoints() {
        val value = TextMetadataSanitizer.displayName("书".repeat(500))
        assertEquals(TextMetadataSanitizer.MAX_DISPLAY_NAME_CODE_POINTS, value.codePointCount(0, value.length))
    }

    @Test fun displayNameFallsBackForOnlyUnsafeCharacters() {
        assertEquals("TXT", TextMetadataSanitizer.displayName("\u0000\n\u202E"))
    }

    @Test fun displayNameKeepsNormalUnicode() {
        val value = TextMetadataSanitizer.displayName("  第十二章 夜雨.txt  ")
        assertTrue(value.startsWith("第十二章"))
        assertTrue(value.endsWith(".txt"))
    }
}
