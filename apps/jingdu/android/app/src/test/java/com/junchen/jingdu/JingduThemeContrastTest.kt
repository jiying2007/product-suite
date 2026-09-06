package com.junchen.jingdu

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class JingduThemeContrastTest {
    @Test
    fun nightRolesRemainReadable() {
        assertReadable(ReaderPalette.NIGHT)
    }

    @Test
    fun oledRolesRemainReadable() {
        assertReadable(ReaderPalette.OLED)
    }

    private fun assertReadable(palette: ReaderPalette) {
        val scheme = jingduColorScheme(palette)
        assertContrast("onSurface", scheme.onSurface, scheme.surface, 4.5)
        assertContrast("onSurfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5)
        assertContrast("onBackground", scheme.onBackground, scheme.background, 4.5)
        assertContrast("primary/onPrimary", scheme.primary, scheme.onPrimary, 4.5)
        assertContrast("secondary/onSecondary", scheme.secondary, scheme.onSecondary, 4.5)
        assertContrast("outline", scheme.outline, scheme.surface, 3.0)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue("$name contrast=$ratio < $minimum", ratio >= minimum)
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red.toDouble()) +
            0.7152 * linear(color.green.toDouble()) +
            0.0722 * linear(color.blue.toDouble())

    private fun linear(value: Double): Double =
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}
