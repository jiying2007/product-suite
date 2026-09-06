package com.junchen.jingdu

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

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
            "continuousReady" -> Bundle().apply { putLong("ready", if (ReaderInteractionRuntime.continuousReady) 1L else 0L) }
            "inputState" -> {
                // Read-only benchmark diagnostics. This exposes no navigation action and cannot alter
                // product state; it only proves whether the real Activity volume-key path reached
                // ReaderInteractionRuntime and which persisted/runtime eligibility inputs it observed.
                val settings = ReaderPreferences(context).load()
                Bundle().apply {
                    putLong("position", ReaderInteractionRuntime.foregroundPosition)
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
                repository.list().filter { it.name.startsWith("Benchmark Novel ") }.forEach(repository::delete)
                context.cacheDir.listFiles()?.filter { it.name.startsWith("Benchmark Novel ") }?.forEach(File::delete)
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

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = null

    private companion object {
        // ~25 KiB/chapter keeps 10 MiB at hundreds of chapters and 100 MiB at thousands:
        // still a heavy novel fixture without the previous pathological one-heading-per-paragraph bias.
        const val BODY_LINES_PER_CHAPTER = 256
    }
}
