package com.junchen.jingdu

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

private const val DEFAULT_READER_CPM = 500.0
private const val MIN_READER_CPM = 120.0
private const val MAX_READER_CPM = 1800.0

/** Process-local hot-path pace cache shared by every ReaderStatsStore instance. */
private object ReaderPaceRuntime {
    @Volatile var charsPerMinute = DEFAULT_READER_CPM
        private set
    @Volatile var samples = 0
        private set
    @Volatile private var generation = 0L

    fun generation(): Long = generation

    @Synchronized
    fun initialize(value: ReaderPaceEntity?, expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        apply(value)
    }

    @Synchronized
    fun restore(value: ReaderPaceEntity?) {
        generation++
        apply(value)
    }

    @Synchronized
    fun update(pace: Double, sampleCount: Int) {
        generation++
        charsPerMinute = pace.coerceIn(MIN_READER_CPM, MAX_READER_CPM)
        samples = sampleCount.coerceAtLeast(0)
    }

    private fun apply(value: ReaderPaceEntity?) {
        val pace = value?.charsPerMinute
        charsPerMinute = if (pace != null && pace.isFinite()) {
            pace.coerceIn(MIN_READER_CPM, MAX_READER_CPM)
        } else {
            DEFAULT_READER_CPM
        }
        samples = value?.samples?.coerceAtLeast(0) ?: 0
    }
}

/** Update the live Reader hot-path cache only after a restored Room pace has committed. */
internal fun restoreReaderPaceRuntime(value: ReaderPaceEntity?) = ReaderPaceRuntime.restore(value)

/**
 * Local-only reader statistics. The hot scroll/page path mutates memory only; Room writes are
 * batched and sessions are committed at lifecycle boundaries. No analytics SDK or network exists.
 */
internal class ReaderStatsStore(context: Context) {
    private val dao = ReaderDatabaseProvider.get(context).statsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionBook: String? = null
    private var sessionStartedElapsed = 0L
    private var sessionStartedWall = 0L
    private var sessionStartPosition = 0L
    private var lastPosition = 0L
    private var lastAt = 0L
    private var lastPacePersistAt = 0L

    init {
        val generation = ReaderPaceRuntime.generation()
        scope.launch { ReaderPaceRuntime.initialize(dao.pace(), generation) }
    }

    fun begin(bookId: String, position: Long) {
        if (sessionBook == bookId) return
        finish()
        val now = SystemClock.elapsedRealtime()
        sessionBook = bookId
        sessionStartedElapsed = now
        sessionStartedWall = System.currentTimeMillis()
        sessionStartPosition = position
        lastPosition = position
        lastAt = now
    }

    fun mark(bookId: String, position: Long) {
        begin(bookId, position)
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastAt
        val chars = position - lastPosition
        if (elapsed in 2_000L..180_000L && chars in 64L..20_000L) {
            val sample = (chars.toDouble() * 60_000.0 / elapsed.toDouble()).coerceIn(MIN_READER_CPM, MAX_READER_CPM)
            val currentSamples = ReaderPaceRuntime.samples
            val weight = if (currentSamples < 5) 0.35 else 0.15
            val nextPace = ReaderPaceRuntime.charsPerMinute * (1.0 - weight) + sample * weight
            ReaderPaceRuntime.update(nextPace, currentSamples + 1)
            if (now - lastPacePersistAt >= PACE_PERSIST_INTERVAL_MS) {
                lastPacePersistAt = now
                persistPaceAsync()
            }
        }
        lastPosition = position
        lastAt = now
    }

    fun finish() {
        val book = sessionBook ?: return
        val endElapsed = SystemClock.elapsedRealtime()
        val duration = (endElapsed - sessionStartedElapsed).coerceAtLeast(0)
        // Snapshot every mutable session field before the asynchronous Room write. begin() may start
        // the next book immediately after finish(), so the worker must never read live session state.
        val startPosition = sessionStartPosition
        val endPosition = lastPosition
        val chars = (endPosition - startPosition).coerceAtLeast(0)
        val startedWall = sessionStartedWall
        sessionBook = null
        persistPaceAsync()
        if (duration < MIN_SESSION_MS) return
        scope.launch {
            dao.insertSession(
                ReaderSessionEntity(
                    id = UUID.randomUUID().toString(), bookId = book,
                    dayEpoch = readerDayEpoch(startedWall),
                    startedAt = startedWall, durationMs = duration,
                    startPosition = startPosition, endPosition = endPosition, charsRead = chars,
                ),
            )
        }
    }

    fun charsPerMinute(): Double = ReaderPaceRuntime.charsPerMinute.coerceIn(MIN_READER_CPM, MAX_READER_CPM)

    fun sessionMinutes(): Int = if (sessionBook == null) 0 else
        ceil((SystemClock.elapsedRealtime() - sessionStartedElapsed).coerceAtLeast(0) / 60_000.0).toInt()

    fun remainingMinutes(position: Long, length: Long): Int? {
        if (length <= 0 || position >= length) return null
        return ceil((length - position).toDouble() / charsPerMinute()).toInt().coerceAtLeast(1)
    }

    fun chapterRemainingMinutes(position: Long, chapterEnd: Long): Int? {
        if (chapterEnd <= position) return null
        return ceil((chapterEnd - position).toDouble() / charsPerMinute()).toInt().coerceAtLeast(1)
    }

    fun observeDays(limit: Int = 365): Flow<List<ReaderDayAggregate>> = dao.observeDays(limit.coerceIn(7, 730))
    fun days(limit: Int = 365): List<ReaderDayAggregate> = runBlocking(Dispatchers.IO) { dao.days(limit.coerceIn(7, 730)) }
    fun totalBookDuration(bookId: String): Long = runBlocking(Dispatchers.IO) { dao.totalBookDuration(bookId) }

    private fun persistPaceAsync() {
        val pace = ReaderPaceRuntime.charsPerMinute.coerceIn(MIN_READER_CPM, MAX_READER_CPM)
        val samples = ReaderPaceRuntime.samples
        scope.launch { dao.upsertPace(ReaderPaceEntity(charsPerMinute = pace, samples = samples)) }
    }

    private companion object {
        const val PACE_PERSIST_INTERVAL_MS = 30_000L
        const val MIN_SESSION_MS = 5_000L
    }
}

/** Calendar/heatmap bucketing follows the device's civil day, not UTC midnight. */
internal fun readerDayEpoch(startedAtMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(startedAtMillis).atZone(zoneId).toLocalDate().toEpochDay()
