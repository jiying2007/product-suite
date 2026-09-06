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

/** Android TextToSpeech transport with exact source-offset projection for presented speech text. */
internal class TtsController(context: Context) : AutoCloseable {
    interface Listener {
        fun onPosition(offset: Long)
        fun onStopped(reason: String?)
        fun onRange(sourceStart: Long, sourceEnd: Long) = Unit
        fun onPaused() = Unit
        fun onResumed() = Unit
    }

    data class VoiceOption(val name: String, val label: String)
    private data class SpokenChunk(val text: String, val projection: TextProjection)

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
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
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) applyDesiredVoice()
        }
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val token = parseToken(utteranceId)
                main.post {
                    val chunk = currentChunk
                    val activeListener = listener
                    if (token != generation.get() || activeListener == null || chunk == null || chunk.text.isEmpty()) return@post
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
                val token = parseToken(utteranceId)
                main.post {
                    if (token != generation.get() || pausedForFocus || reader == null) return@post
                    if (pendingNextOffset > offset) {
                        offset = pendingNextOffset
                        listener?.onPosition(offset)
                    }
                    speakNext(token)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { stop("tts error: $errorCode") }
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                main.post { stop("tts error") }
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
        stop(null)
        if (!ready) {
            listener.onStopped("TTS engine not ready")
            return
        }
        chineseMode = mode
        chineseOverrides = overrides
        val documentLocale = runCatching { detectDocumentLocale(reader.page()) }.getOrDefault(Locale.getDefault())
        val voiceApplied = desiredVoiceName.isNotEmpty() && applyDesiredVoice(mode)
        if (!voiceApplied) {
            val selectedLocale = TtsLocalePolicy.choose(
                mode = mode,
                documentLocale = documentLocale,
                systemLocale = Locale.getDefault(),
                isSupported = { locale -> tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE },
            )
            if (selectedLocale == null || tts.setLanguage(selectedLocale) < TextToSpeech.LANG_AVAILABLE) {
                listener.onStopped("tts error: no compatible voice")
                return
            }
            preferredLocale = selectedLocale
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
        if (pausedForFocus || token != generation.get()) return
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
            listener?.onPosition(offset)
            val sourceEnd = (offset + sourceToSpoken.sourceCodePoints).coerceAtMost(sourceChunk.nextOffset)
            listener?.onRange(offset, sourceEnd.coerceAtLeast(offset + 1))
            if (tts.speak(spoken.text, TextToSpeech.QUEUE_FLUSH, Bundle(), token.toString()) == TextToSpeech.ERROR) {
                stop("tts error: speak failed")
            }
        } catch (error: Exception) {
            stop(error.message ?: "tts error")
        }
    }

    override fun close() {
        stop(null)
        tts.shutdown()
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

    private fun parseToken(id: String?): Long = id?.toLongOrNull() ?: -1L

    private companion object {
        const val HANS_MARKERS = "这为后发国书读时会里还进对从个们来说现学与体门见风东语网无龙边开长"
        const val HANT_MARKERS = "這為後發國書讀時會裡還進對從個們來說現學與體門見風東語網無龍邊開長"
        const val HK_MARKERS = "係嘅唔嗰佢哋冇喺咁啲嚟咗"
    }
}
