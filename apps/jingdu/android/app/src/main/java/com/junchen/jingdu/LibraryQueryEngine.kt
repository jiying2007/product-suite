package com.junchen.jingdu

import java.util.Locale

/** Pure metadata-only library query path. Never opens book payloads. */
internal object LibraryQueryEngine {
    const val COLLECTION_PREFIX = "COLLECTION:"
    fun apply(
        books: List<BookCardModel>,
        query: String,
        filterName: String,
        sortName: String,
    ): List<BookCardModel> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val filtered = books.filter { book ->
            val matchesQuery = needle.isEmpty() ||
                stripTxtForLibrary(book.name).lowercase(Locale.ROOT).contains(needle) ||
                book.tags.any { it.lowercase(Locale.ROOT).contains(needle) }
            val matchesFilter = when {
                filterName == "FAVORITES" -> book.favorite
                filterName == "READING" -> book.status == LibraryBookStatus.READING
                filterName == "FINISHED" -> book.status == LibraryBookStatus.FINISHED
                filterName == "ATTENTION" -> book.healthScore != null && (book.healthScore < 90 || book.healthIssues > 0)
                filterName == "OPTIMIZED" -> book.optimized
                filterName.startsWith(COLLECTION_PREFIX) -> {
                    val collection = filterName.removePrefix(COLLECTION_PREFIX)
                    book.tags.any { it.equals(collection, ignoreCase = true) }
                }
                else -> true
            }
            matchesQuery && matchesFilter
        }
        return when (runCatching { LibrarySort.valueOf(sortName) }.getOrDefault(LibrarySort.RECENT)) {
            LibrarySort.RECENT -> filtered.sortedByDescending(BookCardModel::touchedAt)
            LibrarySort.NAME -> filtered.sortedBy { stripTxtForLibrary(it.name).lowercase(Locale.ROOT) }
            LibrarySort.PROGRESS -> filtered.sortedByDescending(BookCardModel::progressFraction)
        }
    }

    private fun stripTxtForLibrary(value: String): String =
        if (value.endsWith(".txt", ignoreCase = true)) value.dropLast(4) else value
}
