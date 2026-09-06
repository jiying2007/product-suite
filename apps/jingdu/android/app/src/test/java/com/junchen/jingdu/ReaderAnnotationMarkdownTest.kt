package com.junchen.jingdu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAnnotationMarkdownTest {
    @Test
    fun exportUsesLocalizedLabelsAndProgressInsteadOfSourceOffsets() {
        val annotation = ReaderAnnotation(
            id = "note-1",
            bookId = "book-1",
            sourceStart = 250,
            sourceEnd = 280,
            kind = ReaderAnnotationKind.NOTE,
            style = ReaderHighlightStyle.YELLOW,
            excerpt = "本地摘录",
            note = "私人笔记",
            createdAt = 1,
            updatedAt = 1,
        )

        val markdown = buildAnnotationMarkdown(
            book = "测试书.txt",
            values = listOf(annotation),
            documentLength = 1_000,
            title = "净读标注",
            noteLabel = "笔记",
            highlightLabel = "高亮",
            progressLabel = { "全书 $it%" },
        )

        assertTrue(markdown.contains("# 净读标注"))
        assertTrue(markdown.contains("笔记 · 全书 25%"))
        assertTrue(markdown.contains("本地摘录"))
        assertTrue(markdown.contains("私人笔记"))
        assertFalse(markdown.contains(" @ 250"))
    }
}
