package com.junchen.jingdu

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableUserAssetsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun stagedProgressRequiresExactNormalizedRevision() {
        val store = LibraryMetadataStore(context)
        val bookId = "a".repeat(64)
        val revision = "b".repeat(64)
        store.clear(bookId)
        store.restorePortable(bookId, favorite = true, tags = listOf("长篇", "离线"), normalizedSha256 = revision, progress = 12_345)

        assertNull(store.consumeRestoredProgress(bookId, "c".repeat(64)))
        val before = store.load(bookId)
        assertTrue(before.favorite)
        assertEquals(listOf("长篇", "离线"), before.tags)
        assertEquals(12_345L, store.consumeRestoredProgress(bookId, revision))
        assertNull(store.consumeRestoredProgress(bookId, revision))
        assertNull(store.load(bookId).pendingProgress)
        store.clear(bookId)
    }

    @Test fun smartCleanFeedbackBackupContainsNoCandidateTextAndRoundTripsDecision() {
        val store = SmartCleanFeedbackStore(context)
        val bookId = "d".repeat(64)
        val reason = "promo_repeated"
        val candidate = "private candidate text that must not enter backup"
        store.clearBook(bookId)
        store.record(bookId, reason, candidate, SmartCleanFeedback.PROTECT)

        val backup = store.exportJson()
        assertFalse(backup.toString().contains(candidate))
        assertFalse(backup.optBoolean("containsBookText", true))
        store.clearBook(bookId)
        assertEquals(SmartCleanFeedback.NONE, store.decision(bookId, reason, candidate))

        assertEquals(1, store.importJson(backup))
        assertEquals(SmartCleanFeedback.PROTECT, store.decision(bookId, reason, candidate))
        store.clearBook(bookId)
    }

    @Test fun readingStatsBackupRejectsBookTextContractAndRefreshesLivePace() {
        val assets = UserAssetBackup(context)
        val liveStats = ReaderStatsStore(context)
        val bookId = "e".repeat(64)
        val stats = JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-reading-stats")
            .put("containsBookText", false)
            .put("pace", JSONObject().put("charsPerMinute", 640.0).put("samples", 9))
            .put(
                "sessions",
                JSONArray().put(
                    JSONObject()
                        .put("id", "session-1")
                        .put("bookId", bookId)
                        .put("dayEpoch", 20_000)
                        .put("startedAt", 1_700_000_000_000L)
                        .put("durationMs", 60_000L)
                        .put("startPosition", 100L)
                        .put("endPosition", 900L)
                        .put("charsRead", 800L),
                ),
            )

        assertEquals(1, assets.importReadingStats(stats))
        assertEquals(640.0, liveStats.charsPerMinute(), 0.01)
        val exported = assets.exportReadingStats()
        assertFalse(exported.optBoolean("containsBookText", true))
        assertTrue(exported.optJSONArray("sessions")!!.length() >= 1)

        assets.importReadingStats(
            JSONObject()
                .put("schema", 1)
                .put("type", "jingdu-reading-stats")
                .put("containsBookText", false)
                .put("sessions", JSONArray()),
        )
    }

    @Test fun malformedSchema4PreflightPreventsLibraryMutation() {
        val metadata = LibraryMetadataStore(context)
        val bookId = "f".repeat(64)
        metadata.clear(bookId)
        metadata.restorePortable(bookId, favorite = false, tags = listOf("backup"), normalizedSha256 = null, progress = null)

        val backup = UserBackup(
            readerPreferences = ReaderPreferences(context),
            ruleLibrary = RuleLibrary(context),
            annotationStore = ReaderAnnotationStore(context),
        )
        val root = JSONObject(backup.exportJson())

        metadata.restorePortable(bookId, favorite = true, tags = listOf("current"), normalizedSha256 = null, progress = null)
        root.getJSONObject("readingStats").put("containsBookText", true)

        var rejected = false
        try {
            backup.importJson(root.toString())
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        val current = metadata.load(bookId)
        assertTrue(current.favorite)
        assertEquals(listOf("current"), current.tags)
        metadata.clear(bookId)
    }
}
