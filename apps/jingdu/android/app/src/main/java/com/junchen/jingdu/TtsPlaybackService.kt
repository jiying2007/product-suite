package com.junchen.jingdu

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Single Android speech playback authority. Media3 owns session/controller/notification lifecycle;
 * ReaderTtsPlayer owns TextToSpeech transport while source offsets remain Jingdu/Core coordinates.
 */
@OptIn(UnstableApi::class)
class TtsPlaybackService : MediaSessionService() {
    private val main = Handler(Looper.getMainLooper())
    private val progressWorkers: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jingdu-tts-progress").apply { isDaemon = true }
    }
    private lateinit var repository: BookRepository
    private lateinit var player: ReaderTtsPlayer
    private var session: MediaSession? = null
    private var book: BookRepository.Book? = null
    private var lastProgressPersistAt = 0L
    private var lastProgressPersistOffset = -1L

    override fun onCreate() {
        super.onCreate()
        repository = BookRepository(this)
        player = ReaderTtsPlayer(this, ::onPlayerState)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_START -> startBook(intent)
                ACTION_TOGGLE -> if (player.snapshot().playing) player.pause() else player.play()
                ACTION_STOP -> player.stopTts("user")
                ACTION_NEXT -> player.nextSentence()
                ACTION_PREVIOUS -> player.previousSentence()
                ACTION_NEXT_PARAGRAPH -> player.nextParagraph()
                ACTION_PREVIOUS_PARAGRAPH -> player.previousParagraph()
                ACTION_SLEEP -> setSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
                ACTION_STATE -> onPlayerState(player.snapshot())
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startBook(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val requestedBookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        if (path.isBlank() || requestedBookId.isBlank()) {
            player.stopTts("missing document")
            return
        }
        val matched = repository.list().firstOrNull { it.id == requestedBookId }
        if (matched == null) {
            player.stopTts("missing book")
            return
        }
        val source = File(path)
        if (!source.isFile) {
            player.stopTts("missing document")
            return
        }
        val previous = book
        if (previous != null && previous.id != matched.id) {
            val previousOffset = player.snapshot().offset
            if (previousOffset >= 0) persistProgress(previous, previousOffset, force = true)
        }
        book = matched
        lastProgressPersistAt = 0L
        lastProgressPersistOffset = -1L
        runCatching {
            player.load(
                file = source,
                requestedBookId = requestedBookId,
                requestedTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.app_title) },
                fromOffset = intent.getLongExtra(EXTRA_OFFSET, matched.progress),
                rate = intent.getFloatExtra(EXTRA_RATE, 1f),
                pitch = intent.getFloatExtra(EXTRA_PITCH, 1f),
                voiceName = intent.getStringExtra(EXTRA_VOICE).orEmpty(),
                chineseMode = runCatching {
                    ChineseDisplayMode.valueOf(intent.getStringExtra(EXTRA_CHINESE_MODE).orEmpty())
                }.getOrDefault(ChineseDisplayMode.ORIGINAL),
                chineseOverrides = intent.getStringExtra(EXTRA_CHINESE_OVERRIDES).orEmpty(),
            )
        }.onFailure { player.stopTts(it.message ?: "tts open failure") }
    }

    private fun setSleepTimer(minutes: Int) {
        main.removeCallbacksAndMessages(SLEEP_TOKEN)
        if (minutes <= 0) return
        main.postAtTime(
            { player.stopTts("sleep") },
            SLEEP_TOKEN,
            SystemClock.uptimeMillis() + minutes * 60_000L,
        )
    }

    private fun onPlayerState(state: ReaderTtsState) {
        book?.let { current ->
            if (state.offset >= 0) persistProgress(current, state.offset, force = !state.active || !state.playing)
        }
        val broadcast = Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_ACTIVE, state.active)
            .putExtra(EXTRA_PLAYING, state.playing)
            .putExtra(EXTRA_OFFSET, state.offset)
            .putExtra(EXTRA_NEXT_OFFSET, state.nextOffset)
            .putExtra(EXTRA_RANGE_START, state.rangeStart)
            .putExtra(EXTRA_RANGE_END, state.rangeEnd)
        state.reason?.let { broadcast.putExtra(EXTRA_REASON, it) }
        sendBroadcast(broadcast)
        if (!state.active && state.reason !in setOf(null, "paused", "focus-paused")) stopSelf()
    }

    private fun persistProgress(current: BookRepository.Book, offset: Long, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgressPersistAt < PROGRESS_SAVE_INTERVAL_MS &&
            abs(offset - lastProgressPersistOffset) < PROGRESS_SAVE_CHAR_DELTA) return
        lastProgressPersistAt = now
        lastProgressPersistOffset = offset
        progressWorkers.execute { runCatching { repository.saveProgress(current, offset) } }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (::player.isInitialized) {
            val state = player.snapshot()
            book?.let { current ->
                if (state.offset >= 0) {
                    // Submit the durability-boundary write behind all already queued progress work,
                    // then wait for it. This guarantees no older queued offset can overwrite final state.
                    runCatching {
                        progressWorkers.submit { repository.saveProgress(current, state.offset) }.get()
                    }.getOrElse {
                        runCatching { repository.saveProgress(current, state.offset) }
                    }
                }
            }
        }
        progressWorkers.shutdownNow()
        session?.release()
        session = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.junchen.jingdu.tts.START"
        const val ACTION_TOGGLE = "com.junchen.jingdu.tts.TOGGLE"
        const val ACTION_STOP = "com.junchen.jingdu.tts.STOP"
        const val ACTION_NEXT = "com.junchen.jingdu.tts.NEXT"
        const val ACTION_PREVIOUS = "com.junchen.jingdu.tts.PREVIOUS"
        const val ACTION_NEXT_PARAGRAPH = "com.junchen.jingdu.tts.NEXT_PARAGRAPH"
        const val ACTION_PREVIOUS_PARAGRAPH = "com.junchen.jingdu.tts.PREVIOUS_PARAGRAPH"
        const val ACTION_SLEEP = "com.junchen.jingdu.tts.SLEEP"
        const val ACTION_STATE = "com.junchen.jingdu.tts.STATE"

        const val EXTRA_PATH = "path"
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OFFSET = "offset"
        const val EXTRA_RATE = "rate"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_CHINESE_MODE = "chineseMode"
        const val EXTRA_CHINESE_OVERRIDES = "chineseOverrides"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_ACTIVE = "active"
        const val EXTRA_PLAYING = "playing"
        const val EXTRA_NEXT_OFFSET = "nextOffset"
        const val EXTRA_RANGE_START = "rangeStart"
        const val EXTRA_RANGE_END = "rangeEnd"
        const val EXTRA_REASON = "reason"

        private const val PROGRESS_SAVE_INTERVAL_MS = 15_000L
        private const val PROGRESS_SAVE_CHAR_DELTA = 2_048L
        private val SLEEP_TOKEN = Any()
    }
}
