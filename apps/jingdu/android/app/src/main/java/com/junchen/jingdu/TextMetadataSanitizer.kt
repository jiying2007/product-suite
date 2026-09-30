package com.junchen.jingdu

import java.text.Normalizer

/** Bounds provider-controlled display metadata before it is persisted or rendered. */
internal object TextMetadataSanitizer {
    const val MAX_DISPLAY_NAME_CODE_POINTS = 240

    fun displayName(raw: String?, fallback: String = "TXT"): String {
        val normalized = Normalizer.normalize(raw.orEmpty(), Normalizer.Form.NFC)
        val cleaned = buildString(normalized.length.coerceAtMost(512)) {
            var pendingSpace = false
            var offset = 0
            var count = 0
            while (offset < normalized.length && count < MAX_DISPLAY_NAME_CODE_POINTS) {
                val cp = normalized.codePointAt(offset)
                offset += Character.charCount(cp)
                if (isUnsafe(cp)) continue
                if (Character.isWhitespace(cp)) {
                    pendingSpace = isNotEmpty()
                    continue
                }
                if (pendingSpace) {
                    append(' ')
                    pendingSpace = false
                }
                appendCodePoint(cp)
                count++
            }
        }.trim().trim('.')
        return cleaned.ifBlank { fallback }
    }

    private fun isUnsafe(codePoint: Int): Boolean =
        Character.isISOControl(codePoint) || codePoint in BIDI_CONTROLS

    private val BIDI_CONTROLS = setOf(
        0x061C, 0x200E, 0x200F,
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069,
    )
}
