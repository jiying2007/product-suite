package com.junchen.jingdu

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Benchmark-build only. Never merged into the production manifest/source set. */
class ReaderBenchmarkFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return Bundle.EMPTY
        return when (method) {
            "seed" -> {
                val mib = (arg?.toIntOrNull() ?: 10).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Novel ${mib} MiB.txt")
                val target = mib.toLong() * 1024L * 1024L
                if (!fixture.isFile || fixture.length() < target) writeFixture(fixture, target)
                val repository = BookRepository(context)
                val existing = repository.list().firstOrNull { it.name == fixture.name }
                val book = existing ?: repository.importUri(Uri.fromFile(fixture), BookRepository.AUTO)
                Bundle().apply {
                    putString("bookId", book.id)
                    putLong("bytes", fixture.length())
                    putInt("mib", mib)
                }
            }
            "fixtureInfo" -> {
                val mib = (arg?.toIntOrNull() ?: 10).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Novel ${mib} MiB.txt")
                check(fixture.isFile) { "Benchmark fixture is not seeded: ${fixture.name}" }
                val repository = BookRepository(context)
                val book = repository.list().firstOrNull { it.name == fixture.name }
                    ?: error("Benchmark fixture is not imported: ${fixture.name}")
                Bundle().apply {
                    putString("bookId", book.id)
                    putLong("bytes", fixture.length())
                    putInt("mib", mib)
                    putString("sha256", sha256(fixture))
                }
            }
            "prepareSource" -> {
                val mib = (arg?.toIntOrNull() ?: 20).coerceIn(1, 256)
                val fixture = sourceFixture(context.cacheDir, mib)
                val target = mib.toLong() * 1024L * 1024L
                if (!fixture.isFile || fixture.length() < target) writeFixture(fixture, target)
                Bundle().apply {
                    putString("uri", sourceUri(mib).toString())
                    putLong("bytes", fixture.length())
                    putInt("mib", mib)
                    putString("sha256", sha256(fixture))
                }
            }
            "removeImportedSource" -> {
                val mib = (arg?.toIntOrNull() ?: 20).coerceIn(1, 256)
                val fixture = sourceFixture(context.cacheDir, mib)
                check(fixture.isFile) { "Benchmark source fixture is not prepared: ${fixture.name}" }
                val sourceSha = sha256(fixture)
                val repository = BookRepository(context)
                val existing = repository.list().firstOrNull { it.id == sourceSha }
                if (existing != null) repository.delete(existing)
                Bundle().apply {
                    putString("sha256", sourceSha)
                    putBoolean("deleted", existing != null)
                }
            }
            "chapterJumpMetric" -> {
                val mib = (arg?.toIntOrNull() ?: 10).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Novel ${mib} MiB.txt")
                check(fixture.isFile) { "Benchmark fixture is not seeded: ${fixture.name}" }
                val repository = BookRepository(context)
                val book = repository.list().firstOrNull { it.name == fixture.name }
                    ?: error("Benchmark fixture is not imported: ${fixture.name}")
                ReaderController().use { source ->
                    source.open(repository.normalizedFile(book), book.progress)
                    val prewarmed = source.chapters()
                    check(prewarmed.size >= 2) { "Benchmark fixture has insufficient chapters: ${prewarmed.size}" }

                    val startedNs = System.nanoTime()
                    val active = source.chapters()
                    val target = active[active.size / 2]
                    source.jump(target.offset)
                    val finalPosition = source.position()
                    val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000.0

                    check(finalPosition == target.offset) {
                        "Chapter jump position mismatch: target=${target.offset} final=$finalPosition"
                    }
                    Bundle().apply {
                        putString(
                            "sample",
                            buildString {
                                append("metric=chapter-jump")
                                append(";durationMs=").append(String.format(Locale.US, "%.3f", elapsedMs))
                                append(";fixtureMiB=").append(mib)
                                append(";fixtureSha256=").append(book.sourceSha256)
                                append(";normalizedSha256=").append(book.normalizedSha256)
                                append(";chapterCount=").append(active.size)
                                append(";targetOffset=").append(target.offset)
                                append(";finalPosition=").append(finalPosition)
                            },
                        )
                    }
                }
            }
            "indexedSearchMetric" -> {
                val mib = (arg?.toIntOrNull() ?: 10).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Novel ${mib} MiB.txt")
                check(fixture.isFile) { "Benchmark fixture is not seeded: ${fixture.name}" }
                val repository = BookRepository(context)
                val book = repository.list().firstOrNull { it.name == fixture.name }
                    ?: error("Benchmark fixture is not imported: ${fixture.name}")
                ReaderController().use { source ->
                    source.open(repository.normalizedFile(book), book.progress)
                    val prewarmed = source.search(EXACT_SEARCH_QUERY)
                    check(prewarmed.isNotEmpty()) { "Benchmark exact-search query has no prewarm hits" }

                    val startedNs = System.nanoTime()
                    val hits = source.search(EXACT_SEARCH_QUERY)
                    val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000.0

                    check(hits.isNotEmpty()) { "Benchmark exact-search query has no timed hits" }
                    check(hits.first().offset == prewarmed.first().offset) {
                        "Indexed exact-search first-hit drift: prewarm=${prewarmed.first().offset} timed=${hits.first().offset}"
                    }
                    Bundle().apply {
                        putString(
                            "sample",
                            buildString {
                                append("metric=indexed-exact-search")
                                append(";durationMs=").append(String.format(Locale.US, "%.3f", elapsedMs))
                                append(";fixtureMiB=").append(mib)
                                append(";fixtureSha256=").append(book.sourceSha256)
                                append(";normalizedSha256=").append(book.normalizedSha256)
                                append(";queryToken=quick_brown_fox")
                                append(";hitCount=").append(hits.size)
                                append(";firstOffset=").append(hits.first().offset)
                            },
                        )
                    }
                }
            }
            "seedSmartClean" -> {
                val mib = (arg?.toIntOrNull() ?: 20).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Smart Clean ${mib} MiB.txt")
                val target = mib.toLong() * 1024L * 1024L
                if (!fixture.isFile || fixture.length() < target) writeSmartCleanFixture(fixture, target)
                val repository = BookRepository(context)
                val existing = repository.list().firstOrNull { it.name == fixture.name }
                val book = existing ?: repository.importUri(Uri.fromFile(fixture), BookRepository.AUTO)
                Bundle().apply {
                    putString("bookId", book.id)
                    putLong("bytes", fixture.length())
                    putInt("mib", mib)
                    putString("sha256", sha256(fixture))
                    putString("normalizedSha256", book.normalizedSha256)
                }
            }
            "smartCleanMetric" -> {
                val mib = (arg?.toIntOrNull() ?: 20).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Smart Clean ${mib} MiB.txt")
                check(fixture.isFile) { "Smart Clean benchmark fixture is not seeded: ${fixture.name}" }
                val repository = BookRepository(context)
                val book = repository.list().firstOrNull { it.name == fixture.name }
                    ?: error("Smart Clean benchmark fixture is not imported: ${fixture.name}")
                ReaderController().use { source ->
                    source.open(repository.normalizedFile(book), 0)
                    val startedNs = System.nanoTime()
                    val candidates = source.noiseCandidates()
                    val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000.0
                    check(candidates.isNotEmpty()) { "Smart Clean benchmark produced no candidates for noisy fixture" }
                    val top = candidates.first()
                    val candidateSha256 = candidateSha256(candidates)
                    Bundle().apply {
                        putString(
                            "sample",
                            buildString {
                                append("metric=smart-clean-").append(mib).append("mib")
                                append(";durationMs=").append(String.format(Locale.US, "%.3f", elapsedMs))
                                append(";fixtureMiB=").append(mib)
                                append(";fixtureBytes=").append(book.size)
                                append(";fixtureSha256=").append(book.sourceSha256)
                                append(";normalizedSha256=").append(book.normalizedSha256)
                                append(";candidateCount=").append(candidates.size)
                                append(";candidateSha256=").append(candidateSha256)
                                append(";topScore=").append(top.score)
                                append(";topReason=").append(top.reason.replace(';', '_'))
                            },
                        )
                    }
                }
            }
            "ttsNextChunkMetric" -> {
                val mib = (arg?.toIntOrNull() ?: 10).coerceIn(1, 256)
                val fixture = File(context.cacheDir, "Benchmark Novel ${mib} MiB.txt")
                check(fixture.isFile) { "TTS benchmark fixture is not seeded: ${fixture.name}" }
                val repository = BookRepository(context)
                val book = repository.list().firstOrNull { it.name == fixture.name }
                    ?: error("TTS benchmark fixture is not imported: ${fixture.name}")

                ReaderController().use { reader ->
                    reader.open(repository.normalizedFile(book), 0)
                    val readyLatch = CountDownLatch(1)
                    val queuedLatch = CountDownLatch(1)
                    val queued = AtomicReference<TtsController.QueueSample?>(null)
                    val stopped = AtomicReference<String?>(null)
                    val controller = TtsController(
                        context,
                        object : TtsController.QueueObserver {
                            override fun onEngineReady() {
                                readyLatch.countDown()
                            }

                            override fun onChunkQueued(sample: TtsController.QueueSample) {
                                queued.set(sample)
                                queuedLatch.countDown()
                            }
                        },
                    )
                    try {
                        check(readyLatch.await(TTS_READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            "TTS benchmark engine did not become ready"
                        }
                        controller.start(
                            reader = reader,
                            from = 0,
                            mode = ChineseDisplayMode.ORIGINAL,
                            overrides = "",
                            listener = object : TtsController.Listener {
                                override fun onPosition(offset: Long) = Unit
                                override fun onStopped(reason: String?) {
                                    stopped.set(reason)
                                    queuedLatch.countDown()
                                }
                            },
                        )
                        check(queuedLatch.await(TTS_QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            "TTS benchmark queue event timed out"
                        }
                        val sample = queued.get()
                            ?: error("TTS benchmark did not queue a chunk: ${stopped.get().orEmpty()}")
                        check(sample.durationNs > 0L) { "TTS benchmark queue duration is invalid" }
                        check(sample.engine.isNotBlank()) { "TTS benchmark engine identity missing" }
                        check(sample.voice.isNotBlank()) { "TTS benchmark voice identity missing" }
                        check(sample.locale.isNotBlank()) { "TTS benchmark locale identity missing" }
                        check(sample.nextOffset > sample.sourceOffset) {
                            "TTS benchmark next offset did not advance: ${sample.sourceOffset} -> ${sample.nextOffset}"
                        }
                        check(sample.spokenUtf16Chars > 0) { "TTS benchmark queued empty spoken text" }
                        Bundle().apply {
                            putString(
                                "sample",
                                buildString {
                                    append("metric=tts-next-chunk")
                                    append(";durationMs=").append(
                                        String.format(Locale.US, "%.3f", sample.durationNs / 1_000_000.0),
                                    )
                                    append(";fixtureMiB=").append(mib)
                                    append(";fixtureSha256=").append(book.sourceSha256)
                                    append(";normalizedSha256=").append(book.normalizedSha256)
                                    append(";engine=").append(evidenceToken(sample.engine))
                                    append(";voice=").append(evidenceToken(sample.voice))
                                    append(";locale=").append(evidenceToken(sample.locale))
                                    append(";sourceOffset=").append(sample.sourceOffset)
                                    append(";nextOffset=").append(sample.nextOffset)
                                    append(";spokenUtf16Chars=").append(sample.spokenUtf16Chars)
                                },
                            )
                        }
                    } finally {
                        controller.close()
                    }
                }
            }
            "mode" -> {
                val mode = when (arg?.lowercase()) {
                    "paged" -> ReaderMode.PAGED
                    "continuous" -> ReaderMode.CONTINUOUS
                    else -> error("Unsupported Reader benchmark mode: $arg")
                }
                // Reset runtime-only launch evidence before every measured reader start. A source
                // position of zero is valid, so -1 remains the unambiguous not-rendered sentinel.
                ReaderInteractionRuntime.foregroundPosition = -1L
                ReaderInteractionRuntime.backgroundTtsPlaying = false
                ReaderInteractionRuntime.continuousReady = false
                ReaderInteractionRuntime.resetPagedLayoutReadiness()
                ReaderInteractionRuntime.resetVolumeDiagnostics()
                ReaderInteractionRuntime.resetPagedGestureDiagnostics()
                val preferences = ReaderPreferences(context)
                preferences.flush(
                    preferences.load().copy(
                        readingMode = mode,
                        pageAnimation = ReaderPageAnimation.SLIDE,
                        tapPagingEnabled = true,
                        swipePagingEnabled = true,
                        reversePagingGestures = false,
                        autoScrollEnabled = false,
                        // Match the production default. The benchmark must not keep controls resident
                        // for 60 seconds because that changes the measured reader composition workload.
                        controlsAutoHideMs = 3500L,
                        gestureCoachDismissed = true,
                        advancedGestureCustomizationEnabled = false,
                        centerTapAction = ReaderGestureAction.CONTROLS,
                        doubleTapAction = ReaderGestureAction.NONE,
                        doubleTapBookmarkEnabled = false,
                        volumeKeyMode = ReaderVolumeKeyMode.PAGE_WHEN_NOT_TTS,
                        reverseVolumeKeys = false,
                        hapticEnabled = false,
                    ),
                )
                // The shell-visible empty Bundle is the protocol ACK. DataStore flush is synchronous;
                // any write failure escapes above and makes the content command fail instead.
                Bundle.EMPTY
            }
            "position" -> Bundle().apply { putLong("position", ReaderInteractionRuntime.foregroundPosition) }
            "pageState" -> Bundle().apply {
                putLong("position", ReaderInteractionRuntime.foregroundPosition)
                putLong("layoutGeneration", ReaderInteractionRuntime.pagedLayoutGeneration)
            }
            "continuousReady" -> Bundle().apply { putLong("ready", if (ReaderInteractionRuntime.continuousReady) 1L else 0L) }
            "inputState" -> {
                // Read-only benchmark diagnostics. This exposes no navigation action and cannot alter
                // product state; it only proves whether the real Activity volume-key path reached
                // ReaderInteractionRuntime and which persisted/runtime eligibility inputs it observed.
                val settings = ReaderPreferences(context).load()
                Bundle().apply {
                    putLong("position", ReaderInteractionRuntime.foregroundPosition)
                    putLong("pagedLayoutGeneration", ReaderInteractionRuntime.pagedLayoutGeneration)
                    putString("readingMode", settings.readingMode.name)
                    putString("volumeKeyMode", settings.volumeKeyMode.name)
                    putBoolean("reverseVolumeKeys", settings.reverseVolumeKeys)
                    putBoolean("autoScrollEnabled", settings.autoScrollEnabled)
                    putBoolean("backgroundTtsPlaying", ReaderInteractionRuntime.backgroundTtsPlaying)
                    putLong("volumeEligibilityChecks", ReaderInteractionRuntime.volumeEligibilityChecks)
                    putBoolean("lastVolumeForegroundTtsPlaying", ReaderInteractionRuntime.lastVolumeForegroundTtsPlaying)
                    putBoolean("lastVolumeEligible", ReaderInteractionRuntime.lastVolumeEligible)
                    putBoolean("controlsVisible", ReaderInteractionRuntime.controlsVisible)
                    putLong("pagedGestureDowns", ReaderInteractionRuntime.pagedGestureDowns)
                    putLong("pagedTapCandidates", ReaderInteractionRuntime.pagedTapCandidates)
                    putLong("pagedCenterDispatches", ReaderInteractionRuntime.pagedCenterDispatches)
                    putLong("lastPagedGestureDurationMs", ReaderInteractionRuntime.lastPagedGestureDurationMs)
                    putFloat("lastPagedGestureDistancePx", ReaderInteractionRuntime.lastPagedGestureDistancePx)
                    putBoolean("lastPagedGestureConsumedByChild", ReaderInteractionRuntime.lastPagedGestureConsumedByChild)
                }
            }
            "clear" -> {
                val repository = BookRepository(context)
                repository.list().filter {
                    it.name.startsWith("Benchmark Novel ") || it.name.startsWith("Benchmark Smart Clean ")
                }.forEach(repository::delete)
                context.cacheDir.listFiles()?.filter {
                    it.name.startsWith("Benchmark Novel ") ||
                        it.name.startsWith("Benchmark New Source ") ||
                        it.name.startsWith("Benchmark Smart Clean ")
                }?.forEach(File::delete)
                Bundle.EMPTY
            }
            else -> super.call(method, arg, extras)
        }
    }

    private fun writeFixture(file: File, target: Long) {
        val heading = "第%05d章 Reader 基准阅读旅程\n"
        val body = "这是用于净读 Reader 性能与长文本稳定性验证的本地夹具。The quick brown fox jumps over the lazy dog.\n"
        FileOutputStream(file).buffered().use { output ->
            var bytes = 0L
            var chapter = 1
            while (bytes < target) {
                val title = heading.format(chapter++).toByteArray(Charsets.UTF_8)
                output.write(title)
                bytes += title.size
                repeat(BODY_LINES_PER_CHAPTER) {
                    if (bytes >= target) return@repeat
                    val chunk = body.toByteArray(Charsets.UTF_8)
                    output.write(chunk)
                    bytes += chunk.size
                }
                if (bytes < target) {
                    output.write('\n'.code)
                    bytes++
                }
            }
            output.flush()
        }
    }

    private fun writeSmartCleanFixture(file: File, target: Long) {
        val cleanLine = "这是用于净读 Smart Clean 性能验证的正文段落。The quick brown fox jumps over the lazy dog.\n"
        val promoLine = "正文尾部推广提示：请收藏本站 www.reader-benchmark.invalid 最新网址 https://reader-benchmark.invalid\n"
        FileOutputStream(file).buffered().use { output ->
            var bytes = 0L
            var line = 0
            while (bytes < target) {
                val value = if (line % 32 == 31) promoLine else cleanLine
                val chunk = value.toByteArray(Charsets.UTF_8)
                output.write(chunk)
                bytes += chunk.size
                line++
            }
            output.flush()
        }
    }

    private fun evidenceToken(value: String): String =
        value.replace(';', '_').replace('\n', '_').replace('\r', '_')

    private fun candidateSha256(candidates: List<ReaderController.NoiseCandidate>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        candidates.forEach { candidate ->
            val packed = buildString {
                append(candidate.score).append('\u001f')
                append(candidate.count).append('\u001f')
                append(candidate.reason).append('\u001f')
                append(candidate.text).append('\u001e')
            }
            digest.update(packed.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun sourceFixture(cacheDir: File, mib: Int): File =
        File(cacheDir, "Benchmark New Source ${mib} MiB.txt")

    private fun sourceUri(mib: Int): Uri =
        Uri.parse("content://com.junchen.jingdu.benchmarkfixture/source/$mib")

    private fun sourceFile(uri: Uri): File? {
        if (uri.authority != "com.junchen.jingdu.benchmarkfixture") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "source") return null
        val mib = segments[1].toIntOrNull()?.coerceIn(1, 256) ?: return null
        val context = context ?: return null
        return sourceFixture(context.cacheDir, mib).takeIf(File::isFile)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = sourceFile(uri) ?: return null
        val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns.toTypedArray(), 1)
        val row = cursor.newRow()
        columns.forEach { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = if (sourceFile(uri) != null) "text/plain" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        check(!mode.contains('w')) { "Benchmark source provider is read-only" }
        val file = sourceFile(uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private companion object {
        // ~25 KiB/chapter keeps 10 MiB at hundreds of chapters and 100 MiB at thousands:
        // still a heavy novel fixture without the previous pathological one-heading-per-paragraph bias.
        const val BODY_LINES_PER_CHAPTER = 256
        const val EXACT_SEARCH_QUERY = "quick brown fox"
        const val TTS_READY_TIMEOUT_SECONDS = 12L
        const val TTS_QUEUE_TIMEOUT_SECONDS = 5L
    }
}
