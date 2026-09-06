package com.junchen.jingdu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibraryMetadata(
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val pendingProgress: Long? = null,
    val pendingNormalizedSha256: String? = null,
)

internal class LibraryMetadataStore(context: Context) {
    private val prefs = context.getSharedPreferences("jingdu.library.metadata.v1", Context.MODE_PRIVATE)
    private val cache = HashMap<String, LibraryMetadata>()

    @Synchronized
    fun load(bookId: String): LibraryMetadata {
        cache[bookId]?.let { return it }
        val raw = prefs.getString(bookId, null)
        val value = if (raw == null) LibraryMetadata() else runCatching {
            val json = JSONObject(raw)
            val tags = json.optJSONArray("tags") ?: JSONArray()
            val pendingSha = json.optString("pendingNormalizedSha256").takeIf(::validSha256)
            LibraryMetadata(
                favorite = json.optBoolean("favorite", false),
                tags = normalizeTags(buildList {
                    for (index in 0 until tags.length()) add(tags.optString(index))
                }),
                pendingProgress = if (pendingSha != null && json.has("pendingProgress")) json.optLong("pendingProgress").coerceAtLeast(0) else null,
                pendingNormalizedSha256 = pendingSha,
            )
        }.getOrDefault(LibraryMetadata())
        cache[bookId] = value
        return value
    }

    @Synchronized
    fun all(): Map<String, LibraryMetadata> = prefs.all.keys
        .filter(::validSha256)
        .associateWith(::load)

    fun toggleFavorite(bookId: String): LibraryMetadata {
        val current = load(bookId)
        return save(bookId, current.copy(favorite = !current.favorite))
    }

    fun setTags(bookId: String, input: String): LibraryMetadata {
        val current = load(bookId)
        return save(bookId, current.copy(tags = normalizeTags(input.split(',', '，', ';', '；', '\n'))))
    }

    /**
     * Restore portable user-owned library metadata without restoring book bytes. Progress is staged
     * against the normalized revision and is consumed only when that exact source/revision exists.
     */
    fun restorePortable(
        bookId: String,
        favorite: Boolean,
        tags: List<String>,
        normalizedSha256: String?,
        progress: Long?,
    ): LibraryMetadata {
        require(validSha256(bookId)) { "invalid book id" }
        val safeSha = normalizedSha256?.takeIf(::validSha256)
        val safeProgress = if (safeSha != null && progress != null) progress.coerceAtLeast(0) else null
        return save(
            bookId,
            LibraryMetadata(
                favorite = favorite,
                tags = normalizeTags(tags),
                pendingProgress = safeProgress,
                pendingNormalizedSha256 = if (safeProgress != null) safeSha else null,
            ),
        )
    }

    /** Return and clear staged progress only for the exact normalized revision. */
    @Synchronized
    fun consumeRestoredProgress(bookId: String, normalizedSha256: String): Long? {
        if (!validSha256(bookId) || !validSha256(normalizedSha256)) return null
        val current = load(bookId)
        if (current.pendingNormalizedSha256 != normalizedSha256 || current.pendingProgress == null) return null
        val progress = current.pendingProgress.coerceAtLeast(0)
        save(bookId, current.copy(pendingProgress = null, pendingNormalizedSha256 = null))
        return progress
    }

    @Synchronized
    fun clear(bookId: String) {
        cache.remove(bookId)
        prefs.edit().remove(bookId).apply()
    }

    @Synchronized
    private fun save(bookId: String, value: LibraryMetadata): LibraryMetadata {
        val normalized = value.copy(tags = normalizeTags(value.tags))
        val json = JSONObject()
            .put("favorite", normalized.favorite)
            .put("tags", JSONArray(normalized.tags))
        val pendingSha = normalized.pendingNormalizedSha256?.takeIf(::validSha256)
        val pendingProgress = normalized.pendingProgress
        if (pendingSha != null && pendingProgress != null) {
            json.put("pendingNormalizedSha256", pendingSha)
            json.put("pendingProgress", pendingProgress.coerceAtLeast(0))
        }
        cache[bookId] = normalized
        prefs.edit().putString(bookId, json.toString()).apply()
        return normalized
    }

    private fun normalizeTags(values: List<String>): List<String> = values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { it.take(MAX_TAG_CHARS) }
        .distinct()
        .take(MAX_TAGS)

    private fun validSha256(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private companion object {
        const val MAX_TAGS = 12
        const val MAX_TAG_CHARS = 24
    }
}
