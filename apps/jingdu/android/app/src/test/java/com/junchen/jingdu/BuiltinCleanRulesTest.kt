package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinCleanRulesTest {
    @Test
    fun pack4IsConservativeAndBilingual() {
        assertEquals(4, BuiltinCleanRules.PACK_VERSION)
        assertEquals(BuiltinCleanRules.rules.size, BuiltinCleanRules.rules.map { it.id }.distinct().size)
        assertTrue(BuiltinCleanRules.rules.all { it.confidence >= 90 })
        assertTrue(BuiltinCleanRules.rules.all { it.rule.replacement.isEmpty() })
        assertTrue(BuiltinCleanRules.rules.any { it.locale == "zh-Hans" && it.rule.find.contains("网址") })
        assertTrue(BuiltinCleanRules.rules.any { it.locale == "zh-Hant" && it.rule.find.contains("網址") })
        assertTrue(BuiltinCleanRules.rules.any { it.locale == "zh-Hans" && it.rule.find.contains("APP") })
        assertTrue(BuiltinCleanRules.rules.any { it.locale == "zh-Hant" && it.rule.find.contains("APP") })
    }
}
