package com.junchen.jingdu

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SKIM_WHITESPACE = Regex("\\s+")

internal class ReaderSkimController(context: Context, private val bookId: String) : AutoCloseable {
    private val engine = ReaderViewportEngine(context.applicationContext, bookId)
    @Volatile private var documentLength = 0L

    suspend fun preview(
        fraction: Float,
        originOffset: Long,
        settings: ReaderSettings,
        chapters: List<ChapterModel>,
        charsPerMinute: Double,
    ): ReaderSkimPreview = withContext(Dispatchers.IO) {
        val length = documentLength.takeIf { it > 0L } ?: engine.readAround(originOffset, settings).documentLength.also {
            documentLength = it
        }
        if (length <= 0) return@withContext ReaderSkimPreview(0f, 0, null, "", 0, 0, null, null, originOffset)
        val offset = (fraction.coerceIn(0f, 1f) * (length - 1).toFloat()).toLong().coerceIn(0, length - 1)
        val window = engine.readAround(offset, settings)
        val localSource = (offset - window.start).coerceIn(0, window.map.sourceCodePoints)
        val displayCp = window.map.displayForSource(localSource)
        val utf = utf16Index(window.displayText, displayCp)
        val preview = readerSkimPreviewAround(window.displayText, utf)
        val chapterIndex = chapters.indexOfLast { it.offset <= offset }
        val chapter = chapters.getOrNull(chapterIndex)
        val chapterEnd = chapters.getOrNull(chapterIndex + 1)?.offset ?: length
        val chapterSpan = (chapterEnd - (chapter?.offset ?: 0L)).coerceAtLeast(1)
        val chapterProgress = (((offset - (chapter?.offset ?: 0L)).coerceAtLeast(0).toDouble() / chapterSpan.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        val bookProgress = ((offset.toDouble() / length.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        ReaderSkimPreview(
            fraction = fraction.coerceIn(0f, 1f),
            offset = offset,
            chapter = chapter?.title?.let { ReaderTextPresentation.chapterTitle(it, settings) },
            preview = preview,
            chapterProgressPercent = chapterProgress,
            bookProgressPercent = bookProgress,
            chapterRemainingMinutes = remaining(offset, chapterEnd, charsPerMinute),
            bookRemainingMinutes = remaining(offset, length, charsPerMinute),
            originOffset = originOffset,
        )
    }

    override fun close() = engine.close()

    private fun remaining(position: Long, end: Long, cpm: Double): Int? {
        if (end <= position || cpm <= 0) return null
        return kotlin.math.ceil((end - position).toDouble() / cpm.coerceAtLeast(1.0)).toInt().coerceAtLeast(1)
    }

    private fun utf16Index(text: String, codePoints: Long): Int {
        val total = text.codePointCount(0, text.length)
        return text.offsetByCodePoints(0, codePoints.coerceIn(0, total.toLong()).toInt())
    }
}

internal fun readerSkimPreviewAround(text: String, utf: Int): String {
    if (text.isBlank()) return ""
    val sourceSafe = utf.coerceIn(0, text.length)
    var removedBefore = 0
    for (index in 0 until sourceSafe) {
        if (text[index] == ReaderTypographySpec.PARAGRAPH_SPACER) removedBefore++
    }
    val clean = text.replace(ReaderTypographySpec.PARAGRAPH_SPACER.toString(), "")
    val safe = (sourceSafe - removedBefore).coerceIn(0, clean.length)
    var start = (safe - 90).coerceAtLeast(0)
    var end = (safe + 180).coerceAtMost(clean.length)
    if (start in 1 until clean.length && Character.isLowSurrogate(clean[start]) && Character.isHighSurrogate(clean[start - 1])) start--
    if (end in 1 until clean.length && Character.isLowSurrogate(clean[end]) && Character.isHighSurrogate(clean[end - 1])) end++
    return clean.substring(start, end).replace(SKIM_WHITESPACE, " ").trim()
}
