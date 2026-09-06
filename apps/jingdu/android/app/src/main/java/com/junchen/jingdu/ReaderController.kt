package com.junchen.jingdu

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Bounded Kotlin facade over the shared native Reader Core. */
internal class ReaderController(
    private val pageCacheEnabled: Boolean = true,
) : Closeable {
    data class Hit(val offset: Long, val context: String)
    data class Chapter(val offset: Long, val title: String)
    data class NoiseCandidate(val score: Int, val count: Int, val reason: String, val text: String)
    data class Speech(val nextOffset: Long, val text: String, val projection: TextProjection)
    private data class PageWindow(
        val file: File,
        val start: Long,
        val text: String,
        val index: ReaderCodePointIndex,
    ) {
        val codePoints: Long get() = index.codePointCount.toLong()
    }

    private val pageCacheGeneration = AtomicLong()
    private var handle = 0L
    @Volatile private var documentFile: File? = null
    @Volatile private var documentLength = 0L
    @Volatile private var documentPosition = 0L
    @Volatile private var pageWindow: PageWindow? = null

    @Throws(IOException::class)
    fun open(file: File, restoredPosition: Long) {
        close()
        handle = NativeCore.open(file)
        documentFile = file
        documentLength = NativeCore.nativeCharCount(handle)
        documentPosition = restoredPosition.coerceIn(0, (documentLength - 1).coerceAtLeast(0))
        if (pageCacheEnabled && documentLength > 0) primePageWindow(handle, file, documentLength, documentPosition)
    }

    @Throws(IOException::class)
    fun page(): String {
        ensureOpen()
        pageFromWindow(pageWindow, documentPosition)?.let { return it }
        return NativeCore.read(handle, documentPosition, WINDOW_CHARS)
    }

    @Throws(IOException::class)
    fun readAt(offset: Long, maximum: Long): String {
        ensureOpen()
        val safeOffset = offset.coerceIn(0, (documentLength - 1).coerceAtLeast(0))
        val safeMaximum = maximum.coerceIn(0, 64 * 1024L)
        return NativeCore.read(handle, safeOffset, safeMaximum)
    }

    fun documentFile(): File? = documentFile
    fun position(): Long = documentPosition
    fun length(): Long = documentLength

    fun jump(value: Long) {
        documentPosition = value.coerceIn(0, (documentLength - 1).coerceAtLeast(0))
        schedulePagePrefetchIfNeeded()
    }

    fun move(delta: Long) {
        val target = try {
            Math.addExact(documentPosition, delta)
        } catch (_: ArithmeticException) {
            if (delta >= 0) Long.MAX_VALUE else 0L
        }
        jump(target)
    }

    @Throws(IOException::class)
    fun search(query: String): List<Hit> {
        ensureOpen()
        val merged = linkedMapOf<Long, Hit>()
        variantLoop@ for (variant in ChineseTextConverter.searchVariants(query)) {
            if (variant.isBlank()) continue
            NativeCore.search(handle, variant, 500).lineSequence().forEach lineLoop@{ line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@lineLoop
                line.substring(0, tab).toLongOrNull()?.let { offset ->
                    merged.putIfAbsent(offset, Hit(offset, line.substring(tab + 1)))
                }
            }
            if (merged.size >= 500) break@variantLoop
        }
        return merged.values.sortedBy(Hit::offset).take(500)
    }

    @Throws(IOException::class)
    fun chapters(): List<Chapter> {
        ensureOpen()
        return NativeCore.chapters(handle, 20_000).lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            line.substring(0, tab).toLongOrNull()?.let { Chapter(it, line.substring(tab + 1)) }
        }.toList()
    }

    @Throws(IOException::class)
    fun noiseCandidates(): List<NoiseCandidate> {
        ensureOpen()
        val merged = linkedMapOf<String, NoiseCandidate>()
        NativeCore.noiseCandidates(handle, 80).lineSequence().forEach { line ->
            if (line.isEmpty()) return@forEach
            val fields = line.split('\t', limit = 4)
            if (fields.size != 4) return@forEach
            val score = fields[0].toIntOrNull() ?: return@forEach
            val count = fields[1].toIntOrNull() ?: return@forEach
            val candidate = NoiseCandidate(score, count, fields[2], fields[3])
            merged["${candidate.reason}\u001f${candidate.text}"] = candidate
        }
        documentFile?.let { file ->
            SmartCleanRefiner.scan(file, 40).forEach { refined ->
                val key = "${refined.reason}\u001f${refined.text}"
                val existing = merged[key]
                if (existing == null || refined.score > existing.score) {
                    merged[key] = NoiseCandidate(refined.score, refined.count, refined.reason, refined.text)
                }
            }
        }
        return merged.values
            .sortedWith(compareByDescending<NoiseCandidate> { it.score }.thenByDescending { it.count })
            .take(100)
    }

    /** Returns source nextOffset plus explicitly presented speech text and source↔speech projection. */
    @Throws(IOException::class)
    fun speech(from: Long, mode: ChineseDisplayMode, overrides: String): Speech {
        ensureOpen()
        val packed = NativeCore.speechChunk(handle, from, 900)
        val tab = packed.indexOf('\t')
        if (tab < 0) return Speech(from, "", TextProjection.identity(0))
        val next = packed.substring(0, tab).toLongOrNull()
            ?: throw IOException("invalid speech core response")
        val sourceText = packed.substring(tab + 1)
        val presented = ReaderTextPresentation.present(sourceText, mode, overrides)
        return Speech(next, presented.displayText, presented.projection)
    }

    @Throws(IOException::class)
    fun exportRules(packedRules: String, output: File) {
        ensureOpen()
        NativeCore.exportRules(handle, packedRules, output)
    }

    override fun close() {
        pageCacheGeneration.incrementAndGet()
        pageWindow = null
        if (handle != 0L) {
            NativeCore.nativeClose(handle)
            handle = 0L
        }
        documentFile = null
        documentLength = 0L
        documentPosition = 0L
    }

    @Throws(IOException::class)
    private fun primePageWindow(nativeHandle: Long, file: File, length: Long, target: Long) {
        val start = pageWindowStart(target, length)
        val text = NativeCore.read(nativeHandle, start, PAGE_CACHE_CHARS)
        pageWindow = pageWindow(file, start, text)
    }

    private fun pageFromWindow(window: PageWindow?, target: Long): String? {
        val file = documentFile
        if (!pageCacheEnabled || window == null || file == null || file != window.file) return null
        val relative = target - window.start
        if (relative < 0 || relative >= window.codePoints || relative > Int.MAX_VALUE) return null
        return window.index.slice(relative.toInt(), WINDOW_CHARS.toInt())
    }

    private fun schedulePagePrefetchIfNeeded() {
        if (!pageCacheEnabled) return
        val current = pageWindow ?: return
        val file = documentFile ?: return
        val length = documentLength
        val target = documentPosition
        if (length <= 0 || file != current.file) return
        val relative = target - current.start
        val nearStart = current.start > 0 && relative < PAGE_CACHE_PREFETCH_MARGIN_CHARS
        val nearEnd = current.start + current.codePoints < length &&
            relative + WINDOW_CHARS + PAGE_CACHE_PREFETCH_MARGIN_CHARS > current.codePoints
        if (!nearStart && !nearEnd) return

        val generation = pageCacheGeneration.incrementAndGet()
        PAGE_PREFETCH.execute {
            if (generation != pageCacheGeneration.get()) return@execute
            var temporary = 0L
            try {
                temporary = NativeCore.open(file)
                val start = pageWindowStart(target, length)
                val text = NativeCore.read(temporary, start, PAGE_CACHE_CHARS)
                val next = pageWindow(file, start, text)
                if (generation == pageCacheGeneration.get() && file == documentFile && documentLength == length) {
                    pageWindow = next
                }
            } catch (_: IOException) {
                // Opportunistic cache refill; the authoritative native read remains the fallback.
            } finally {
                if (temporary != 0L) NativeCore.nativeClose(temporary)
            }
        }
    }

    private fun pageWindow(file: File, start: Long, text: String): PageWindow =
        PageWindow(file, start, text, ReaderCodePointIndex.build(text))

    @Throws(IOException::class)
    private fun ensureOpen() {
        if (handle == 0L || documentFile == null) throw IOException("no document open")
    }

    companion object {
        const val MIN_PAGE_CHARS = 120L
        const val DEFAULT_PAGE_CHARS = 800L
        const val WINDOW_CHARS = 1536L
        const val PAGE_CACHE_CHARS = 64 * 1024L
        private const val PAGE_CACHE_ALIGN_CHARS = 4096L
        private const val PAGE_CACHE_BACK_CHARS = 8192L
        private const val PAGE_CACHE_PREFETCH_MARGIN_CHARS = 8192L
        private val PAGE_PREFETCH = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "jingdu-page-prefetch").apply { isDaemon = true }
        }

        private fun pageWindowStart(target: Long, documentLength: Long): Long {
            val preferred = (target - PAGE_CACHE_BACK_CHARS).coerceAtLeast(0)
            val aligned = (preferred / PAGE_CACHE_ALIGN_CHARS) * PAGE_CACHE_ALIGN_CHARS
            val maximum = (documentLength - PAGE_CACHE_CHARS).coerceAtLeast(0)
            return minOf(aligned, maximum)
        }
    }
}
