package com.junchen.jingdu

enum class SemanticCandidateLabel { BODY, AD, UNCERTAIN }

data class SemanticCandidateDecision(
    val label: SemanticCandidateLabel,
    val confidence: Float,
    val score: Int = 0,
)

/** Candidate-only local classifier contract. A complete TXT document is never accepted here. */
internal fun interface SemanticCandidateClassifier {
    fun classifyCandidate(text: String): SemanticCandidateDecision
}

/**
 * Tiny quantized linear model for ambiguous Smart Clean candidates. It uses a 64-bucket hashed
 * character-bigram vector plus a small set of auditable structural features. Weights are signed
 * int8-range values generated deterministically from quality/smartclean/train-v1.tsv; inference
 * allocates no model tensor/runtime and is deliberately precision-first. High-confidence AD can
 * strengthen a candidate, BODY can protect it, and the wide middle region stays UNCERTAIN.
 */
internal object TinyLocalSemanticCandidateClassifier : SemanticCandidateClassifier {
    const val MODEL_VERSION = 1
    private const val AD_THRESHOLD = 20
    private const val BODY_THRESHOLD = -12
    private val weights = intArrayOf(
        0, -2, 1, 0, 0, 0, -2, 0, -1, -6, 1, -2, -1, -1, 0, -1,
        -3, 1, 0, -1, 0, -1, -1, 0, 0, -1, -4, 0, 0, -2, 0, -5,
        -3, -1, -1, -5, -7, 0, -2, 2, -1, -2, -5, 0, 1, -1, -3, 0,
        2, -4, 0, -1, -1, -1, 2, -2, -2, 0, -1, -1, -1, 0, 0, -2,
    )
    private val strongMarkers = listOf(
        "http://", "https://", "www.", ".com", ".net", ".cn", ".tw", ".hk",
        "最新网址", "备用网址", "请收藏本站", "请记住本站", "手机用户请访问",
        "关注公众号", "微信公众号", "本书来自", "更多精彩", "搜索书名", "请牢记域名",
        "最新網址", "備用網址", "請收藏本站", "請記住本站", "手機用戶請訪問",
        "關注公眾號", "本書來自", "更多精彩", "搜尋書名", "請牢記網域",
    )

    override fun classifyCandidate(text: String): SemanticCandidateDecision {
        val value = text.trim().take(512)
        if (value.length < 4) return SemanticCandidateDecision(SemanticCandidateLabel.UNCERTAIN, 0f, 0)
        var score = -4
        val lower = value.lowercase()
        strongMarkers.forEach { marker -> if (lower.contains(marker.lowercase())) score += 12 }
        val seen = BooleanArray(weights.size)
        val chars = value.filterNot(Char::isWhitespace)
        for (index in 0 until chars.length - 1) {
            val slot = hashBigram(chars[index], chars[index + 1]) and (weights.size - 1)
            if (!seen[slot]) {
                seen[slot] = true
                score += weights[slot]
            }
        }
        if (value.length > 180) score -= 4
        if ('。' in value && strongMarkers.none { lower.contains(it.lowercase()) }) score -= 4
        if (looksLikeHeading(value)) score -= 20

        val label = when {
            score >= AD_THRESHOLD -> SemanticCandidateLabel.AD
            score <= BODY_THRESHOLD -> SemanticCandidateLabel.BODY
            else -> SemanticCandidateLabel.UNCERTAIN
        }
        val confidence = when (label) {
            SemanticCandidateLabel.AD -> ((score - AD_THRESHOLD + 20) / 40f).coerceIn(0.5f, 0.99f)
            SemanticCandidateLabel.BODY -> ((BODY_THRESHOLD - score + 20) / 40f).coerceIn(0.5f, 0.99f)
            SemanticCandidateLabel.UNCERTAIN -> (kotlin.math.abs(score) / 40f).coerceIn(0f, 0.49f)
        }
        return SemanticCandidateDecision(label, confidence, score)
    }

    private fun looksLikeHeading(value: String): Boolean {
        if (value.startsWith("Chapter", ignoreCase = true)) return true
        if (value.startsWith('第')) {
            val prefix = value.take(24)
            if (listOf('章', '回', '节', '節', '卷').any(prefix::contains)) return true
        }
        return value in setOf("序章", "楔子", "前言", "序言", "后记", "後記", "尾声", "尾聲", "大结局", "大結局", "终章", "終章")
    }

    private fun hashBigram(first: Char, second: Char): Int {
        var hash = 0x811c9dc5.toInt()
        listOf(first, second).forEach { character ->
            val code = character.code
            hash = (hash xor (code and 0xff)) * 0x01000193
            hash = (hash xor ((code ushr 8) and 0xff)) * 0x01000193
        }
        return hash
    }
}

/** Kept for tests/experiments that explicitly require the disabled seam. */
internal object DisabledSemanticCandidateClassifier : SemanticCandidateClassifier {
    override fun classifyCandidate(text: String) = SemanticCandidateDecision(
        label = SemanticCandidateLabel.UNCERTAIN,
        confidence = 0f,
        score = 0,
    )
}
