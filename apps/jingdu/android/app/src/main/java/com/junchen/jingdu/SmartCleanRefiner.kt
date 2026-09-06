package com.junchen.jingdu

import java.io.File
import java.io.IOException

/** Streaming second-stage Smart Clean detector. It never loads or mutates the whole document. */
internal object SmartCleanRefiner {
    data class Candidate(val score: Int, val count: Int, val reason: String, val text: String)

    private const val MAX_LINE_CHARS = 2048
    private const val MAX_UNIQUE = 160
    private val inlineMarkers = listOf(
        "https://", "http://", "www.",
        "最新网址", "备用网址", "请收藏", "请记住", "手机用户", "关注公众号", "牢记本站域名",
        "最新網址", "備用網址", "請收藏", "請記住", "手機用戶", "關注公眾號", "請牢記網域",
    )

    @Throws(IOException::class)
    fun scan(normalizedUtf8: File, maxCandidates: Int): List<Candidate> {
        val merged = linkedMapOf<String, MutableCandidate>()
        normalizedUtf8.bufferedReader(Charsets.UTF_8, 64 * 1024).useLines { lines ->
            lines.forEach { line ->
                if (line.length > MAX_LINE_CHARS) return@forEach
                val trimmed = line.trim()
                if (trimmed.length < 4) return@forEach
                inlineFragment(trimmed)?.let { add(merged, "inline_fragment", it, 72) }
                val badRatio = malformedRatio(trimmed)
                if (badRatio >= 0.16 && trimmed.length <= 512) {
                    add(merged, "garbled_line", trimmed, if (badRatio >= 0.35) 78 else 68)
                }
            }
        }
        return merged.values
            .map { Candidate(it.score, it.count, it.reason, it.text) }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.count }.thenBy { it.text })
            .take(maxCandidates.coerceAtLeast(0))
    }

    private fun add(merged: MutableMap<String, MutableCandidate>, reason: String, text: String, score: Int) {
        val key = "$reason\u001f$text"
        merged[key]?.let { existing ->
            existing.count++
            existing.score = maxOf(existing.score, score)
            return
        }
        if (merged.size >= MAX_UNIQUE) return
        merged[key] = MutableCandidate(reason, text, score)
    }

    private fun inlineFragment(line: String): String? {
        val first = inlineMarkers.map { line.indexOf(it) }.filter { it >= 6 }.minOrNull() ?: return null
        var start = first
        var backtrack = 0
        while (start > 0 && backtrack < 4) {
            val ch = line[start - 1]
            if (ch.isWhitespace() || ch in "【】[]（）()<>《》｜|·-—:：，,。；;") {
                start--
                backtrack++
            } else break
        }
        return line.substring(start).trim().takeIf { it.length in 6..512 }
    }

    private fun malformedRatio(text: String): Double {
        var total = 0
        var suspicious = 0
        var offset = 0
        while (offset < text.length) {
            val cp = text.codePointAt(offset)
            offset += Character.charCount(cp)
            total++
            val type = Character.getType(cp)
            if (cp == 0xFFFD || cp == 0 ||
                (type == Character.CONTROL.toInt() && cp != '\t'.code) ||
                type == Character.UNASSIGNED.toInt() || type == Character.SURROGATE.toInt()
            ) suspicious++
        }
        return if (total == 0) 0.0 else suspicious.toDouble() / total.toDouble()
    }

    private data class MutableCandidate(
        val reason: String,
        val text: String,
        var score: Int,
        var count: Int = 1,
    )
}
