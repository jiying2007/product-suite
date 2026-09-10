package com.junchen.posestudio.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileNameTest {
    @Test
    fun preservesChineseProjectName() {
        assertEquals("角色姿势一.png", exportFileName("角色姿势一", "png"))
    }

    @Test
    fun replacesUnsafePathAndControlCharacters() {
        val name = exportFileName("  pose/a:b*?\nref  ", "pose.json")
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
        assertFalse(name.contains('*'))
        assertFalse(name.contains('?'))
        assertFalse(name.contains('\n'))
        assertTrue(name.endsWith(".pose.json"))
    }

    @Test
    fun blankNameFallsBackToPose() {
        assertEquals("pose.png", exportFileName("   ", "png"))
    }

    @Test
    fun truncatesByCodePointWithoutSplittingEmoji() {
        val fileName = exportFileName("😀".repeat(60), "png")
        val stem = fileName.removeSuffix(".png")
        assertEquals(48, stem.codePointCount(0, stem.length))
        assertEquals("😀".repeat(48), stem)
    }
}
