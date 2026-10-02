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
        assertEquals(false, ttsRuntimeErrorRetryable("tts error: no offline voice"))
        assertEquals(false, ttsRuntimeErrorRetryable("audio focus denied"))
    }

    @Test fun queuedNextOffsetAcceptsOnlyCurrentChunk() {
        assertEquals(180L, ttsQueuedNextOffset(100L, 100L, 180L))
        assertEquals(null, ttsQueuedNextOffset(100L, 80L, 160L))
        assertEquals(null, ttsQueuedNextOffset(100L, 100L, 99L))
    }

}
