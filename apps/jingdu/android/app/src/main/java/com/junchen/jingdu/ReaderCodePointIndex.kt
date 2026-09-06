package com.junchen.jingdu

/**
 * Sparse code-point -> UTF-16 index for the bounded Reader page window.
 *
 * Reader positions are code-point offsets while Kotlin strings are UTF-16 indexed. Building the
 * anchors once when a page cache window is filled keeps the UI-thread page slice bounded to at most
 * one small stride instead of rescanning the 64 KiB window prefix on every page turn.
 */
internal class ReaderCodePointIndex private constructor(
    private val text: String,
    private val stride: Int,
    private val utf16Anchors: IntArray,
    val codePointCount: Int,
) {
    fun utf16At(codePointOffset: Int): Int {
        val bounded = codePointOffset.coerceIn(0, codePointCount)
        if (bounded == codePointCount) return text.length
        val anchorIndex = bounded / stride
        val anchorCodePoints = anchorIndex * stride
        val anchorUtf16 = utf16Anchors[anchorIndex]
        return text.offsetByCodePoints(anchorUtf16, bounded - anchorCodePoints)
    }

    fun slice(startCodePoint: Int, maximumCodePoints: Int): String {
        if (text.isEmpty() || maximumCodePoints <= 0) return ""
        val start = startCodePoint.coerceIn(0, codePointCount)
        if (start >= codePointCount) return ""
        val end = (start.toLong() + maximumCodePoints.toLong())
            .coerceAtMost(codePointCount.toLong())
            .toInt()
        return text.substring(utf16At(start), utf16At(end))
    }

    companion object {
        private const val DEFAULT_STRIDE = 256

        fun build(text: String, stride: Int = DEFAULT_STRIDE): ReaderCodePointIndex {
            val safeStride = stride.coerceAtLeast(1)
            val count = text.codePointCount(0, text.length)
            val anchorCount = (count / safeStride) + 1
            val anchors = IntArray(anchorCount)
            var utf16 = 0
            for (index in 1 until anchorCount) {
                val previousCodePoints = (index - 1) * safeStride
                val targetCodePoints = (index * safeStride).coerceAtMost(count)
                utf16 = text.offsetByCodePoints(utf16, targetCodePoints - previousCodePoints)
                anchors[index] = utf16
            }
            return ReaderCodePointIndex(text, safeStride, anchors, count)
        }
    }
}
