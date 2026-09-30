package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsRuntimePolicyTest {
    @Test fun utteranceGenerationRejectsPreviewAndStaleCallbacks() {
        assertEquals(42L, ttsUtteranceGeneration("42"))
        assertEquals(-1L, ttsUtteranceGeneration("preview-123"))
        assertEquals(-1L, ttsUtteranceGeneration(null))
    }

    @Test fun runtimeRetryPolicyDistinguishesTransientFromConfigurationErrors() {
        assertEquals(true, ttsRuntimeErrorRetryable("tts error: speak failed"))
        assertEquals(true, ttsRuntimeErrorRetryable("tts error: -4"))
        assertEquals(true, ttsRuntimeErrorRetryable("tts error: -5"))
        assertEquals(false, ttsRuntimeErrorRetryable("tts error: no compatible voice"))
        assertEquals(false, ttsRuntimeErrorRetryable("audio focus denied"))
    }
}
