package com.junchen.jingdu

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

internal class ReviewPrompter(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordBookOpened() {
        val count = preferences.getInt(KEY_OPENS, 0) + 1
        preferences.edit().putInt(KEY_OPENS, count).apply()
        if (count == 3) maybeRequest()
    }

    fun recordSmartCleanApplied() {
        val count = preferences.getInt(KEY_CLEANS, 0) + 1
        preferences.edit().putInt(KEY_CLEANS, count).apply()
        if (count >= 2) maybeRequest()
    }

    fun recordEncodingRescue() {
        val count = preferences.getInt(KEY_REDECODES, 0) + 1
        preferences.edit().putInt(KEY_REDECODES, count).apply()
        if (count == 1 && preferences.getInt(KEY_OPENS, 0) >= 2) maybeRequest()
    }

    private fun maybeRequest() {
        val now = System.currentTimeMillis()
        val last = preferences.getLong(KEY_LAST_REQUEST, 0L)
        if (last != 0L && now - last < MIN_INTERVAL_MS) return
        preferences.edit().putLong(KEY_LAST_REQUEST, now).apply()

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) return@addOnCompleteListener
            manager.launchReviewFlow(activity, request.result)
        }
    }

    companion object {
        private const val PREFS = "jingdu.review.v1"
        private const val KEY_OPENS = "book.opens"
        private const val KEY_CLEANS = "smart.clean.applied"
        private const val KEY_REDECODES = "encoding.rescues"
        private const val KEY_LAST_REQUEST = "review.last.request"
        private const val MIN_INTERVAL_MS = 180L * 24L * 60L * 60L * 1000L
    }
}
