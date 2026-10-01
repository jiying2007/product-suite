package com.junchen.jingdu

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Portable local-user backup: no complete book payload or automatically captured source excerpts. */
internal class UserBackup(
    private val readerPreferences: ReaderPreferences,
    private val ruleLibrary: RuleLibrary,
    private val annotationStore: ReaderAnnotationStore,
) {
    private val assets = UserAssetBackup(ruleLibrary.appContext)
    private val smartCleanFeedback = SmartCleanFeedbackStore(ruleLibrary.appContext)
    private val ttsPronunciation = TtsPronunciationStore(ruleLibrary.appContext)
    private val restoreJournal = File(ruleLibrary.appContext.filesDir, RESTORE_JOURNAL)
    private val restoreJournalTmp = File(ruleLibrary.appContext.filesDir, "$RESTORE_JOURNAL.tmp")
    private var recovering = false

    init {
        // A completed journal exists only after a durable rename and before the first mutation.
        // If Android killed the process during a multi-store restore, replay the pre-restore
        // snapshot before exposing the app's user state again.
        restoreJournalTmp.delete()
        recoverPendingRestore()
    }

    fun exportJson(): String {
        val settings = JSONObject()
        readerPreferences.exportMap().forEach { (key, value) -> settings.put(key, value) }
        val ruleRoot = JSONObject(ruleLibrary.exportJson())
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("type", "jingdu-local-user-backup")
            .put("reader", "v3")
            .put("containsBookText", false)
            .put("containsAutomaticBookExcerpts", false)
            .put("containsUserAuthoredText", true)
            .put("settings", settings)
            .put("annotations", annotationStore.exportPortableJson())
            .put("globalRules", ruleRoot.optJSONArray("rules") ?: JSONArray())
            .put("libraryAssets", assets.exportLibrary())
            .put("readingStats", assets.exportReadingStats())
            .put("smartCleanFeedback", smartCleanFeedback.exportJson())
            .put("ttsPronunciation", ttsPronunciation.raw())
        val text = root.toString(2)
        if (text.length > MAX_BACKUP_CHARS) throw IllegalStateException("backup exceeds portable size limit")
        return text
    }

    fun importJson(text: String): Result {
        if (text.length > MAX_BACKUP_CHARS) throw IllegalArgumentException("backup too large")
        val root = JSONObject(text)
        val schema = root.optInt("schema")
        if (schema !in setOf(LEGACY_SCHEMA, PREVIOUS_SCHEMA, SCHEMA) || root.optString("type") != "jingdu-local-user-backup" || root.optString("reader") != "v3") {
            throw IllegalArgumentException("not a Reader backup")
        }
        if (schema >= PREVIOUS_SCHEMA && root.optBoolean("containsBookText", true)) {
            throw IllegalArgumentException("backup privacy contract missing")
        }
        if (schema == SCHEMA && root.optBoolean("containsAutomaticBookExcerpts", true)) {
            throw IllegalArgumentException("backup automatic excerpt privacy contract missing")
        }

        val settingsObject = root.optJSONObject("settings") ?: throw IllegalArgumentException("backup missing reader settings")
        val settingsMap = linkedMapOf<String, Any?>()
        settingsObject.keys().forEach { key -> settingsMap[key] = settingsObject.opt(key) }
        val annotations = root.optJSONArray("annotations")
            ?: if (schema >= PREVIOUS_SCHEMA) throw IllegalArgumentException("backup missing annotations") else JSONArray()
        if (annotations.length() > MAX_ANNOTATIONS) throw IllegalArgumentException("too many annotations")
        val globalRules = root.optJSONArray("globalRules")
            ?: if (schema >= PREVIOUS_SCHEMA) throw IllegalArgumentException("backup missing global rules") else JSONArray()
        val ruleRoot = JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-global-clean-rules")
            .put("rules", globalRules)
        val rules = ruleLibrary.parseExportJson(ruleRoot.toString(), allowEmpty = true)

        var library: JSONArray? = null
        var stats: JSONObject? = null
        var feedback: JSONObject? = null
        var pronunciationRaw: String? = null
        if (schema >= PREVIOUS_SCHEMA) {
            library = root.optJSONArray("libraryAssets") ?: throw IllegalArgumentException("backup missing library assets")
            stats = root.optJSONObject("readingStats") ?: throw IllegalArgumentException("backup missing reading stats")
            feedback = root.optJSONObject("smartCleanFeedback") ?: throw IllegalArgumentException("backup missing Smart Clean feedback")
            pronunciationRaw = root.optString("ttsPronunciation").takeIf { root.has("ttsPronunciation") }

            // Parse every schema-4 section before the first persistent mutation. A malformed or
            // privacy-invalid backup must fail without partially replacing settings/library state.
            assets.validateLibrary(library)
            assets.validateReadingStats(stats)
            smartCleanFeedback.validateImport(feedback)
            pronunciationRaw?.let(TtsPronunciationStore::parse)
        }

        if (!recovering) writeRestoreJournal(exportJson())
        return try {
            val settings = readerPreferences.importMap(settingsMap)
            if (schema == LEGACY_SCHEMA) annotationStore.importJson(annotations) else annotationStore.importPortableJson(annotations)
            ruleLibrary.save(rules)

            var libraryAssets = 0
            var readingSessions = 0
            var feedbackEntries = 0
            if (schema >= PREVIOUS_SCHEMA) {
                libraryAssets = assets.importLibrary(requireNotNull(library))
                readingSessions = assets.importReadingStats(requireNotNull(stats))
                feedbackEntries = smartCleanFeedback.importJson(requireNotNull(feedback))
                // Optional for backward compatibility with early schema-4 pre-production backups.
                pronunciationRaw?.let(ttsPronunciation::save)
            }
            if (!recovering) clearRestoreJournal()
            Result(settings, rules, libraryAssets, readingSessions, feedbackEntries)
        } catch (error: Throwable) {
            if (!recovering) recoverPendingRestore()
            throw error
        }
    }

    private fun writeRestoreJournal(snapshot: String) {
        // The rollback journal is private app state, not a portable export. Preserve the complete
        // local annotation snapshot so a failed restore can recover re-anchor context exactly.
        val journal = JSONObject(snapshot)
            .put(INTERNAL_ANNOTATIONS, annotationStore.exportJson())
            .put(INTERNAL_CONTAINS_BOOK_TEXT, true)
            .toString()
        if (journal.toByteArray(Charsets.UTF_8).size > MAX_ROLLBACK_JOURNAL_BYTES) {
            throw IllegalStateException("rollback snapshot too large")
        }
        FileOutputStream(restoreJournalTmp).use { output ->
            output.write(journal.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (restoreJournal.exists() && !restoreJournal.delete()) throw IllegalStateException("cannot replace restore journal")
        if (!restoreJournalTmp.renameTo(restoreJournal)) throw IllegalStateException("cannot publish restore journal")
    }

    private fun recoverPendingRestore() {
        if (!restoreJournal.isFile || recovering) return
        val journal = runCatching {
            if (restoreJournal.length() > MAX_ROLLBACK_JOURNAL_BYTES) error("restore journal too large")
            JSONObject(restoreJournal.readText(Charsets.UTF_8))
        }.getOrNull() ?: return
        val fullAnnotations = journal.optJSONArray(INTERNAL_ANNOTATIONS)
        journal.remove(INTERNAL_ANNOTATIONS)
        journal.remove(INTERNAL_CONTAINS_BOOK_TEXT)
        recovering = true
        val restored = try {
            runCatching {
                importJson(journal.toString())
                fullAnnotations?.let(annotationStore::importJson)
            }.isSuccess
        } finally {
            recovering = false
        }
        if (restored) clearRestoreJournal()
    }

    private fun clearRestoreJournal() {
        restoreJournalTmp.delete()
        restoreJournal.delete()
    }

    data class Result(
        val settings: ReaderSettings,
        val globalRules: List<RepairRule>,
        val libraryAssets: Int = 0,
        val readingSessions: Int = 0,
        val feedbackEntries: Int = 0,
    )

    private companion object {
        const val LEGACY_SCHEMA = 3
        const val PREVIOUS_SCHEMA = 4
        const val SCHEMA = 5
        const val MAX_BACKUP_CHARS = 2 * 1024 * 1024
        const val MAX_ANNOTATIONS = 20_000
        const val RESTORE_JOURNAL = "jingdu-user-backup-restore.rollback.json"
        const val INTERNAL_ANNOTATIONS = "_internalRollbackAnnotations"
        const val INTERNAL_CONTAINS_BOOK_TEXT = "_internalContainsBookText"
        const val MAX_ROLLBACK_JOURNAL_BYTES = 32L * 1024L * 1024L
    }
}
