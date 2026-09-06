package com.junchen.jingdu

import java.util.Locale

/** Small bounded policy shared by paged layout and tests; UI locale never changes document identity. */
internal object ReaderCjkTypography {
    fun containsCjk(text: CharSequence): Boolean {
        val limit = minOf(text.length, SAMPLE_UTF16)
        var cursor = 0
        while (cursor < limit) {
            val cp = Character.codePointAt(text, cursor)
            if (cp in 0x3400..0x4DBF || cp in 0x4E00..0x9FFF || cp in 0xF900..0xFAFF) return true
            cursor += Character.charCount(cp)
        }
        return false
    }

    fun localeFor(text: CharSequence, mode: ChineseDisplayMode): Locale = when (mode) {
        ChineseDisplayMode.SIMPLIFIED -> Locale.forLanguageTag("zh-CN")
        ChineseDisplayMode.TRADITIONAL, ChineseDisplayMode.TAIWAN, ChineseDisplayMode.TAIWAN_PHRASES -> Locale.forLanguageTag("zh-TW")
        ChineseDisplayMode.HONG_KONG -> Locale.forLanguageTag("zh-HK")
        ChineseDisplayMode.ORIGINAL -> detectOriginalLocale(text)
    }

    private fun detectOriginalLocale(text: CharSequence): Locale {
        var hans = 0
        var hant = 0
        var hk = 0
        val limit = minOf(text.length, SAMPLE_UTF16)
        var cursor = 0
        while (cursor < limit) {
            val cp = Character.codePointAt(text, cursor)
            cursor += Character.charCount(cp)
            if (cp > Char.MAX_VALUE.code) continue
            val c = cp.toChar()
            if (HANS_MARKERS.indexOf(c) >= 0) hans++
            if (HANT_MARKERS.indexOf(c) >= 0) hant++
            if (HK_MARKERS.indexOf(c) >= 0) hk++
        }
        return when {
            hk >= 2 && hk >= hant / 3 -> Locale.forLanguageTag("zh-HK")
            hant > hans -> Locale.forLanguageTag("zh-TW")
            else -> Locale.forLanguageTag("zh-CN")
        }
    }

    private const val SAMPLE_UTF16 = 2048
    private const val HANS_MARKERS = "这为后发国书读时会里还进对从个们来说现学与体门见风东语网无龙边开长"
    private const val HANT_MARKERS = "這為後發國書讀時會裡還進對從個們來說現學與體門見風東語網無龍邊開長"
    private const val HK_MARKERS = "係嘅唔嗰佢哋冇喺咁啲嚟咗"
}
