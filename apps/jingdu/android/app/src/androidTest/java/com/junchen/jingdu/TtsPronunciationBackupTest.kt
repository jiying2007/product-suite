package com.junchen.jingdu

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TtsPronunciationBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun schema4BackupCarriesPronunciationsWithoutBookText() {
        val store = TtsPronunciationStore(context)
        store.save("单于 => chán yú\n长孙 => zhǎng sūn")
        val backup = UserBackup(
            readerPreferences = ReaderPreferences(context),
            ruleLibrary = RuleLibrary(context),
            annotationStore = ReaderAnnotationStore(context),
        )
        val exported = JSONObject(backup.exportJson())
        assertFalse(exported.optBoolean("containsBookText", true))
        assertEquals("单于 => chán yú\n长孙 => zhǎng sūn", exported.getString("ttsPronunciation"))

        store.save("")
        backup.importJson(exported.toString())
        assertEquals("单于 => chán yú\n长孙 => zhǎng sūn", store.raw())
        store.save("")
    }

    @Test fun olderSchema4BackupWithoutPronunciationRemainsImportable() {
        val store = TtsPronunciationStore(context)
        store.save("叶凡 => yè fán")
        val backup = UserBackup(
            readerPreferences = ReaderPreferences(context),
            ruleLibrary = RuleLibrary(context),
            annotationStore = ReaderAnnotationStore(context),
        )
        val root = JSONObject(backup.exportJson()).apply { remove("ttsPronunciation") }
        backup.importJson(root.toString())
        // Missing optional field preserves the destination's local pronunciation asset.
        assertEquals("叶凡 => yè fán", store.raw())
        store.save("")
    }
}
