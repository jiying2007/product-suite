package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class LibraryQueryEngineTest {
    @Test fun smartCollectionsFilterCorrectly() {
        val books = listOf(
            book(1, health = 70, issues = 2, optimized = false),
            book(2, health = 98, issues = 0, optimized = true),
            book(3, health = null, issues = 0, optimized = false),
        )
        assertEquals(listOf("Book 1.txt"), LibraryQueryEngine.apply(books, "", "ATTENTION", "RECENT").map { it.name })
        assertEquals(listOf("Book 2.txt"), LibraryQueryEngine.apply(books, "", "OPTIMIZED", "RECENT").map { it.name })
    }

    @Test fun tenThousandBookMetadataQueryStaysInteractive() {
        val books = (0 until 10_000).map { index ->
            book(
                index,
                health = if (index % 7 == 0) 72 else 98,
                issues = if (index % 7 == 0) 2 else 0,
                optimized = index % 5 == 0,
            ).copy(tags = listOf(if (index % 3 == 0) "科幻" else "novel"))
        }
        var result = emptyList<BookCardModel>()
        val elapsedNs = measureNanoTime {
            repeat(10) {
                result = LibraryQueryEngine.apply(books, "book 99", "ALL", "NAME")
                LibraryQueryEngine.apply(books, "", "ATTENTION", "RECENT")
                LibraryQueryEngine.apply(books, "", "OPTIMIZED", "PROGRESS")
            }
        }
        assertTrue(result.isNotEmpty())
        assertTrue("10k metadata query took ${elapsedNs / 1_000_000}ms", elapsedNs < 2_000_000_000L)
    }

    private fun book(index: Int, health: Int?, issues: Int, optimized: Boolean) = BookCardModel(
        id = index.toString().padStart(64, '0'),
        name = "Book $index.txt",
        encoding = "UTF-8",
        sizeBytes = 1024,
        progress = index.toLong(),
        charCount = 10_000,
        touchedAt = index.toLong(),
        normalizedSha256 = index.toString().padStart(64, 'a').takeLast(64),
        favorite = index % 2 == 0,
        healthScore = health,
        healthIssues = issues,
        optimized = optimized,
    )
}
