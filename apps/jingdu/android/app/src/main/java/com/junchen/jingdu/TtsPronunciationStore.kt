package com.junchen.jingdu

import android.content.Context

internal data class TtsPronunciationRule(val source: String, val spoken: String)

internal data class TtsPronunciationPresentation(
    val text: String,
    val projection: TextProjection,
)

/**
 * Local literal pronunciation overrides for Chinese names/polyphones. Rules are deliberately small,
 * bounded and non-regex. They alter only the speech projection; Reader/source/search/TOC text stays
 * unchanged and TTS range callbacks remain mapped to source coordinates through TextProjection.
 */
internal class TtsPronunciationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Volatile private var cachedRaw: String? = null
    @Volatile private var cachedRules: List<TtsPronunciationRule> = emptyList()

    fun raw(): String = preferences.getString(KEY, "").orEmpty()

    @Synchronized
    fun save(raw: String): List<TtsPronunciationRule> {
        val parsed = parse(raw)
        val canonical = parsed.joinToString("\n") { "${it.source} => ${it.spoken}" }
        preferences.edit().putString(KEY, canonical).apply()
        cachedRaw = canonical
        cachedRules = parsed
        return parsed
    }

    fun present(text: String): TtsPronunciationPresentation {
        val rules = rules()
        if (text.isEmpty() || rules.isEmpty()) {
            return TtsPronunciationPresentation(text, TextProjection.identity(text.codePointCount(0, text.length)))
        }
        var spoken = text
        // Longest source first prevents a short name from partially consuming a more specific name.
        rules.sortedByDescending { it.source.length }.forEach { rule -> spoken = spoken.replace(rule.source, rule.spoken) }
        return TtsPronunciationPresentation(spoken, TextProjection.between(text, spoken))
    }

    @Synchronized
    private fun rules(): List<TtsPronunciationRule> {
        val raw = raw()
        if (raw == cachedRaw) return cachedRules
        val parsed = parse(raw)
        cachedRaw = raw
        cachedRules = parsed
        return parsed
    }

    companion object {
        const val MAX_RULES = 100
        const val MAX_RAW_CHARS = 16 * 1024
        private const val MAX_SOURCE_CHARS = 64
        private const val MAX_SPOKEN_CHARS = 128
        private const val PREFS = "jingdu.tts.pronunciation.v1"
        private const val KEY = "rules"

        fun parse(raw: String): List<TtsPronunciationRule> {
            if (raw.length > MAX_RAW_CHARS) throw IllegalArgumentException("pronunciation dictionary too large")
            return raw.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .mapNotNull { line ->
                    val marker = line.indexOf("=>")
                    if (marker <= 0) return@mapNotNull null
                    val source = line.substring(0, marker).trim()
                    val spoken = line.substring(marker + 2).trim()
                    if (source.isEmpty() || spoken.isEmpty()) return@mapNotNull null
                    if (source.length > MAX_SOURCE_CHARS || spoken.length > MAX_SPOKEN_CHARS) return@mapNotNull null
                    if (source.any { it == '\u0000' || it == '\n' || it == '\r' } || spoken.any { it == '\u0000' || it == '\n' || it == '\r' }) return@mapNotNull null
                    TtsPronunciationRule(source, spoken)
                }
                .distinctBy(TtsPronunciationRule::source)
                .take(MAX_RULES)
                .toList()
        }
    }
}
