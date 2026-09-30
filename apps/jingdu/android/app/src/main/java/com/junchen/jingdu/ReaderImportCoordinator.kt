package com.junchen.jingdu

import android.net.Uri

/** File/repository import orchestration kept outside MainActivity lifecycle/UI concerns. */
internal class ReaderImportCoordinator(
    private val repository: BookRepository,
) {
    data class BatchResult(
        val imported: Int,
        val failed: Int,
        val selected: Int,
        val requested: Int,
    )

    fun importUri(uri: Uri): BookRepository.Book =
        repository.importUri(uri, BookRepository.AUTO)

    fun importSharedText(text: String, displayName: String): BookRepository.Book =
        repository.importSharedText(text, displayName)

    fun importBatch(uris: List<Uri>, maxFiles: Int): BatchResult {
        val selected = uris.take(maxFiles.coerceAtLeast(1))
        var imported = 0
        var failed = 0
        selected.forEach { uri ->
            try {
                repository.importUri(uri, BookRepository.AUTO)
                imported++
            } catch (_: Throwable) {
                failed++
            }
        }
        return BatchResult(
            imported = imported,
            failed = failed,
            selected = selected.size,
            requested = uris.size,
        )
    }
}
