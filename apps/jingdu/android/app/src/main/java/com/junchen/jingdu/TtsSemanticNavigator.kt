package com.junchen.jingdu

import java.text.BreakIterator
import java.util.Locale

/** Bounded source-offset navigation for TTS. Never scans the whole book. */
internal object TtsSemanticNavigator {
    fun previousSentence(reader: ReaderController, position: Long, locale: Locale = Locale.getDefault()): Long =
        previousBoundary(reader, position, locale, sentence = true)

    fun nextSentence(reader: ReaderController, position: Long, locale: Locale = Locale.getDefault()): Long =
        nextBoundary(reader, position, locale, sentence = true)

    fun previousParagraph(reader: ReaderController, position: Long): Long =
        previousBoundary(reader, position, Locale.getDefault(), sentence = false)

    fun nextParagraph(reader: ReaderController, position: Long): Long =
        nextBoundary(reader, position, Locale.getDefault(), sentence = false)

    /** Pure helpers keep the semantic boundary algorithm host-testable without JNI or a whole-book scan. */
    internal fun previousSentenceOffset(text: String, locale: Locale): Long {
        if (text.isEmpty()) return 0
        val breaker = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        var boundary = breaker.preceding(text.length)
        if (boundary == BreakIterator.DONE) return 0
        // If we're exactly at a sentence start, Previous means the preceding sentence.
        if (boundary >= text.length - 2) boundary = breaker.preceding(boundary)
        if (boundary == BreakIterator.DONE) return 0
        return text.codePointCount(0, boundary).toLong()
    }

    internal fun nextSentenceOffset(text: String, locale: Locale): Long {
        if (text.isEmpty()) return 0
        val breaker = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        var boundary = breaker.following(0)
        if (boundary == BreakIterator.DONE) return text.codePointCount(0, text.length).toLong()
        while (boundary < text.length && text.substring(0, boundary).isBlank()) {
            boundary = breaker.following(boundary)
            if (boundary == BreakIterator.DONE) return 0
        }
        return text.codePointCount(0, boundary).toLong()
    }

    internal fun previousParagraphOffset(text: String): Long {
        if (text.isEmpty()) return 0
        val normalized = text.replace("\r\n", "\n")
        val cursor = normalized.trimEnd().lastIndexOf("\n\n")
        if (cursor < 0) return 0
        var next = cursor + 2
        while (next < normalized.length && normalized[next].isWhitespace()) next++
        return normalized.codePointCount(0, next).toLong()
    }

    internal fun nextParagraphOffset(text: String): Long {
        if (text.isEmpty()) return 0
        val normalized = text.replace("\r\n", "\n")
        val cursor = normalized.indexOf("\n\n", startIndex = 1)
        if (cursor < 0) return normalized.codePointCount(0, normalized.length).toLong()
        var next = cursor + 2
        while (next < normalized.length && normalized[next].isWhitespace()) next++
        return normalized.codePointCount(0, next).toLong()
    }

    private fun previousBoundary(reader: ReaderController, position: Long, locale: Locale, sentence: Boolean): Long {
        if (position <= 0 || reader.length() <= 0) return 0
        val start = (position - WINDOW_CP).coerceAtLeast(0)
        val text = reader.readAt(start, position - start)
        if (text.isEmpty()) return start
        val local = if (sentence) previousSentenceOffset(text, locale) else previousParagraphOffset(text)
        return (start + local).coerceIn(0, position)
    }

    private fun nextBoundary(reader: ReaderController, position: Long, locale: Locale, sentence: Boolean): Long {
        val length = reader.length()
        if (length <= 0 || position >= length - 1) return (length - 1).coerceAtLeast(0)
        val text = reader.readAt(position, minOf(WINDOW_CP, length - position))
        if (text.isEmpty()) return position
        val local = if (sentence) nextSentenceOffset(text, locale) else nextParagraphOffset(text)
        return (position + local).coerceAtMost(length - 1)
    }

    private const val WINDOW_CP = 4096L
}
