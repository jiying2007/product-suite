package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPronunciationStoreTest {
    @Test fun parserIsBoundedLiteralAndIgnoresInvalidRows() {
        val rules = TtsPronunciationStore.parse(
            """
            # local pronunciations
            单于 => chán yú
            长孙 => zhǎng sūn
            malformed
            单于 => duplicate ignored
            """.trimIndent(),
        )
        assertEquals(2, rules.size)
        assertEquals("单于", rules[0].source)
        assertEquals("chán yú", rules[0].spoken)
    }

    @Test fun lengthChangingPronunciationKeepsMonotonicSourceProjection() {
        val source = "单于站在城门外，长孙从远处走来。"
        val rules = listOf(
            TtsPronunciationRule("单于", "chán yú"),
            TtsPronunciationRule("长孙", "zhǎng sūn"),
        )
        var spoken = source
        rules.sortedByDescending { it.source.length }.forEach { spoken = spoken.replace(it.source, it.spoken) }
        val projection = TextProjection.between(source, spoken)
        var previous = 0L
        for (index in 0..spoken.codePointCount(0, spoken.length)) {
            val current = projection.sourceForDisplay(index.toLong())
            assertTrue(current >= previous)
            previous = current
        }
        assertEquals(source.codePointCount(0, source.length).toLong(), projection.sourceCodePoints)
    }

    @Test fun parserCapsRuleCount() {
        val raw = (0 until 140).joinToString("\n") { "词$it => pronunciation$it" }
        assertEquals(TtsPronunciationStore.MAX_RULES, TtsPronunciationStore.parse(raw).size)
    }
}
