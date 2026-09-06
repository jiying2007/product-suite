package com.junchen.jingdu

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

enum class ReaderAnnotationKind { BOOKMARK, HIGHLIGHT, NOTE }
enum class ReaderHighlightStyle { YELLOW, GREEN, BLUE, PINK }

data class ReaderAnnotation(
    val id: String,
    val bookId: String,
    val sourceStart: Long,
    val sourceEnd: Long,
    val kind: ReaderAnnotationKind,
    val style: ReaderHighlightStyle = ReaderHighlightStyle.YELLOW,
    val note: String = "",
    val excerpt: String = "",
    val anchorBefore: String = "",
    val anchorSelected: String = "",
    val anchorAfter: String = "",
    val anchorHash: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

/**
 * Room-backed source-range annotations. Anchors are captured from the normalized source so display
 * conversion and typography can never invalidate them. Re-decode/re-normalization first performs a
 * bounded contextual re-anchor and uses proportional mapping only as the final fallback.
 */
class ReaderAnnotationStore(private val context: Context) {
    private val dao = ReaderDatabaseProvider.get(context).annotationDao()
    private val repository = BookRepository(context.applicationContext)

    fun observe(bookId: String): Flow<List<ReaderAnnotation>> = dao.observe(bookId).map { values -> values.map(::fromEntity) }
    suspend fun listAsync(bookId: String): List<ReaderAnnotation> = dao.list(bookId).map(::fromEntity)
    fun list(bookId: String): List<ReaderAnnotation> = io { listAsync(bookId) }
    fun bookmarks(bookId: String): List<ReaderAnnotation> = list(bookId).filter { it.kind == ReaderAnnotationKind.BOOKMARK }

    suspend fun addBookmarkAsync(bookId: String, offset: Long): ReaderAnnotation {
        val normalized = offset.coerceAtLeast(0)
        val existing = dao.list(bookId).firstOrNull { it.kind == ReaderAnnotationKind.BOOKMARK.name && abs(it.sourceStart - normalized) <= 1 }
        if (existing != null) return fromEntity(existing)
        val anchor = captureAnchor(bookId, normalized, normalized, bookmark = true)
        val value = ReaderAnnotation(
            id = UUID.randomUUID().toString(), bookId = bookId,
            sourceStart = normalized, sourceEnd = normalized, kind = ReaderAnnotationKind.BOOKMARK,
            anchorBefore = anchor.before, anchorSelected = anchor.selected, anchorAfter = anchor.after, anchorHash = anchor.hash,
        )
        dao.upsert(value.toEntity())
        return value
    }
    fun addBookmark(bookId: String, offset: Long): ReaderAnnotation = io { addBookmarkAsync(bookId, offset) }

    suspend fun upsertRangeAsync(
        bookId: String,
        start: Long,
        end: Long,
        kind: ReaderAnnotationKind,
        style: ReaderHighlightStyle = ReaderHighlightStyle.YELLOW,
        note: String = "",
        excerpt: String = "",
        id: String? = null,
    ): ReaderAnnotation {
        require(kind != ReaderAnnotationKind.BOOKMARK) { "range annotation required" }
        val from = minOf(start, end).coerceAtLeast(0)
        val to = maxOf(start, end).coerceAtLeast(from + 1)
        val previous = id?.let { dao.find(bookId, it) }?.let(::fromEntity)
        val now = System.currentTimeMillis()
        val anchor = captureAnchor(bookId, from, to, bookmark = false)
        val value = ReaderAnnotation(
            id = previous?.id ?: UUID.randomUUID().toString(), bookId = bookId,
            sourceStart = from, sourceEnd = to, kind = kind, style = style,
            note = note.take(MAX_NOTE_CHARS), excerpt = excerpt.take(MAX_EXCERPT_CHARS),
            anchorBefore = anchor.before, anchorSelected = anchor.selected, anchorAfter = anchor.after, anchorHash = anchor.hash,
            createdAt = previous?.createdAt ?: now, updatedAt = now,
        )
        dao.upsert(value.toEntity())
        return value
    }

    fun upsertRange(bookId: String, start: Long, end: Long, kind: ReaderAnnotationKind, style: ReaderHighlightStyle = ReaderHighlightStyle.YELLOW, note: String = "", excerpt: String = "", id: String? = null): ReaderAnnotation =
        io { upsertRangeAsync(bookId, start, end, kind, style, note, excerpt, id) }

    suspend fun deleteAsync(bookId: String, id: String) = dao.delete(bookId, id)
    fun delete(bookId: String, id: String) = io { deleteAsync(bookId, id) }

    suspend fun deleteBookmarkAsync(bookId: String, offset: Long) {
        dao.list(bookId).filter { it.kind == ReaderAnnotationKind.BOOKMARK.name && abs(it.sourceStart - offset) <= 1 }
            .forEach { dao.delete(bookId, it.id) }
    }
    fun deleteBookmark(bookId: String, offset: Long) = io { deleteBookmarkAsync(bookId, offset) }

    suspend fun clearBookAsync(bookId: String) = dao.clearBook(bookId)
    fun clearBook(bookId: String) = io { clearBookAsync(bookId) }

    suspend fun remapBookAsync(bookId: String, oldLength: Long, newLength: Long) {
        if (oldLength <= 0 || newLength <= 0) return
        val book = repository.list().firstOrNull { it.id == bookId } ?: return
        val reader = ReaderController()
        try {
            reader.open(repository.normalizedFile(book), 0)
            val values = dao.list(bookId).map(::fromEntity)
            val remapped = values.map { item -> reanchor(item, reader, oldLength, newLength).toEntity() }
            dao.upsertAll(remapped)
        } finally { reader.close() }
    }

    /**
     * Called by BookRepository while re-decode is already running on the serialized worker. The
     * one-shot in-memory marker lets MainActivity's legacy success-path call return immediately,
     * while a process restart can never leave a stale persisted skip marker behind.
     */
    fun remapBookForRedecode(bookId: String, oldLength: Long, newLength: Long) {
        io { remapBookAsync(bookId, oldLength, newLength) }
        preparedRemaps[bookId] = remapSignature(oldLength, newLength)
    }

    fun remapBook(bookId: String, oldLength: Long, newLength: Long) {
        val signature = remapSignature(oldLength, newLength)
        if (preparedRemaps.remove(bookId, signature)) return
        io { remapBookAsync(bookId, oldLength, newLength) }
    }

    suspend fun exportJsonAsync(): JSONArray = JSONArray().also { array -> dao.listAll().map(::fromEntity).forEach { array.put(toJson(it)) } }
    fun exportJson(): JSONArray = io { exportJsonAsync() }

    suspend fun importJsonAsync(array: JSONArray) {
        val parsed = ArrayList<ReaderAnnotation>()
        for (index in 0 until minOf(array.length(), MAX_ANNOTATIONS)) runCatching { parsed += fromJson(array.getJSONObject(index)) }
        dao.clearAll()
        dao.upsertAll(parsed.distinctBy { it.id }.map(ReaderAnnotation::toEntity))
    }
    fun importJson(array: JSONArray) = io { importJsonAsync(array) }

    private fun captureAnchor(bookId: String, start: Long, end: Long, bookmark: Boolean): Anchor {
        val book = repository.list().firstOrNull { it.id == bookId } ?: return Anchor.EMPTY
        val reader = ReaderController()
        return try {
            reader.open(repository.normalizedFile(book), 0)
            val length = reader.length()
            if (length <= 0) return Anchor.EMPTY
            val safeStart = start.coerceIn(0, length - 1)
            val safeEnd = if (bookmark) (safeStart + 1).coerceAtMost(length) else end.coerceIn(safeStart + 1, length)
            val windowStart = (safeStart - ANCHOR_CONTEXT_CP).coerceAtLeast(0)
            val requested = (safeEnd - windowStart + ANCHOR_CONTEXT_CP).coerceAtMost(MAX_ANCHOR_WINDOW_CP.toLong())
            val text = reader.readAt(windowStart, requested)
            val startLocal = (safeStart - windowStart).coerceAtMost(text.codePointCount(0, text.length).toLong()).toInt()
            val endLocal = (safeEnd - windowStart).coerceAtMost(text.codePointCount(0, text.length).toLong()).toInt()
            val selectedEnd = minOf(endLocal, startLocal + MAX_ANCHOR_SELECTED_CP)
            val beforeStart = maxOf(0, startLocal - ANCHOR_CONTEXT_CP)
            val afterEnd = minOf(text.codePointCount(0, text.length), selectedEnd + ANCHOR_CONTEXT_CP)
            val before = cpSlice(text, beforeStart, startLocal)
            val selected = cpSlice(text, startLocal, selectedEnd)
            val after = cpSlice(text, selectedEnd, afterEnd)
            Anchor(before, selected, after, hashAnchor(before, selected, after))
        } catch (_: Throwable) { Anchor.EMPTY } finally { reader.close() }
    }

    private fun reanchor(item: ReaderAnnotation, reader: ReaderController, oldLength: Long, newLength: Long): ReaderAnnotation {
        val fallback = mapOffset(item.sourceStart, oldLength, newLength)
        val selected = item.anchorSelected
        if (selected.isBlank()) return proportional(item, oldLength, newLength)
        val searchStart = (fallback - SEARCH_RADIUS_CP).coerceAtLeast(0)
        val searchCount = minOf(newLength - searchStart, SEARCH_RADIUS_CP * 2 + MAX_ANCHOR_WINDOW_CP)
        if (searchCount <= 0) return proportional(item, oldLength, newLength)
        val text = runCatching { reader.readAt(searchStart, searchCount) }.getOrNull() ?: return proportional(item, oldLength, newLength)
        val candidates = allIndices(text, selected)
        if (candidates.isEmpty()) return proportional(item, oldLength, newLength)
        val best = candidates.maxByOrNull { utfIndex ->
            contextScore(text, utfIndex, selected.length, item.anchorBefore, item.anchorAfter)
        } ?: return proportional(item, oldLength, newLength)
        val newStart = searchStart + text.codePointCount(0, best).toLong()
        val oldSpan = (item.sourceEnd - item.sourceStart).coerceAtLeast(if (item.kind == ReaderAnnotationKind.BOOKMARK) 0 else 1)
        val newEnd = if (item.kind == ReaderAnnotationKind.BOOKMARK) newStart else (newStart + oldSpan).coerceAtMost(newLength)
        return item.copy(sourceStart = newStart, sourceEnd = newEnd.coerceAtLeast(newStart), updatedAt = System.currentTimeMillis())
    }

    private fun proportional(item: ReaderAnnotation, oldLength: Long, newLength: Long): ReaderAnnotation {
        val start = mapOffset(item.sourceStart, oldLength, newLength)
        val end = if (item.kind == ReaderAnnotationKind.BOOKMARK) start else mapOffset(item.sourceEnd, oldLength, newLength).coerceAtLeast(start + 1).coerceAtMost(newLength)
        return item.copy(sourceStart = start, sourceEnd = end, updatedAt = System.currentTimeMillis())
    }

    private fun contextScore(text: String, selectedUtf: Int, selectedLengthUtf: Int, before: String, after: String): Int {
        val beforeText = text.substring(0, selectedUtf)
        val afterStart = (selectedUtf + selectedLengthUtf).coerceAtMost(text.length)
        val afterText = text.substring(afterStart)
        return commonSuffix(beforeText, before) * 3 + commonPrefix(afterText, after) * 3
    }

    private fun allIndices(text: String, needle: String): List<Int> = buildList {
        var from = 0
        while (from <= text.length - needle.length && size < MAX_ANCHOR_CANDIDATES) {
            val index = text.indexOf(needle, from)
            if (index < 0) break
            add(index); from = index + 1
        }
    }

    private fun commonPrefix(a: String, b: String): Int { var i = 0; val max = minOf(a.length, b.length); while (i < max && a[i] == b[i]) i++; return i }
    private fun commonSuffix(a: String, b: String): Int { var i = 0; val max = minOf(a.length, b.length); while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++; return i }

    private fun cpSlice(text: String, startCp: Int, endCp: Int): String {
        if (text.isEmpty() || endCp <= startCp) return ""
        val total = text.codePointCount(0, text.length)
        val start = text.offsetByCodePoints(0, startCp.coerceIn(0, total))
        val end = text.offsetByCodePoints(0, endCp.coerceIn(startCp.coerceIn(0, total), total))
        return text.substring(start, end)
    }

    private fun hashAnchor(before: String, selected: String, after: String): String = MessageDigest.getInstance("SHA-256")
        .digest((before + '\u0000' + selected + '\u0000' + after).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(32)

    private fun mapOffset(value: Long, oldLength: Long, newLength: Long): Long {
        if (newLength <= 1) return 0
        if (oldLength <= 1) return value.coerceIn(0, newLength - 1)
        return (value.coerceIn(0, oldLength - 1).toDouble() / (oldLength - 1).toDouble() * (newLength - 1)).toLong().coerceIn(0, newLength - 1)
    }

    private fun remapSignature(oldLength: Long, newLength: Long): String = "$oldLength:$newLength"

    private inline fun <T> io(crossinline block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private data class Anchor(val before: String, val selected: String, val after: String, val hash: String) {
        companion object { val EMPTY = Anchor("", "", "", "") }
    }

    private companion object {
        val preparedRemaps = ConcurrentHashMap<String, String>()
        const val MAX_ANNOTATIONS = 20_000
        const val MAX_NOTE_CHARS = 8 * 1024
        const val MAX_EXCERPT_CHARS = 512
        const val ANCHOR_CONTEXT_CP = 48
        const val MAX_ANCHOR_SELECTED_CP = 160
        const val MAX_ANCHOR_WINDOW_CP = 512
        const val SEARCH_RADIUS_CP = 8192L
        const val MAX_ANCHOR_CANDIDATES = 64
    }
}

private fun ReaderAnnotation.toEntity() = ReaderAnnotationEntity(id, bookId, sourceStart, sourceEnd, kind.name, style.name, note, excerpt, anchorBefore, anchorSelected, anchorAfter, anchorHash, createdAt, updatedAt)
private fun fromEntity(value: ReaderAnnotationEntity) = ReaderAnnotation(value.id, value.bookId, value.sourceStart, value.sourceEnd, enumOr(value.kind, ReaderAnnotationKind.HIGHLIGHT), enumOr(value.style, ReaderHighlightStyle.YELLOW), value.note, value.excerpt, value.anchorBefore, value.anchorSelected, value.anchorAfter, value.anchorHash, value.createdAt, value.updatedAt)
private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback
private fun toJson(value: ReaderAnnotation) = JSONObject().put("id", value.id).put("bookId", value.bookId).put("sourceStart", value.sourceStart).put("sourceEnd", value.sourceEnd).put("kind", value.kind.name).put("style", value.style.name).put("note", value.note).put("excerpt", value.excerpt).put("anchorBefore", value.anchorBefore).put("anchorSelected", value.anchorSelected).put("anchorAfter", value.anchorAfter).put("anchorHash", value.anchorHash).put("createdAt", value.createdAt).put("updatedAt", value.updatedAt)
private fun fromJson(value: JSONObject): ReaderAnnotation {
    val start = value.optLong("sourceStart", 0).coerceAtLeast(0)
    val end = value.optLong("sourceEnd", start).coerceAtLeast(start)
    return ReaderAnnotation(value.optString("id").take(80), value.optString("bookId").take(128), start, end, enumOr(value.optString("kind"), ReaderAnnotationKind.HIGHLIGHT), enumOr(value.optString("style"), ReaderHighlightStyle.YELLOW), value.optString("note").take(8 * 1024), value.optString("excerpt").take(512), value.optString("anchorBefore").take(256), value.optString("anchorSelected").take(512), value.optString("anchorAfter").take(256), value.optString("anchorHash").take(64), value.optLong("createdAt", 0).coerceAtLeast(0), value.optLong("updatedAt", 0).coerceAtLeast(0))
}
