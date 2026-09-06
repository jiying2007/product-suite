package com.junchen.jingdu

import openccjava.OpenCC
import java.util.Locale
import java.util.UUID

/** Stateless bounded Chinese text conversion. Source files and source offsets are never mutated. */
internal object ChineseTextConverter {
    private const val MAX_OVERRIDES = 200
    private const val MAX_OVERRIDE_FIELD_CHARS = 64

    fun convert(text: String, mode: ChineseDisplayMode, overridesText: String): String {
        if (text.isEmpty() || mode == ChineseDisplayMode.ORIGINAL) return text
        val overrides = parseOverrides(overridesText)
        var protectedText = text
        val protectedOverrides = ArrayList<ProtectedOverride>()
        val nonce = "__JINGDU_${UUID.randomUUID().toString().replace("-", "")}_"
        overrides.forEachIndexed { index, pair ->
            if (pair.source !in protectedText) return@forEachIndexed
            val token = "$nonce${index}__"
            protectedText = protectedText.replace(pair.source, token)
            protectedOverrides += ProtectedOverride(token, pair.target)
        }
        var converted = OpenCC.convert(protectedText, config(mode))
        protectedOverrides.forEach { pair -> converted = converted.replace(pair.token, pair.target) }
        return converted
    }

    /** Search source text with the common Chinese script variants without maintaining a second table. */
    fun searchVariants(query: String): List<String> {
        val value = query.trim()
        if (value.isEmpty()) return emptyList()
        val variants = linkedSetOf(value)
        listOf("t2s", "s2t", "s2twp", "s2hk").forEach { config ->
            runCatching { OpenCC.convert(value, config) }.getOrNull()?.takeIf(String::isNotEmpty)?.let(variants::add)
        }
        return variants.toList()
    }

    fun overrideCount(overridesText: String): Int = parseOverrides(overridesText).size

    private fun config(mode: ChineseDisplayMode): String = when (mode) {
        ChineseDisplayMode.SIMPLIFIED -> "t2s"
        ChineseDisplayMode.TRADITIONAL -> "s2t"
        ChineseDisplayMode.TAIWAN -> "s2tw"
        ChineseDisplayMode.TAIWAN_PHRASES -> "s2twp"
        ChineseDisplayMode.HONG_KONG -> "s2hk"
        ChineseDisplayMode.ORIGINAL -> error("ORIGINAL has no OpenCC config")
    }

    private fun parseOverrides(raw: String): List<OverridePair> {
        if (raw.isBlank()) return emptyList()
        val values = raw.lineSequence().mapNotNull { line ->
            val value = line.trim()
            if (value.isEmpty() || value.startsWith('#')) return@mapNotNull null
            val arrow = value.indexOf("=>")
            val split = if (arrow >= 1) arrow else value.indexOf('=')
            val step = if (arrow >= 1) 2 else 1
            if (split < 1) return@mapNotNull null
            val source = value.substring(0, split).trim()
            val target = value.substring(split + step).trim()
            if (source.isEmpty() || target.isEmpty() ||
                source.length > MAX_OVERRIDE_FIELD_CHARS || target.length > MAX_OVERRIDE_FIELD_CHARS
            ) return@mapNotNull null
            OverridePair(source, target)
        }.take(MAX_OVERRIDES).sortedByDescending { it.source.codePointCount(0, it.source.length) }

        val seen = hashSetOf<String>()
        return values.filter { seen.add(it.source.lowercase(Locale.ROOT)) }.toList()
    }

    private data class OverridePair(val source: String, val target: String)
    private data class ProtectedOverride(val token: String, val target: String)
}
