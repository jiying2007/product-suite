package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCjkTypographyTest {
    @Test fun detectsCjkWithoutTreatingEnglishAsCjk() {
        assertTrue(ReaderCjkTypography.containsCjk("第一章 夜雨落在旧城。"))
        assertFalse(ReaderCjkTypography.containsCjk("Chapter 1 A quiet night."))
    }

    @Test fun explicitDisplayModeOwnsLayoutLocale() {
        assertEquals("zh-CN", ReaderCjkTypography.localeFor("繁體文字", ChineseDisplayMode.SIMPLIFIED).toLanguageTag())
        assertEquals("zh-TW", ReaderCjkTypography.localeFor("简体文字", ChineseDisplayMode.TAIWAN_PHRASES).toLanguageTag())
        assertEquals("zh-HK", ReaderCjkTypography.localeFor("簡體文字", ChineseDisplayMode.HONG_KONG).toLanguageTag())
    }

    @Test fun originalModeDistinguishesCommonTraditionalAndHongKongSignals() {
        assertEquals("zh-TW", ReaderCjkTypography.localeFor("這本書會在後面繼續說明閱讀方式", ChineseDisplayMode.ORIGINAL).toLanguageTag())
        assertEquals("zh-HK", ReaderCjkTypography.localeFor("佢哋喺呢度係唔會咁做嘅", ChineseDisplayMode.ORIGINAL).toLanguageTag())
    }
}
