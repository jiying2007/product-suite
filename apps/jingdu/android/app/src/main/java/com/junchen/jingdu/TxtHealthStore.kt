package com.junchen.jingdu

import android.content.Context

internal data class TxtHealthSummary(
    val score: Int,
    val issueCount: Int,
    val checkedAt: Long,
) {
    val needsAttention: Boolean get() = score < 90 || issueCount > 0
}

internal class TxtHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(bookId: String): TxtHealthSummary? {
        val score = prefs.getInt("$bookId.score", -1)
        if (score !in 0..100) return null
        return TxtHealthSummary(
            score = score,
            issueCount = prefs.getInt("$bookId.issues", 0).coerceAtLeast(0),
            checkedAt = prefs.getLong("$bookId.checked", 0L),
        )
    }

    fun save(bookId: String, report: TxtDoctorReport) {
        val issues = listOf(
            report.encodingScore < 90,
            report.textScore < 90,
            report.tocScore < 90,
            report.cleanScore < 90,
            report.hardWrapDetected,
        ).count { it }
        prefs.edit()
            .putInt("$bookId.score", report.healthScore)
            .putInt("$bookId.issues", issues)
            .putLong("$bookId.checked", System.currentTimeMillis())
            .apply()
    }

    fun shouldNudge(bookId: String): Boolean = !prefs.getBoolean("$bookId.nudged", false)

    fun markNudged(bookId: String) {
        prefs.edit().putBoolean("$bookId.nudged", true).apply()
    }

    fun remove(bookId: String) {
        prefs.edit()
            .remove("$bookId.score")
            .remove("$bookId.issues")
            .remove("$bookId.checked")
            .remove("$bookId.nudged")
            .apply()
    }

    private companion object {
        const val PREFS = "jingdu.txt-health.v1"
    }
}
