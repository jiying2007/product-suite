package com.junchen.jingdu

import org.json.JSONArray
import org.json.JSONObject

/** Portable local-user backup. Book/source/normalized/Clean text is intentionally never included. */
internal class UserBackup(
    private val readerPreferences: ReaderPreferences,
    private val ruleLibrary: RuleLibrary,
    private val annotationStore: ReaderAnnotationStore,
) {
    private val assets = UserAssetBackup(ruleLibrary.appContext)
    private val smartCleanFeedback = SmartCleanFeedbackStore(ruleLibrary.appContext)
    private val ttsPronunciation = TtsPronunciationStore(ruleLibrary.appContext)

    fun exportJson(): String {
        val settings = JSONObject()
        readerPreferences.exportMap().forEach { (key, value) -> settings.put(key, value) }
        val ruleRoot = JSONObject(ruleLibrary.exportJson())
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("type", "jingdu-local-user-backup")
            .put("reader", "v3")
            .put("containsBookText", false)
            .put("settings", settings)
            .put("annotations", annotationStore.exportJson())
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
        if (schema !in setOf(LEGACY_SCHEMA, SCHEMA) || root.optString("type") != "jingdu-local-user-backup" || root.optString("reader") != "v3") {
            throw IllegalArgumentException("not a Reader backup")
        }
        if (schema == SCHEMA && root.optBoolean("containsBookText", true)) {
            throw IllegalArgumentException("backup privacy contract missing")
        }

        val settingsObject = root.optJSONObject("settings") ?: throw IllegalArgumentException("backup missing reader settings")
        val settingsMap = linkedMapOf<String, Any?>()
        settingsObject.keys().forEach { key -> settingsMap[key] = settingsObject.opt(key) }
        val annotations = root.optJSONArray("annotations")
            ?: if (schema == SCHEMA) throw IllegalArgumentException("backup missing annotations") else JSONArray()
        if (annotations.length() > MAX_ANNOTATIONS) throw IllegalArgumentException("too many annotations")
        val globalRules = root.optJSONArray("globalRules")
            ?: if (schema == SCHEMA) throw IllegalArgumentException("backup missing global rules") else JSONArray()
        val ruleRoot = JSONObject()
            .put("schema", 1)
            .put("type", "jingdu-global-clean-rules")
            .put("rules", globalRules)
        val rules = ruleLibrary.parseExportJson(ruleRoot.toString(), allowEmpty = true)

        var library: JSONArray? = null
        var stats: JSONObject? = null
        var feedback: JSONObject? = null
        var pronunciationRaw: String? = null
        if (schema == SCHEMA) {
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

        val settings = readerPreferences.importMap(settingsMap)
        annotationStore.importJson(annotations)
        ruleLibrary.save(rules)

        var libraryAssets = 0
        var readingSessions = 0
        var feedbackEntries = 0
        if (schema == SCHEMA) {
            libraryAssets = assets.importLibrary(requireNotNull(library))
            readingSessions = assets.importReadingStats(requireNotNull(stats))
            feedbackEntries = smartCleanFeedback.importJson(requireNotNull(feedback))
            // Optional for backward compatibility with early schema-4 pre-production backups.
            pronunciationRaw?.let(ttsPronunciation::save)
        }
        return Result(settings, rules, libraryAssets, readingSessions, feedbackEntries)
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
        const val SCHEMA = 4
        const val MAX_BACKUP_CHARS = 2 * 1024 * 1024
        const val MAX_ANNOTATIONS = 20_000
    }
}
