package com.junchen.jingdu

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal fun ttsUtteranceGeneration(utteranceId: String?): Long =
    utteranceId?.toLongOrNull() ?: -1L

/** Android TextToSpeech transport with exact source-offset projection for presented speech text. */
internal class TtsController(
    context: Context,
    private val queueObserver: QueueObserver? = null,
    engineName: String? = null,
) : AutoCloseable {
    interface Listener {
        fun onPosition(offset: Long)
        fun onStopped(reason: String?)
        fun onChunkQueued(sourceOffset: Long, nextOffset: Long) = Unit
        fun onRange(sourceStart: Long, sourceEnd: Long) = Unit
        fun onPaused() = Unit
        fun onResumed() = Unit
    }

    data class VoiceOption(val name: String, val label: String)
    data class EngineOption(val name: String, val label: String)

    /** Benchmark/test observability only. Production callers use the default null observer. */
    interface QueueObserver {
        fun onEngineReady()
        fun onChunkQueued(sample: QueueSample)
    }

    data class QueueSample(
        val durationNs: Long,
        val engine: String,
        val voice: String,
        val locale: String,
        val sourceOffset: Long,
        val nextOffset: Long,
        val spokenUtf16Chars: Int,
    )

    private data class SpokenChunk(val text: String, val projection: TextProjection)

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private val readyCallbacks = mutableListOf<() -> Unit>()
    private val pronunciation = TtsPronunciationStore(context.applicationContext)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> main.post(::resumeAfterFocus)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> main.post(::pauseForFocus)
                AudioManager.AUDIOFOCUS_LOSS -> main.post { stop("audio focus") }
            }
        }, main)
        .build()
    private val tts: TextToSpeech

    @Volatile private var ready = false
    @Volatile private var closed = false
    private var pausedForFocus = false
    private var resumeOnFocusGain = false
    private var desiredVoiceName = ""
    private var preferredLocale: Locale = Locale.getDefault()
    private var reader: ReaderController? = null
    private var listener: Listener? = null
    private var offset = 0L
    private var pendingNextOffset = 0L
    private var currentChunkOffset = 0L
    private var currentChunk: SpokenChunk? = null
    private var chineseMode = ChineseDisplayMode.ORIGINAL
    private var chineseOverrides = ""

    init {
        val initListener = TextToSpeech.OnInitListener { status ->
            if (closed) return@OnInitListener
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyDesiredVoice()
                main.post {
                    if (closed) return@post
                    val callbacks = synchronized(readyCallbacks) {
                        readyCallbacks.toList().also { readyCallbacks.clear() }
                    }
                    callbacks.forEach { callback -> runCatching(callback) }
                    queueObserver?.let { observer -> runCatching { observer.onEngineReady() } }
                }
            }
        }
        val requestedEngine = engineName?.trim().orEmpty()
        tts = if (requestedEngine.isEmpty()) {
            TextToSpeech(context.applicationContext, initListener)
        } else {
            TextToSpeech(context.applicationContext, initListener, requestedEngine)
        }
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val token = ttsUtteranceGeneration(utteranceId)
                main.post {
                    val chunk = currentChunk
                    val activeListener = listener
                    if (closed || token != generation.get() || activeListener == null || chunk == null || chunk.text.isEmpty()) return@post
                    val relative = ReaderTextPresentation.sourceRangeForDisplayUtf16(
                        chunk.text,
                        chunk.projection,
                        start,
                        end,
                    )
                    val sourceStart = relative.first.coerceIn(0, chunk.projection.sourceCodePoints)
                    val sourceEnd = if (relative.isEmpty()) {
                        (sourceStart + 1).coerceAtMost(chunk.projection.sourceCodePoints)
                    } else {
                        (relative.last + 1).coerceAtMost(chunk.projection.sourceCodePoints)
                    }
                    activeListener.onRange(
                        currentChunkOffset + sourceStart,
                        currentChunkOffset + sourceEnd.coerceAtLeast(sourceStart + 1),
                    )
                }
            }

            override fun onDone(utteranceId: String?) {
                val token = ttsUtteranceGeneration(utteranceId)
                main.post { completeUtterance(token) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val token = ttsUtteranceGeneration(utteranceId)
                main.post {
                    if (closed || token != generation.get()) return@post
                    stop("tts error: $errorCode")
                }
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                val token = ttsUtteranceGeneration(utteranceId)
                main.post {
                    if (closed || token != generation.get()) return@post
                    stop("tts error")
                }
            }
        })
    }

    fun start(
        reader: ReaderController,
        from: Long,
        mode: ChineseDisplayMode,
        overrides: String,
        listener: Listener,
    ) {
        if (closed) {
            listener.onStopped("tts error: controller closed")
            return
        }
        stop(null)
        if (!ready) {
            listener.onStopped("TTS engine not ready")
            return
        }
        chineseMode = mode
        chineseOverrides = overrides
        val documentLocale = runCatching { detectDocumentLocale(reader.page()) }.getOrDefault(Locale.getDefault())
        val voiceApplied = desiredVoiceName.isNotEmpty() && applyDesiredVoice(mode)
        if (!voiceApplied && !applyOfflineVoice(mode, documentLocale)) {
            listener.onStopped("tts error: no offline voice")
            return
        }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            listener.onStopped("audio focus denied")
            return
        }
        this.reader = reader
        this.listener = listener
        offset = from.coerceAtLeast(0)
        pendingNextOffset = offset
        currentChunkOffset = offset
        currentChunk = null
        pausedForFocus = false
        resumeOnFocusGain = false
        speakNext(generation.incrementAndGet())
    }

    fun stop(reason: String?) {
        generation.incrementAndGet()
        resumeOnFocusGain = false
        pausedForFocus = false
        pendingNextOffset = offset
        currentChunk = null
        tts.stop()
        audio.abandonAudioFocusRequest(focus)
        val old = listener
        listener = null
        reader = null
        if (reason != null) old?.onStopped(reason)
    }

    fun isSpeaking(): Boolean = reader != null && !pausedForFocus

    fun runWhenReady(callback: () -> Unit) {
        main.post {
            if (closed) return@post
            if (ready) callback()
            else synchronized(readyCallbacks) { readyCallbacks += callback }
        }
    }

    fun setRate(rate: Float) { tts.setSpeechRate(rate.coerceIn(0.5f, 2f)) }
    fun setPitch(pitch: Float) { tts.setPitch(pitch.coerceIn(0.5f, 2f)) }

    fun setLanguage(locale: Locale?) {
        preferredLocale = locale ?: Locale.getDefault()
        tts.language = preferredLocale
    }

    fun setVoiceName(voiceName: String?) {
        desiredVoiceName = voiceName.orEmpty()
        if (ready) applyDesiredVoice()
    }

    fun previewVoice(voiceName: String, text: String): Boolean {
        if (!ready || text.isBlank()) return false
        val voice = tts.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.name == voiceName } ?: return false
        val previous = tts.voice
        return try {
            tts.voice = voice
            tts.speak(text.take(240), TextToSpeech.QUEUE_FLUSH, Bundle(), "preview-${System.nanoTime()}") != TextToSpeech.ERROR
        } finally {
            if (previous != null) tts.voice = previous
        }
    }

    fun installedEngines(): List<EngineOption> =
        tts.engines.orEmpty()
            .map { EngineOption(it.name, it.label?.toString().orEmpty().ifBlank { it.name }) }
            .distinctBy(EngineOption::name)
            .sortedBy { it.label.lowercase(Locale.ROOT) }

    fun offlineVoices(): List<VoiceOption> {
        val voices = tts.voices ?: return emptyList()
        if (!ready) return emptyList()
        val preferredLanguage = preferredLocale.language
        return voices.asSequence()
            .filter { !it.isNetworkConnectionRequired }
            .map { voice ->
                val language = voice.locale?.toLanguageTag().orEmpty()
                VoiceOption(voice.name, if (language.isEmpty()) voice.name else "$language · ${voice.name}")
            }
            .sortedWith(
                compareBy<VoiceOption> { if (it.label.lowercase(Locale.ROOT).startsWith(preferredLanguage.lowercase(Locale.ROOT))) 0 else 1 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
            .toList()
    }

    private fun applyDesiredVoice(mode: ChineseDisplayMode? = null): Boolean {
        if (!ready || desiredVoiceName.isEmpty()) return false
        val voice = tts.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.name == desiredVoiceName } ?: return false
        val voiceLocale = voice.locale ?: if (mode == null || mode == ChineseDisplayMode.ORIGINAL) Locale.getDefault() else return false
        if (mode != null && !TtsLocalePolicy.acceptsSavedVoice(mode, voiceLocale)) return false
        preferredLocale = voiceLocale
        tts.voice = voice
        return true
    }

    private fun applyOfflineVoice(mode: ChineseDisplayMode, documentLocale: Locale): Boolean {
        if (!ready) return false
        val offline = tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        if (offline.isEmpty()) return false
        val candidates = TtsLocalePolicy.candidates(mode, documentLocale, Locale.getDefault())
        val exact = candidates.firstNotNullOfOrNull { candidate ->
            offline.firstOrNull { voice ->
                val locale = voice.locale ?: return@firstOrNull false
                locale.toLanguageTag().equals(candidate.toLanguageTag(), ignoreCase = true)
            }
        }
        val languageFallback = candidates.firstNotNullOfOrNull { candidate ->
            offline.firstOrNull { voice ->
                voice.locale?.language?.equals(candidate.language, ignoreCase = true) == true
            }
        }
        val voice = exact ?: languageFallback ?: return false
        preferredLocale = voice.locale ?: documentLocale
        tts.voice = voice
        return true
    }

    private fun pauseForFocus() {
        if (reader == null || pausedForFocus) return
        resumeOnFocusGain = true
        pausedForFocus = true
        generation.incrementAndGet()
        tts.stop()
        listener?.onPaused()
    }

    private fun resumeAfterFocus() {
        if (reader == null || !pausedForFocus || !resumeOnFocusGain) return
        pausedForFocus = false
        resumeOnFocusGain = false
        pendingNextOffset = offset
        val token = generation.incrementAndGet()
        listener?.onResumed()
        speakNext(token)
    }

    private fun speakNext(token: Long) {
        val activeReader = reader ?: return
        if (closed || pausedForFocus || token != generation.get()) return
        val observer = queueObserver
        val scheduleStartedNs = if (observer != null) System.nanoTime() else 0L
        try {
            val sourceChunk = activeReader.speech(offset, chineseMode, chineseOverrides)
            if (sourceChunk.text.isBlank() || sourceChunk.nextOffset <= offset) {
                stop("end")
                return
            }
            val spoken = pronunciation.present(sourceChunk.text)
            val sourceToSpoken = sourceChunk.projection.compose(spoken.projection)
            currentChunkOffset = offset
            currentChunk = SpokenChunk(spoken.text, sourceToSpoken)
            pendingNextOffset = sourceChunk.nextOffset
            listener?.onChunkQueued(offset, pendingNextOffset)
            listener?.onPosition(offset)
            val sourceEnd = (offset + sourceToSpoken.sourceCodePoints).coerceAtMost(sourceChunk.nextOffset)
            listener?.onRange(offset, sourceEnd.coerceAtLeast(offset + 1))
            val speakResult = tts.speak(spoken.text, TextToSpeech.QUEUE_FLUSH, Bundle(), token.toString())
            if (speakResult == TextToSpeech.ERROR) {
                stop("tts error: speak failed")
                return
            }
            scheduleCompletionWatchdog(token)
            if (observer != null) {
                val durationNs = System.nanoTime() - scheduleStartedNs
                val activeVoice = tts.voice
                runCatching {
                    observer.onChunkQueued(
                        QueueSample(
                            durationNs = durationNs,
                            engine = tts.defaultEngine.orEmpty(),
                            voice = activeVoice?.name.orEmpty(),
                            locale = activeVoice?.locale?.toLanguageTag().orEmpty(),
                            sourceOffset = offset,
                            nextOffset = sourceChunk.nextOffset,
                            spokenUtf16Chars = spoken.text.length,
                        ),
                    )
                }
            }
        } catch (error: Exception) {
            stop(error.message ?: "tts error")
        }
    }

    private fun completeUtterance(token: Long) {
        if (closed || token != generation.get() || pausedForFocus || reader == null) return
        if (pendingNextOffset > offset) {
            offset = pendingNextOffset
            listener?.onPosition(offset)
        }
        speakNext(generation.incrementAndGet())
    }

    private fun scheduleCompletionWatchdog(token: Long) {
        main.postDelayed(
            object : Runnable {
                override fun run() {
                    if (closed || token != generation.get() || pausedForFocus || reader == null) return
                    if (!tts.isSpeaking) {
                        completeUtterance(token)
                    } else {
                        main.postDelayed(this, TTS_COMPLETION_WATCHDOG_POLL_MS)
                    }
                }
            },
            TTS_COMPLETION_WATCHDOG_INITIAL_MS,
        )
    }

    override fun close() {
        if (closed) return
        stop(null)
        closed = true
        synchronized(readyCallbacks) { readyCallbacks.clear() }
        main.removeCallbacksAndMessages(null)
        runCatching { tts.shutdown() }
    }

    private fun detectDocumentLocale(text: String): Locale {
        var hans = 0
        var hant = 0
        var hk = 0
        var latin = 0
        var cjk = 0
        var cursor = 0
        while (cursor < text.length) {
            val cp = text.codePointAt(cursor)
            cursor += Character.charCount(cp)
            if (cp in 0x4E00..0x9FFF) cjk++
            if (cp in 'A'.code..'Z'.code || cp in 'a'.code..'z'.code) latin++
            if (HANS_MARKERS.indexOf(cp.toChar()) >= 0) hans++
            if (HANT_MARKERS.indexOf(cp.toChar()) >= 0) hant++
            if (HK_MARKERS.indexOf(cp.toChar()) >= 0) hk++
        }
        return when {
            hk >= 2 && hk >= hant / 3 -> Locale.forLanguageTag("zh-HK")
            hant > hans -> Locale.forLanguageTag("zh-TW")
            hans > 0 || cjk >= latin -> Locale.forLanguageTag("zh-CN")
            latin > cjk * 2 -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
    }


    private companion object {
        const val HANS_MARKERS = "这为后发国书读时会里还进对从个们来说现学与体门见风东语网无龙边开长"
        const val HANT_MARKERS = "這為後發國書讀時會裡還進對從個們來說現學與體門見風東語網無龍邊開長"
        const val HK_MARKERS = "係嘅唔嗰佢哋冇喺咁啲嚟咗"
        const val TTS_COMPLETION_WATCHDOG_INITIAL_MS = 1_000L
        const val TTS_COMPLETION_WATCHDOG_POLL_MS = 500L
    }
}
