package com.junchen.jingdu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

internal fun ttsRuntimeErrorRetryable(reason: String?): Boolean {
    if (reason == "tts error" || reason == "tts error: speak failed") return true
    if (reason?.startsWith("tts error: ") != true) return false
    return reason.removePrefix("tts error: ").toIntOrNull() in setOf(-3, -4, -5, -6, -7)
}

internal fun ttsQueuedNextOffset(
    currentOffset: Long,
    sourceOffset: Long,
    nextOffset: Long,
): Long? = nextOffset.takeIf { sourceOffset == currentOffset && it >= sourceOffset }

internal data class ReaderTtsState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val offset: Long = -1L,
    val nextOffset: Long = -1L,
    val rangeStart: Long = -1L,
    val rangeEnd: Long = -1L,
    val reason: String? = null,
)

/**
 * Media3 Player facade for Android TextToSpeech. It intentionally exposes no fake audio timeline:
 * source offsets remain Jingdu/Core coordinates while Media3 owns transport/session semantics.
 */
@OptIn(UnstableApi::class)
internal class ReaderTtsPlayer(
    context: Context,
    private val onState: (ReaderTtsState) -> Unit,
    engineName: String? = null,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val reader = ReaderController()
    private val engine = TtsController(appContext, engineName = engineName)
    private var active = false
    private var playing = false
    private var offset = 0L
    private var nextOffset = 0L
    private var rangeStart = -1L
    private var rangeEnd = -1L
    private var title = "Jingdu"
    private var bookId = ""
    private var chineseMode = ChineseDisplayMode.ORIGINAL
    private var chineseOverrides = ""
    private var startRetries = 0
    private var runtimeRetries = 0
    private var lastRangePublishAt = 0L
    private var lastReason: String? = null

    private val commands = Player.Commands.Builder()
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_STOP)
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_GET_TIMELINE)
        .add(Player.COMMAND_GET_METADATA)
        .add(Player.COMMAND_RELEASE)
        .build()

    fun load(
        file: File,
        requestedBookId: String,
        requestedTitle: String,
        fromOffset: Long,
        rate: Float,
        pitch: Float,
        voiceName: String,
        chineseMode: ChineseDisplayMode,
        chineseOverrides: String,
    ) {
        assertApplicationThread()
        engine.stop(null)
        reader.close()
        reader.open(file, fromOffset.coerceAtLeast(0))
        bookId = requestedBookId
        title = requestedTitle.ifBlank { "Jingdu" }
        offset = reader.position()
        nextOffset = offset
        rangeStart = -1L
        rangeEnd = -1L
        lastReason = null
        engine.setRate(rate)
        engine.setPitch(pitch)
        engine.setVoiceName(voiceName)
        this.chineseMode = chineseMode
        this.chineseOverrides = chineseOverrides
        active = true
        playing = true
        startRetries = 0
        runtimeRetries = 0
        lastRangePublishAt = 0L
        publish()
        startSpeechWithRetry()
    }

    fun snapshot(): ReaderTtsState = ReaderTtsState(active, playing, offset, nextOffset, rangeStart, rangeEnd, lastReason)

    fun previousParagraph() = semanticJump(paragraph = true, previous = true)
    fun nextParagraph() = semanticJump(paragraph = true, previous = false)
    fun previousSentence() = semanticJump(paragraph = false, previous = true)
    fun nextSentence() = semanticJump(paragraph = false, previous = false)

    fun stopTts(reason: String? = "user") {
        assertApplicationThread()
        engine.stop(null)
        active = false
        playing = false
        rangeStart = -1L
        rangeEnd = -1L
        lastReason = reason
        publish()
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        if (!active || bookId.isBlank()) {
            return builder.setPlaybackState(Player.STATE_IDLE).build()
        }
        val metadata = MediaMetadata.Builder().setTitle(title).setArtist(appContext.getString(R.string.tts_media_artist)).build()
        val item = MediaItem.Builder().setMediaId(bookId).setMediaMetadata(metadata).build()
        val data = MediaItemData.Builder(bookId)
            .setMediaItem(item)
            .setMediaMetadata(metadata)
            .setDurationUs(C.TIME_UNSET)
            .setIsSeekable(false)
            .build()
        return builder
            .setPlaylist(listOf(data))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0L)
            .setPlaybackState(Player.STATE_READY)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) resumeTts() else pauseTts()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT -> nextSentence()
            Player.COMMAND_SEEK_TO_PREVIOUS -> previousSentence()
            else -> Unit
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        stopTts("user")
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        main.removeCallbacksAndMessages(null)
        engine.close()
        reader.close()
        active = false
        playing = false
        return Futures.immediateVoidFuture()
    }

    private fun resumeTts() {
        if (!active || playing || reader.length() <= 0) return
        playing = true
        lastReason = null
        startRetries = 0
        publish()
        startSpeechWithRetry()
    }

    private fun pauseTts() {
        if (!active || !playing) return
        engine.stop(null)
        playing = false
        lastReason = "paused"
        publish()
    }

    private fun semanticJump(paragraph: Boolean, previous: Boolean) {
        if (!active || reader.length() <= 0) return
        val target = when {
            paragraph && previous -> TtsSemanticNavigator.previousParagraph(reader, offset)
            paragraph -> TtsSemanticNavigator.nextParagraph(reader, offset)
            previous -> TtsSemanticNavigator.previousSentence(reader, offset)
            else -> TtsSemanticNavigator.nextSentence(reader, offset)
        }.coerceIn(0, (reader.length() - 1).coerceAtLeast(0))
        offset = target
        nextOffset = target
        rangeStart = -1L
        rangeEnd = -1L
        reader.jump(target)
        engine.stop(null)
        publish()
        if (playing) startSpeechWithRetry()
    }

    private fun startSpeechWithRetry() {
        if (!active || !playing) return
        engine.start(reader, offset, chineseMode, chineseOverrides, object : TtsController.Listener {
            override fun onChunkQueued(sourceOffset: Long, nextOffset: Long) {
                ttsQueuedNextOffset(this@ReaderTtsPlayer.offset, sourceOffset, nextOffset)?.let { accepted ->
                    this@ReaderTtsPlayer.nextOffset = accepted
                }
            }

            override fun onPosition(offset: Long) {
                val previous = this@ReaderTtsPlayer.offset
                this@ReaderTtsPlayer.offset = offset.coerceAtLeast(0)
                if (this@ReaderTtsPlayer.offset > previous) runtimeRetries = 0
                startRetries = 0
                publish()
            }

            override fun onRange(sourceStart: Long, sourceEnd: Long) {
                rangeStart = sourceStart.coerceAtLeast(0)
                rangeEnd = sourceEnd.coerceAtLeast(rangeStart + 1)
                val now = SystemClock.elapsedRealtime()
                if (now - lastRangePublishAt >= RANGE_PUBLISH_INTERVAL_MS) {
                    lastRangePublishAt = now
                    publish()
                }
            }

            override fun onPaused() {
                playing = false
                lastReason = "focus-paused"
                publish()
            }

            override fun onResumed() {
                playing = true
                lastReason = null
                publish()
            }

            override fun onStopped(reason: String?) {
                if (reason == "TTS engine not ready" && startRetries < MAX_START_RETRIES && active && playing) {
                    startRetries++
                    main.postDelayed(::startSpeechWithRetry, START_RETRY_MS)
                    return
                }
                if (ttsRuntimeErrorRetryable(reason) &&
                    runtimeRetries < MAX_RUNTIME_RETRIES && active && playing
                ) {
                    runtimeRetries++
                    main.postDelayed(::startSpeechWithRetry, RUNTIME_RETRY_MS * runtimeRetries)
                    return
                }
                active = false
                playing = false
                rangeStart = -1L
                rangeEnd = -1L
                lastReason = reason ?: "stopped"
                publish()
            }
        })
    }

    private fun publish() {
        invalidateState()
        onState(snapshot())
    }

    private fun assertApplicationThread() {
        check(Looper.myLooper() == applicationLooper) { "ReaderTtsPlayer must run on its application looper" }
    }

    private companion object {
        const val MAX_START_RETRIES = 12
        const val MAX_RUNTIME_RETRIES = 3
        const val START_RETRY_MS = 250L
        const val RUNTIME_RETRY_MS = 450L
        const val RANGE_PUBLISH_INTERVAL_MS = 400L
    }
}
