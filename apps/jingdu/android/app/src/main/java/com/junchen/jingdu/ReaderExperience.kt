package com.junchen.jingdu

import android.content.Context
import android.os.SystemClock
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Runtime-only coordination for controls that are owned by Android rather than Compose. */
internal object ReaderInteractionRuntime {
    @Volatile var backgroundTtsPlaying: Boolean = false
    @Volatile var foregroundPosition: Long = -1L
    @Volatile var continuousReady: Boolean = false
    @Volatile var volumeEligibilityChecks: Long = 0L
    @Volatile var lastVolumeForegroundTtsPlaying: Boolean = false
    @Volatile var lastVolumeEligible: Boolean = false
    @Volatile var controlsVisible: Boolean = true
    @Volatile var pagedGestureDowns: Long = 0L
    @Volatile var pagedTapCandidates: Long = 0L
    @Volatile var pagedCenterDispatches: Long = 0L
    @Volatile var lastPagedGestureDurationMs: Long = -1L
    @Volatile var lastPagedGestureDistancePx: Float = -1f
    @Volatile var lastPagedGestureConsumedByChild: Boolean = false

    fun resetPagedGestureDiagnostics() {
        controlsVisible = true
        pagedGestureDowns = 0L
        pagedTapCandidates = 0L
        pagedCenterDispatches = 0L
        lastPagedGestureDurationMs = -1L
        lastPagedGestureDistancePx = -1f
        lastPagedGestureConsumedByChild = false
    }

    fun resetVolumeDiagnostics() {
        volumeEligibilityChecks = 0L
        lastVolumeForegroundTtsPlaying = false
        lastVolumeEligible = false
    }

    fun shouldUseVolumeKeysForPaging(settings: ReaderSettings, foregroundTtsPlaying: Boolean): Boolean {
        val eligible = if (settings.autoScrollEnabled) {
            false
        } else {
            when (settings.volumeKeyMode) {
                ReaderVolumeKeyMode.SYSTEM_VOLUME -> false
                ReaderVolumeKeyMode.ALWAYS_PAGE -> true
                ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS -> !foregroundTtsPlaying && !backgroundTtsPlaying
            }
        }
        lastVolumeForegroundTtsPlaying = foregroundTtsPlaying
        lastVolumeEligible = eligible
        volumeEligibilityChecks += 1L
        return eligible
    }
}

internal data class ContinuousWindow(
    val start: Long,
    val text: String,
    val documentLength: Long,
)

/**
 * Read-only bounded companion window for continuous mode.
 *
 * It never owns persisted position: MainActivity's ReaderController/sourceOffset remains authoritative.
 * The companion only reads at explicit source offsets so Compose never needs the whole TXT in memory.
 */
internal class ContinuousWindowReader(context: Context, bookId: String) : Closeable {
    private val reader = ReaderController()

    init {
        val repository = BookRepository(context.applicationContext)
        val book = repository.list().firstOrNull { it.id == bookId }
            ?: throw IllegalStateException("book unavailable")
        reader.open(repository.normalizedFile(book), 0)
    }

    @Synchronized
    fun readAround(position: Long): ContinuousWindow {
        val length = reader.length()
        val bounded = position.coerceIn(0L, (length - 1).coerceAtLeast(0L))
        val start = (bounded - BACK_BUFFER_CHARS).coerceAtLeast(0L)
        return ContinuousWindow(
            start = start,
            text = reader.readAt(start, ReaderController.WINDOW_CHARS),
            documentLength = length,
        )
    }

    @Synchronized
    override fun close() = reader.close()

    private companion object {
        const val BACK_BUFFER_CHARS = 1400L
    }
}

/** Local-only pace estimate used for optional remaining-time UI. No book text or analytics leave device. */
internal class ReadingPaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jingdu.reader.pace.v1", Context.MODE_PRIVATE)
    private var bookId: String? = null
    private var lastPosition = -1L
    private var lastAt = 0L

    fun markManualPage(book: String, position: Long) {
        val now = SystemClock.elapsedRealtime()
        if (bookId == book && lastPosition >= 0 && position > lastPosition) {
            val elapsed = now - lastAt
            val chars = position - lastPosition
            if (elapsed in MIN_SAMPLE_MS..MAX_SAMPLE_MS && chars in MIN_SAMPLE_CHARS..MAX_SAMPLE_CHARS) {
                val sample = (chars.toDouble() * 60_000.0 / elapsed.toDouble()).coerceIn(MIN_CPM, MAX_CPM)
                val previous = prefs.getFloat(KEY_CPM, DEFAULT_CPM.toFloat()).toDouble()
                val next = previous * 0.80 + sample * 0.20
                prefs.edit().putFloat(KEY_CPM, next.toFloat()).putInt(KEY_SAMPLES, prefs.getInt(KEY_SAMPLES, 0) + 1).apply()
            }
        }
        bookId = book
        lastPosition = position
        lastAt = now
    }

    fun remainingMinutes(position: Long, length: Long): Int? {
        if (length <= 0 || position >= length || prefs.getInt(KEY_SAMPLES, 0) < 2) return null
        val cpm = prefs.getFloat(KEY_CPM, DEFAULT_CPM.toFloat()).toDouble().coerceIn(MIN_CPM, MAX_CPM)
        return ceil((length - position).toDouble() / cpm).roundToInt().coerceAtLeast(1)
    }

    fun resetSession(book: String, position: Long) {
        if (bookId == book && abs(position - lastPosition) < MAX_SAMPLE_CHARS) return
        bookId = book
        lastPosition = position
        lastAt = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val KEY_CPM = "charsPerMinute"
        const val KEY_SAMPLES = "samples"
        const val DEFAULT_CPM = 500.0
        const val MIN_CPM = 120.0
        const val MAX_CPM = 1800.0
        const val MIN_SAMPLE_MS = 3_000L
        const val MAX_SAMPLE_MS = 180_000L
        const val MIN_SAMPLE_CHARS = 80L
        const val MAX_SAMPLE_CHARS = 12_000L
    }
}
