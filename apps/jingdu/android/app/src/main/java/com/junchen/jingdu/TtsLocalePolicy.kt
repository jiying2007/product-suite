package com.junchen.jingdu

import java.util.Locale

/**
 * Chooses a TTS locale without assuming that every engine ships every Chinese regional voice.
 * Converted text can still be spoken by another installed Chinese locale when the preferred
 * zh-TW/zh-HK voice is unavailable.
 */
internal object TtsLocalePolicy {
    fun candidates(
        mode: ChineseDisplayMode,
        documentLocale: Locale,
        systemLocale: Locale = Locale.getDefault(),
    ): List<Locale> {
        val preferred = when (mode) {
            ChineseDisplayMode.SIMPLIFIED -> Locale.forLanguageTag("zh-CN")
            ChineseDisplayMode.TRADITIONAL,
            ChineseDisplayMode.TAIWAN,
            ChineseDisplayMode.TAIWAN_PHRASES -> Locale.forLanguageTag("zh-TW")
            ChineseDisplayMode.HONG_KONG -> Locale.forLanguageTag("zh-HK")
            ChineseDisplayMode.ORIGINAL -> documentLocale
        }
        val convertedChinese = mode != ChineseDisplayMode.ORIGINAL
        val values = buildList {
            add(preferred)
            if (documentLocale.language.equals("zh", ignoreCase = true)) add(documentLocale)
            if (preferred.language.equals("zh", ignoreCase = true)) {
                add(Locale.forLanguageTag("zh-CN"))
                add(Locale.forLanguageTag("zh-TW"))
                add(Locale.forLanguageTag("zh-HK"))
            }
            if (!convertedChinese || systemLocale.language.equals("zh", ignoreCase = true)) add(systemLocale)
        }
        return values.distinctBy { it.toLanguageTag().lowercase(Locale.ROOT) }
    }

    fun acceptsSavedVoice(mode: ChineseDisplayMode, voiceLocale: Locale): Boolean = when (mode) {
        ChineseDisplayMode.ORIGINAL -> true
        ChineseDisplayMode.SIMPLIFIED,
        ChineseDisplayMode.TRADITIONAL,
        ChineseDisplayMode.TAIWAN,
        ChineseDisplayMode.TAIWAN_PHRASES,
        ChineseDisplayMode.HONG_KONG -> voiceLocale.language.equals("zh", ignoreCase = true)
    }

    fun choose(
        mode: ChineseDisplayMode,
        documentLocale: Locale,
        systemLocale: Locale = Locale.getDefault(),
        isSupported: (Locale) -> Boolean,
    ): Locale? = candidates(mode, documentLocale, systemLocale).firstOrNull(isSupported)
}
