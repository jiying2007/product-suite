package com.junchen.jingdu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadingSessionRuntimeTest {
    @Test fun cleanPreviewRuntimeCanEnterAndExitWithoutLeakingState() {
        try {
            ReaderReadingSessionRuntime.publishCleanPreview(false)
            assertFalse(ReaderReadingSessionRuntime.cleanPreviewActive)

            ReaderReadingSessionRuntime.publishCleanPreview(true)
            assertTrue(ReaderReadingSessionRuntime.cleanPreviewActive)

            ReaderReadingSessionRuntime.publishCleanPreview(false)
            assertFalse(ReaderReadingSessionRuntime.cleanPreviewActive)
        } finally {
            ReaderReadingSessionRuntime.publishCleanPreview(false)
        }
    }

    @Test fun newReaderSessionClearsStalePreviewRuntimeFromDestroyedActivity() {
        try {
            ReaderReadingSessionRuntime.publishCleanPreview(true)
            assertTrue(ReaderReadingSessionRuntime.cleanPreviewActive)

            ReaderSession()

            assertFalse(ReaderReadingSessionRuntime.cleanPreviewActive)
        } finally {
            ReaderReadingSessionRuntime.publishCleanPreview(false)
        }
    }
}
