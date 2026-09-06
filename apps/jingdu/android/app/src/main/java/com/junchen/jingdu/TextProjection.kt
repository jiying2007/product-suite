package com.junchen.jingdu

/**
 * Monotonic edit-aware projection between bounded source and presentation text.
 *
 * Equal-length transformations (the common OpenCC case) preserve exact code-point boundaries.
 * Length-changing edits are localized around resynchronization anchors instead of scaling an entire
 * window, so unchanged text before and after an edit remains exact. Ambiguity exists only inside an
 * actual replacement/deletion/insertion span, where boundaries are interpolated monotonically.
 */
internal class TextProjection private constructor(
    private val sourceToDisplay: IntArray?,
    private val displayToSource: IntArray?,
    private val sourceCount: Int,
    private val displayCount: Int,
    private val identityMapping: Boolean,
) {
    val sourceCodePoints: Long get() = sourceCount.toLong()
    val displayCodePoints: Long get() = displayCount.toLong()

    fun displayForSource(source: Long): Long {
        val bounded = source.coerceIn(0, sourceCodePoints)
        if (identityMapping) return bounded
        return sourceToDisplay!![bounded.toInt()].toLong()
    }

    fun sourceForDisplay(display: Long): Long {
        val bounded = display.coerceIn(0, displayCodePoints)
        if (identityMapping) return bounded
        return displayToSource!![bounded.toInt()].toLong()
    }

    fun compose(after: TextProjection): TextProjection {
        require(displayCodePoints == after.sourceCodePoints) { "projection domains do not compose" }
        // Reader presentation commonly ends in an equal-code-point transformation (ORIGINAL or
        // OpenCC script conversion). Reusing the authoritative mapping avoids copying and walking
        // two full IntArrays on every page while preserving exactly the same boundary semantics.
        if (after.identityMapping) return this
        if (identityMapping) return after
        val firstS2d = sourceToDisplay!!
        val afterD2s = after.displayToSource!!
        val s2d = IntArray(sourceCount + 1)
        for (index in s2d.indices) s2d[index] = after.displayForSource(firstS2d[index].toLong()).toInt()
        val d2s = IntArray(after.displayCount + 1)
        for (index in d2s.indices) d2s[index] = sourceForDisplay(afterD2s[index].toLong()).toInt()
        return TextProjection(s2d, d2s, sourceCount, after.displayCount, false)
    }

    companion object {
        fun identity(codePoints: Int): TextProjection {
            val count = codePoints.coerceAtLeast(0)
            // Identity projections are by far the most common Reader path (ORIGINAL text and
            // equal-code-point script conversion). Keep only the domain size: mapping is simply a
            // clamp, so allocating and initializing two page-sized IntArrays provides no value.
            return TextProjection(null, null, count, count, true)
        }

        fun between(source: String, display: String): TextProjection {
            // Preserve the existing rule exactly: equal code-point counts map one-to-one regardless
            // of glyph/script changes. Count first so the hot identity path never materializes two
            // complete code-point arrays merely to discover that their lengths are equal.
            if (source === display) return identity(source.codePointCount(0, source.length))
            val sourceCount = source.codePointCount(0, source.length)
            val displayCount = display.codePointCount(0, display.length)
            if (sourceCount == displayCount) return identity(sourceCount)
            return between(source.codePoints().toArray(), display.codePoints().toArray())
        }

        fun between(source: IntArray, display: IntArray): TextProjection {
            val n = source.size
            val m = display.size
            if (n == m) return identity(n)
            val s2d = IntArray(n + 1) { -1 }
            val d2s = IntArray(m + 1) { -1 }
            s2d[0] = 0; d2s[0] = 0
            var i = 0
            var j = 0
            while (i < n && j < m) {
                if (source[i] == display[j]) {
                    i++; j++; s2d[i] = j; d2s[j] = i
                    continue
                }
                val match = nearestAnchor(source, display, i, j)
                if (match == null) {
                    mapEditSpan(s2d, d2s, i, n, j, m)
                    i = n; j = m
                } else {
                    val (nextI, nextJ) = match
                    mapEditSpan(s2d, d2s, i, nextI, j, nextJ)
                    i = nextI; j = nextJ
                }
            }
            if (i < n || j < m) mapEditSpan(s2d, d2s, i, n, j, m)
            s2d[n] = m; d2s[m] = n
            fillMonotonic(s2d, m)
            fillMonotonic(d2s, n)
            return TextProjection(s2d, d2s, n, m, false)
        }

        private fun nearestAnchor(source: IntArray, display: IntArray, sourceAt: Int, displayAt: Int): Pair<Int, Int>? {
            var bestI = -1
            var bestJ = -1
            var bestCost = Int.MAX_VALUE
            val limits = intArrayOf(24, 96, 384)
            for (limit in limits) {
                val maxI = minOf(source.size, sourceAt + limit)
                val maxJ = minOf(display.size, displayAt + limit)
                for (i in sourceAt until maxI) {
                    val sourceDelta = i - sourceAt
                    if (bestCost != Int.MAX_VALUE && sourceDelta >= bestCost) break
                    val jLimit = if (bestCost == Int.MAX_VALUE) {
                        maxJ
                    } else {
                        minOf(maxJ, displayAt + (bestCost - sourceDelta))
                    }
                    for (j in displayAt until jLimit) {
                        if (source[i] != display[j]) continue
                        if (!anchorLooksStable(source, display, i, j)) continue
                        val cost = sourceDelta + (j - displayAt)
                        if (cost < bestCost) {
                            bestCost = cost; bestI = i; bestJ = j
                            if (cost <= 2) return bestI to bestJ
                        }
                    }
                }
                if (bestI >= 0) return bestI to bestJ
            }
            return null
        }

        private fun anchorLooksStable(source: IntArray, display: IntArray, i: Int, j: Int): Boolean {
            var equal = 0
            var compared = 0
            while (compared < 6 && i + compared < source.size && j + compared < display.size) {
                if (source[i + compared] == display[j + compared]) equal++
                compared++
            }
            return equal >= minOf(3, compared)
        }

        private fun mapEditSpan(
            s2d: IntArray,
            d2s: IntArray,
            sourceStart: Int,
            sourceEnd: Int,
            displayStart: Int,
            displayEnd: Int,
        ) {
            val sourceCount = sourceEnd - sourceStart
            val displayCount = displayEnd - displayStart
            if (sourceCount == 0) {
                for (d in displayStart..displayEnd) d2s[d] = sourceStart
                if (sourceStart in s2d.indices) s2d[sourceStart] = displayEnd
                return
            }
            if (displayCount == 0) {
                for (s in sourceStart..sourceEnd) s2d[s] = displayStart
                if (displayStart in d2s.indices) d2s[displayStart] = sourceEnd
                return
            }
            for (s in sourceStart..sourceEnd) {
                val fraction = (s - sourceStart).toDouble() / sourceCount.toDouble()
                s2d[s] = (displayStart + fraction * displayCount).toInt().coerceIn(displayStart, displayEnd)
            }
            for (d in displayStart..displayEnd) {
                val fraction = (d - displayStart).toDouble() / displayCount.toDouble()
                d2s[d] = (sourceStart + fraction * sourceCount).toInt().coerceIn(sourceStart, sourceEnd)
            }
        }

        private fun fillMonotonic(values: IntArray, max: Int) {
            var last = 0
            for (index in values.indices) {
                val value = if (values[index] >= 0) values[index] else last
                last = value.coerceIn(last, max)
                values[index] = last
            }
            var next = max
            for (index in values.indices.reversed()) {
                next = values[index].coerceIn(0, next)
                values[index] = next
            }
        }
    }
}
