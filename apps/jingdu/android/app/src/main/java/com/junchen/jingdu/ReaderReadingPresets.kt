package com.junchen.jingdu

/** User-facing reading scenarios. Persisted enum values stay stable; only the tuned bundles evolve. */
internal fun ReaderSettings.applyProductPreset(value: ReaderPreset): ReaderSettings = when (value) {
    ReaderPreset.STANDARD -> copy(
        preset = value, palette = ReaderPalette.PAPER, typeface = ReaderTypeface.SYSTEM,
        fontSizeSp = 20f, lineHeightMultiplier = 1.55f, letterSpacingEm = 0f,
        paragraphSpacingEm = 0.45f, horizontalPaddingDp = 24f, verticalPaddingDp = 18f,
        firstLineIndentEm = 0f, textAlignment = ReaderTextAlignment.JUSTIFY,
        fontWeight = ReaderFontWeight.NORMAL, extraDim = 0f, activeThemeId = "",
    )
    // Paper-book typography uses a real first-line indent and deliberately small paragraph gaps;
    // combining a 2-em indent with large web-style spacing creates an unnecessarily loose page.
    ReaderPreset.COMFORT -> copy(
        preset = value, palette = ReaderPalette.PAPER, typeface = ReaderTypeface.SERIF,
        fontSizeSp = 21f, lineHeightMultiplier = 1.62f, letterSpacingEm = 0.01f,
        paragraphSpacingEm = 0.12f, horizontalPaddingDp = 28f, verticalPaddingDp = 22f,
        firstLineIndentEm = 2f, textAlignment = ReaderTextAlignment.JUSTIFY,
        fontWeight = ReaderFontWeight.NORMAL, extraDim = 0f, activeThemeId = "",
    )
    ReaderPreset.LARGE -> copy(
        preset = value, palette = ReaderPalette.LIGHT, typeface = ReaderTypeface.SYSTEM,
        fontSizeSp = 28f, lineHeightMultiplier = 1.72f, letterSpacingEm = 0.01f,
        paragraphSpacingEm = 0.65f, horizontalPaddingDp = 30f, verticalPaddingDp = 24f,
        firstLineIndentEm = 1f, textAlignment = ReaderTextAlignment.START,
        fontWeight = ReaderFontWeight.MEDIUM, extraDim = 0f, activeThemeId = "",
    )
    ReaderPreset.NIGHT -> copy(
        preset = value, palette = ReaderPalette.NIGHT, typeface = ReaderTypeface.SERIF,
        fontSizeSp = 21f, lineHeightMultiplier = 1.62f, letterSpacingEm = 0.01f,
        paragraphSpacingEm = 0.18f, horizontalPaddingDp = 26f, verticalPaddingDp = 20f,
        firstLineIndentEm = 2f, textAlignment = ReaderTextAlignment.JUSTIFY,
        fontWeight = ReaderFontWeight.NORMAL, extraDim = 0.08f, activeThemeId = "",
    )
    ReaderPreset.LOW_VISION -> copy(
        preset = value, palette = ReaderPalette.LIGHT, typeface = ReaderTypeface.SYSTEM,
        fontSizeSp = 34f, lineHeightMultiplier = 1.85f, letterSpacingEm = 0.035f,
        paragraphSpacingEm = 0.8f, horizontalPaddingDp = 32f, verticalPaddingDp = 28f,
        firstLineIndentEm = 0f, textAlignment = ReaderTextAlignment.START,
        fontWeight = ReaderFontWeight.SEMIBOLD, focusRulerLines = 5, extraDim = 0f,
        activeThemeId = "",
    )
    ReaderPreset.CUSTOM -> copy(preset = value)
}
