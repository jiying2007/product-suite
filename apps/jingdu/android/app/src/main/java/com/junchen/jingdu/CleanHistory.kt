package com.junchen.jingdu

import android.content.Context

/**
 * Stores only rule metadata, never book text. The snapshot is intentionally one-deep: Smart Clean
 * is reversible without turning Clean into an unbounded history database.
 */
internal class CleanHistory(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.clean.undo.v1", Context.MODE_PRIVATE)

    fun save(bookId: String, packedRules: String) {
        prefs.edit()
            .putString(key(bookId), packedRules.take(MAX_PACKED_RULES_CHARS))
            .putLong(timeKey(bookId), System.currentTimeMillis())
            .apply()
    }

    fun peek(bookId: String): String? = prefs.getString(key(bookId), null)

    fun has(bookId: String): Boolean = prefs.contains(key(bookId))

    fun clear(bookId: String) {
        prefs.edit().remove(key(bookId)).remove(timeKey(bookId)).apply()
    }

    fun clearAllForBook(bookId: String) = clear(bookId)

    private fun key(bookId: String) = "rules.$bookId"
    private fun timeKey(bookId: String) = "time.$bookId"

    private companion object {
        const val MAX_PACKED_RULES_CHARS = 512 * 1024
    }
}
