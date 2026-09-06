package com.junchen.jingdu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class RuleLibrary(context: Context) {
    internal val appContext: Context = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<RepairRule> = decode(preferences.getString(KEY_RULES, "[]") ?: "[]")

    fun save(rules: List<RepairRule>) {
        val normalized = rules
            .filter { it.find.isNotBlank() && validField(it.find) && validField(it.replacement) }
            .distinctBy { Triple(it.mode, it.find, it.replacement) }
            .take(MAX_RULES)
        preferences.edit().putString(KEY_RULES, encode(normalized)).apply()
    }

    fun add(rule: RepairRule): List<RepairRule> {
        val updated = (load() + rule).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(MAX_RULES)
        save(updated)
        return updated
    }

    fun remove(index: Int): List<RepairRule> {
        val current = load()
        if (index !in current.indices) return current
        val updated = current.filterIndexed { i, _ -> i != index }
        save(updated)
        return updated
    }

    fun installRecommended(): List<RepairRule> {
        val recommended = BuiltinCleanRules.rules.map(BuiltinCleanRule::rule)
        val updated = (load() + recommended).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(MAX_RULES)
        save(updated)
        return updated
    }

    fun exportJson(): String = JSONObject()
        .put("schema", 1)
        .put("type", "jingdu-global-clean-rules")
        .put("builtinPackVersion", BuiltinCleanRules.PACK_VERSION)
        .put("rules", JSONArray(encode(load())))
        .toString(2)

    fun importJson(text: String): List<RepairRule> {
        val imported = parseExportJson(text, allowEmpty = false)
        val updated = (load() + imported).distinctBy { Triple(it.mode, it.find, it.replacement) }.take(MAX_RULES)
        save(updated)
        return updated
    }

    fun parseExportJson(text: String, allowEmpty: Boolean): List<RepairRule> {
        val root = JSONObject(text)
        if (root.optInt("schema") != 1 || root.optString("type") != "jingdu-global-clean-rules") {
            throw IllegalArgumentException("unsupported_rule_file")
        }
        val rulesArray = root.optJSONArray("rules") ?: JSONArray()
        if (rulesArray.length() > MAX_RULES) throw IllegalArgumentException("rule_limit_exceeded")
        val imported = decode(rulesArray.toString())
        if (!allowEmpty && imported.isEmpty()) throw IllegalArgumentException("no_valid_rules")
        return imported
    }

    private fun encode(rules: List<RepairRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            if (!validField(rule.find) || !validField(rule.replacement)) return@forEach
            array.put(JSONObject().put("mode", rule.mode.name).put("find", rule.find).put("replacement", rule.replacement))
        }
        return array.toString()
    }

    private fun decode(text: String): List<RepairRule> {
        val output = mutableListOf<RepairRule>()
        val array = try { JSONArray(text) } catch (_: Throwable) { return emptyList() }
        for (index in 0 until minOf(array.length(), MAX_RULES)) {
            val item = array.optJSONObject(index) ?: continue
            val find = item.optString("find")
            val replacement = item.optString("replacement")
            if (find.isBlank() || !validField(find) || !validField(replacement)) continue
            val mode = runCatching { RepairRuleMode.valueOf(item.optString("mode")) }.getOrDefault(RepairRuleMode.LITERAL)
            output += RepairRule(find, replacement, mode)
        }
        return output.distinctBy { Triple(it.mode, it.find, it.replacement) }
    }

    private fun validField(value: String): Boolean = value.length <= MAX_FIELD_CHARS && value.none { it == '\u001e' || it == '\u001f' }

    companion object {
        private const val PREFS = "jingdu.rules.global.v1"
        private const val KEY_RULES = "rules"
        private const val MAX_RULES = 500
        private const val MAX_FIELD_CHARS = 1024
    }
}
