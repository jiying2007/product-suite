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
        assertEquals(true, ttsRuntimeErrorRetryable("tts error: engine unavailable"))
        assertEquals(false, ttsRuntimeErrorRetryable("tts error: no compatible voice"))
        assertEquals(false, ttsRuntimeErrorRetryable("tts error: no offline voice"))
        assertEquals(false, ttsRuntimeErrorRetryable("audio focus denied"))
    }

    @Test fun queuedNextOffsetAcceptsOnlyCurrentChunk() {
        assertEquals(180L, ttsQueuedNextOffset(100L, 100L, 180L))
        assertEquals(null, ttsQueuedNextOffset(100L, 80L, 160L))
        assertEquals(null, ttsQueuedNextOffset(100L, 100L, 99L))
    }

    @Test fun engineRecoveryFallsBackFromBrokenPreferredEngineButDoesNotLoopOnMissingDefaultOfflineVoice() {
        assertEquals(true, ttsEngineRecoveryRecommended("TTS engine not ready", hasPreferredEngine = true))
        assertEquals(true, ttsEngineRecoveryRecommended("TTS engine not ready", hasPreferredEngine = false))
        assertEquals(true, ttsEngineRecoveryRecommended("tts error: speak failed", hasPreferredEngine = false))
        assertEquals(true, ttsEngineRecoveryRecommended("tts error: no offline voice", hasPreferredEngine = true))
        assertEquals(false, ttsEngineRecoveryRecommended("tts error: no offline voice", hasPreferredEngine = false))
        assertEquals(false, ttsEngineRecoveryRecommended("audio focus denied", hasPreferredEngine = true))
    }

}
