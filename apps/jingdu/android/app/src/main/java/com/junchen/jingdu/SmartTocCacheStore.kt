package com.junchen.jingdu

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Revision-keyed derived TOC cache. Only chapter metadata derived from the normalized private copy is
 * cached; source text is never persisted here. Corruption or eviction is harmless because Core can
 * deterministically rebuild the report.
 */
internal class SmartTocCacheStore(context: Context) {
    private val directory = File(context.cacheDir, "smart-toc-v1")

    fun load(bookId: String, revision: String, sourceLength: Long): TocQualityReport? = runCatching {
        val file = cacheFile(bookId, revision, sourceLength)
        if (!file.isFile) return@runCatching null
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return@runCatching null
            if (input.readUTF() != revision || input.readLong() != sourceLength) return@runCatching null
            val score = input.readInt()
            val duplicateTitles = input.readInt()
            val numericGaps = input.readInt()
            val suspiciousTitles = input.readInt()
            val count = input.readInt()
            if (count !in 0..MAX_CHAPTERS) return@runCatching null
            val chapters = ArrayList<SmartChapter>(count)
            repeat(count) {
                val offset = input.readLong()
                val title = input.readUTF().trim().take(MAX_TITLE_CHARS)
                val source = input.readUTF().trim().take(MAX_SOURCE_CHARS)
                val confidence = input.readInt().coerceIn(0, 100)
                if (offset in 0 until sourceLength.coerceAtLeast(1) && title.isNotEmpty()) {
                    chapters += SmartChapter(offset, title, source.ifEmpty { "core" }, confidence)
                }
            }
            TocQualityReport(chapters, score.coerceIn(0, 100), duplicateTitles.coerceAtLeast(0), numericGaps.coerceAtLeast(0), suspiciousTitles.coerceAtLeast(0))
        }
    }.getOrNull()

    fun save(bookId: String, revision: String, sourceLength: Long, report: TocQualityReport) {
        runCatching {
            directory.mkdirs()
            val target = cacheFile(bookId, revision, sourceLength)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(revision.take(256))
                output.writeLong(sourceLength)
                output.writeInt(report.score)
                output.writeInt(report.duplicateTitles)
                output.writeInt(report.numericGaps)
                output.writeInt(report.suspiciousTitles)
                val chapters = report.chapters.take(MAX_CHAPTERS)
                output.writeInt(chapters.size)
                chapters.forEach { chapter ->
                    output.writeLong(chapter.offset)
                    output.writeUTF(chapter.title.take(MAX_TITLE_CHARS))
                    output.writeUTF(chapter.source.take(MAX_SOURCE_CHARS))
                    output.writeInt(chapter.confidence)
                }
                output.flush()
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            pruneOtherRevisions(bookId, target)
        }
    }

    fun clear(bookId: String) {
        val prefix = "${bookKey(bookId)}-"
        directory.listFiles()?.forEach { file -> if (file.name.startsWith(prefix)) file.delete() }
    }

    private fun pruneOtherRevisions(bookId: String, keep: File) {
        val prefix = "${bookKey(bookId)}-"
        directory.listFiles()?.forEach { file ->
            if (file != keep && file.name.startsWith(prefix)) file.delete()
        }
    }

    private fun cacheFile(bookId: String, revision: String, sourceLength: Long): File {
        val safeRevision = revision.filter(Char::isLetterOrDigit).take(32).ifEmpty { "unknown" }
        return File(directory, "${bookKey(bookId)}-$safeRevision-$sourceLength.toc")
    }

    private fun bookKey(bookId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bookId.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val MAGIC = 0x4A544F43 // JTOC
        const val VERSION = 1
        const val MAX_CHAPTERS = 100_000
        const val MAX_TITLE_CHARS = 1_024
        const val MAX_SOURCE_CHARS = 32
    }
}
