package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsRuntimePolicyTest {
    @Test fun utteranceGenerationRejectsPreviewAndStaleCallbacks() {
        assertEquals(42L, ttsUtteranceGeneration("42"))
        assertEquals(-1L, ttsUtteranceGeneration("preview-123"))
        assertEquals(-1L, ttsUtteranceGeneration(null))
    }
}
