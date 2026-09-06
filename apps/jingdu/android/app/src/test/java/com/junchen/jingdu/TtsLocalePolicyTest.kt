package com.junchen.jingdu

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsLocalePolicyTest {
    @Test
    fun traditionalPrefersTaiwanWhenInstalled() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.TRADITIONAL,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { it.toLanguageTag() in setOf("zh-TW", "zh-CN") }

        assertEquals("zh-TW", selected?.toLanguageTag())
    }

    @Test
    fun traditionalFallsBackToInstalledChineseVoice() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.TRADITIONAL,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { it.toLanguageTag() == "zh-CN" }

        assertEquals("zh-CN", selected?.toLanguageTag())
    }

    @Test
    fun hongKongFallsBackWithoutChangingConvertedTextLocalePolicy() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.HONG_KONG,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { it.toLanguageTag() == "zh-TW" }

        assertEquals("zh-TW", selected?.toLanguageTag())
    }

    @Test
    fun convertedChineseNeverFallsBackToNonChineseSystemVoice() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.TRADITIONAL,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { it == Locale.ENGLISH }

        assertNull(selected)
    }

    @Test
    fun convertedChineseRejectsSavedNonChineseVoice() {
        assertFalse(TtsLocalePolicy.acceptsSavedVoice(ChineseDisplayMode.TRADITIONAL, Locale.ENGLISH))
        assertFalse(TtsLocalePolicy.acceptsSavedVoice(ChineseDisplayMode.SIMPLIFIED, Locale.JAPANESE))
        assertTrue(TtsLocalePolicy.acceptsSavedVoice(ChineseDisplayMode.HONG_KONG, Locale.forLanguageTag("zh-CN")))
    }

    @Test
    fun originalAllowsExplicitSavedVoice() {
        assertTrue(TtsLocalePolicy.acceptsSavedVoice(ChineseDisplayMode.ORIGINAL, Locale.ENGLISH))
    }

    @Test
    fun originalKeepsDetectedDocumentLocale() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.ORIGINAL,
            documentLocale = Locale.ENGLISH,
            systemLocale = Locale.forLanguageTag("zh-CN"),
        ) { true }

        assertEquals(Locale.ENGLISH, selected)
    }

    @Test
    fun noSupportedVoiceReturnsNull() {
        val selected = TtsLocalePolicy.choose(
            mode = ChineseDisplayMode.TRADITIONAL,
            documentLocale = Locale.forLanguageTag("zh-CN"),
            systemLocale = Locale.ENGLISH,
        ) { false }

        assertNull(selected)
    }
}
