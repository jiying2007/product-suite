package com.junchen.jingdu

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Seeds Reader instrumentation with a real private BookRepository revision.
 *
 * ReaderViewportEngine deliberately re-opens the authoritative private source instead of trusting
 * AppUiState metadata. A clean emulator therefore needs the same source/document backing files that
 * production Reader sessions require; fake 64-character ids can only pass when stale app data leaks
 * between runs. Keep the fixture on the production import path so clean CI exercises that contract.
 */
internal object ReaderInstrumentationFixture {
    @Volatile private var cached: BookCardModel? = null

    @Synchronized
    fun book(context: Context): BookCardModel {
        val appContext = context.applicationContext
        val repository = BookRepository(appContext)
        cached?.let { candidate ->
            if (repository.list().any { it.id == candidate.id }) return candidate
        }

        val source = File(appContext.cacheDir, FIXTURE_NAME)
        if (!source.isFile || source.length() < MIN_FIXTURE_BYTES) {
            source.writeText(fixtureText(), Charsets.UTF_8)
        }

        val imported = repository.importUri(Uri.fromFile(source), "UTF-8")
        ReaderController(false).use { reader ->
            reader.open(repository.normalizedFile(imported), 0)
            repository.updateCharCount(imported, reader.length())
        }
        val model = BookCardModel(
            id = imported.id,
            name = "Long Novel.txt",
            encoding = imported.encoding,
            sizeBytes = imported.size,
            progress = 500,
            charCount = imported.charCount.coerceAtLeast(10_000),
            touchedAt = imported.touchedAt,
            normalizedSha256 = imported.normalizedSha256,
        )
        cached = model
        return model
    }

    private fun fixtureText(): String = buildString {
        append("Chapter 1\n")
        repeat(420) { index ->
            append("Paragraph ")
            append(index + 1)
            append(" keeps a stable Reader backing file. 中文标点，段落边界与分页内容保持确定。")
            append('\n')
        }
        append("Chapter 2\n")
        repeat(220) { index ->
            append("Second section ")
            append(index + 1)
            append(" keeps enough content for paging, progress, chapters and settings instrumentation.\n")
        }
    }

    private const val FIXTURE_NAME = "jingdu-reader-instrumentation-fixture.txt"
    private const val MIN_FIXTURE_BYTES = 12_000L
}
