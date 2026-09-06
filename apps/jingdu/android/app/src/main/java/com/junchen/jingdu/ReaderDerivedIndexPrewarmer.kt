package com.junchen.jingdu

import android.content.Context

/**
 * Builds revision-keyed reader metadata while the immutable normalized document is already open.
 * Import/redecode workers call this outside the reading frame path, so the first Chapters open can
 * consume the same Smart TOC report without starting another full-document analysis.
 */
internal object ReaderDerivedIndexPrewarmer {
    @JvmStatic
    fun prewarm(
        context: Context,
        bookId: String,
        revision: String,
        sourceLength: Long,
        reader: ReaderController,
    ) {
        if (sourceLength <= 0L || bookId.isBlank() || revision.isBlank()) return
        val store = SmartTocCacheStore(context.applicationContext)
        if (store.load(bookId, revision, sourceLength) != null) return
        store.save(bookId, revision, sourceLength, SmartToc.analyze(reader))
    }
}
