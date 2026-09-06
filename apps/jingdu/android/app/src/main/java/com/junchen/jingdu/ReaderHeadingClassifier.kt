package com.junchen.jingdu

/** Document-content heading heuristic; never UI copy. */
internal object ReaderHeadingClassifier {
    private const val CJK_ORDINAL_PREFIX = "\u7b2c"
    fun isHeading(value: String): Boolean {
        val text = value.trim()
        return text.length in 2..48 && (text.startsWith(CJK_ORDINAL_PREFIX) || text.startsWith("Chapter", ignoreCase = true))
    }
}
