package com.junchen.jingdu

import java.util.ArrayDeque
import java.util.LinkedHashMap

internal fun readerDocumentKey(bookId: String, normalizedSha256: String): String =
    "$bookId\u001f$normalizedSha256"

/**
 * Exact measured paged boundaries are published by the UI worker and consumed by navigation.
 * Keying by immutable book revision + source start means a late worker can never move a newer page.
 */
internal object ReaderPageBoundaryRuntime {
    private data class Key(val documentKey: String, val start: Long)
    private val boundaries = object : LinkedHashMap<Key, Long>(40, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Long>?): Boolean = size > MAX_BOUNDARIES
    }

    @Synchronized
    fun publish(documentKey: String, start: Long, endExclusive: Long) {
        if (documentKey.isBlank() || start < 0L || endExclusive <= start) return
        boundaries[Key(documentKey, start)] = endExclusive
    }

    @Synchronized
    fun endFor(documentKey: String, start: Long): Long? =
        boundaries[Key(documentKey, start)]?.takeIf { it > start }

    @Synchronized
    fun invalidate(documentKey: String, start: Long) {
        boundaries.remove(Key(documentKey, start))
    }

    @Synchronized
    fun previousStartFor(documentKey: String, endExclusive: Long): Long? {
        var best: Long? = null
        for ((key, end) in boundaries) {
            if (key.documentKey != documentKey || end != endExclusive || key.start >= endExclusive) continue
            if (best == null || key.start > best) best = key.start
        }
        return best
    }

    private const val MAX_BOUNDARIES = 64
}

/**
 * Android reader-session boundary. Core ReaderController remains the source-offset authority;
 * this class owns the currently-open book/revision and all transient page navigation state.
 */
internal class ReaderSession {
    init {
        // ReaderReadingSessionRuntime is process-local while MainActivity/ReaderSession is not.
        // A new Activity/session must never inherit a stale Clean Preview flag from a destroyed one.
        ReaderReadingSessionRuntime.publishCleanPreview(false)
    }

    var reader: ReaderController = ReaderController()
        internal set
    var book: BookRepository.Book? = null
        internal set
    var cleanMode: Boolean = false
        internal set(value) {
            field = value
            ReaderReadingSessionRuntime.publishCleanPreview(value)
        }
    var visiblePageChars: Long = ReaderController.DEFAULT_PAGE_CHARS
    internal val pageHistory = ArrayDeque<Long>()

    fun replace(nextReader: ReaderController, nextBook: BookRepository.Book, clean: Boolean): ReaderController {
        val previous = reader
        reader = nextReader
        book = nextBook
        cleanMode = clean
        pageHistory.clear()
        return previous
    }

    fun clear(): ReaderController {
        val previous = reader
        reader = ReaderController()
        book = null
        cleanMode = false
        pageHistory.clear()
        return previous
    }

    fun pushPage(position: Long) {
        if (pageHistory.lastOrNull() != position) pageHistory.addLast(position)
        while (pageHistory.size > MAX_PAGE_HISTORY) pageHistory.removeFirst()
    }

    fun previousPagePosition(): Long? = if (pageHistory.isEmpty()) null else pageHistory.removeLast()
    fun clearPageHistory() = pageHistory.clear()
    fun hasBook(): Boolean = book != null

    private companion object { const val MAX_PAGE_HISTORY = 256 }
}
