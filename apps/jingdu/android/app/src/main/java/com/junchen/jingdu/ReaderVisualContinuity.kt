package com.junchen.jingdu

/** Pure source-coordinate policy for keeping the reader's visual center stable across reflow. */
internal object ReaderVisualContinuity {
    fun layoutKey(settings: ReaderSettings): Int = listOf(
        settings.typeface.name,
        settings.customFontId,
        settings.fontSizeSp,
        settings.lineHeightMultiplier,
        settings.letterSpacingEm,
        settings.paragraphSpacingEm,
        settings.horizontalPaddingDp,
        settings.verticalPaddingDp,
        settings.firstLineIndentEm,
        settings.textAlignment.name,
        settings.fontWeight.name,
        settings.compressBlankLines,
        settings.emphasizeHeadings,
        settings.readingMode.name,
        settings.wideColumns.name,
        settings.orientation.name,
        settings.chineseMode.name,
        settings.chineseOverrides.hashCode(),
    ).hashCode()

    fun centerAnchor(position: Long, visibleChars: Long, length: Long): Long {
        if (length <= 0) return 0
        return (position + visibleChars.coerceAtLeast(0) / 2).coerceIn(0, length - 1)
    }

    fun topForCenter(anchor: Long, visibleChars: Long, length: Long): Long {
        if (length <= 0) return 0
        return (anchor - visibleChars.coerceAtLeast(0) / 2).coerceIn(0, length - 1)
    }
}
