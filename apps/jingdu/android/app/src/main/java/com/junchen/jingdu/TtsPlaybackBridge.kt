package com.junchen.jingdu

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

internal data class TtsPlaybackBroadcastState(
    val active: Boolean,
    val playing: Boolean,
    val offset: Long,
    val nextOffset: Long,
    val rangeStart: Long,
    val rangeEnd: Long,
    val reason: String?,
)

internal class TtsPlaybackBridge(context: Context) {
    private val appContext = context.applicationContext

    fun parseState(intent: Intent?): TtsPlaybackBroadcastState? {
        if (intent?.action != TtsPlaybackService.ACTION_STATE) return null
        return TtsPlaybackBroadcastState(
            active = intent.getBooleanExtra(TtsPlaybackService.EXTRA_ACTIVE, false),
            playing = intent.getBooleanExtra(TtsPlaybackService.EXTRA_PLAYING, false),
            offset = intent.getLongExtra(TtsPlaybackService.EXTRA_OFFSET, -1L),
            nextOffset = intent.getLongExtra(TtsPlaybackService.EXTRA_NEXT_OFFSET, -1L),
            rangeStart = intent.getLongExtra(TtsPlaybackService.EXTRA_RANGE_START, -1L),
            rangeEnd = intent.getLongExtra(TtsPlaybackService.EXTRA_RANGE_END, -1L),
            reason = intent.getStringExtra(TtsPlaybackService.EXTRA_REASON),
        )
    }

    fun queryState() {
        appContext.startService(serviceIntent(TtsPlaybackService.ACTION_STATE))
    }

    fun start(
        source: File,
        bookId: String,
        title: String,
        offset: Long,
        settings: ReaderSettings,
    ) {
        val intent = serviceIntent(TtsPlaybackService.ACTION_START)
            .putExtra(TtsPlaybackService.EXTRA_PATH, source.absolutePath)
            .putExtra(TtsPlaybackService.EXTRA_BOOK_ID, bookId)
            .putExtra(TtsPlaybackService.EXTRA_TITLE, title)
            .putExtra(TtsPlaybackService.EXTRA_OFFSET, offset)
            .putExtra(TtsPlaybackService.EXTRA_RATE, settings.ttsRate)
            .putExtra(TtsPlaybackService.EXTRA_PITCH, settings.ttsPitch)
            .putExtra(TtsPlaybackService.EXTRA_VOICE, settings.ttsVoiceName)
            .putExtra(TtsPlaybackService.EXTRA_CHINESE_MODE, settings.chineseMode.name)
            .putExtra(TtsPlaybackService.EXTRA_CHINESE_OVERRIDES, settings.chineseOverrides)
        if (Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(intent) else appContext.startService(intent)
    }

    fun toggle() {
        appContext.startService(serviceIntent(TtsPlaybackService.ACTION_TOGGLE))
    }

    fun stop() {
        appContext.startService(serviceIntent(TtsPlaybackService.ACTION_STOP))
    }

    fun sleep(minutes: Int) {
        appContext.startService(
            serviceIntent(TtsPlaybackService.ACTION_SLEEP)
                .putExtra(TtsPlaybackService.EXTRA_MINUTES, minutes),
        )
    }

    fun terminate() {
        appContext.stopService(Intent(appContext, TtsPlaybackService::class.java))
    }

    private fun serviceIntent(action: String): Intent =
        Intent(appContext, TtsPlaybackService::class.java).setAction(action)
}
