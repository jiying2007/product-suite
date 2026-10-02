package com.junchen.jingdu

import android.content.Context
import java.io.Closeable

internal data class TtsCatalogSnapshot(
    val voices: List<TtsVoiceModel>,
    val engines: List<TtsEngineModel>,
    val engineName: String,
)

internal fun normalizedTtsEnginePackage(packageName: String): String =
    packageName.trim().take(255)

/**
 * Owns the optional settings/preview TextToSpeech instance.
 *
 * Playback stays service-owned. This coordinator keeps MainActivity from managing a second engine's
 * readiness, replacement and shutdown details directly, and it remains lazy until voice/engine UI
 * or preview is actually used.
 */
internal class TtsCatalogCoordinator(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val engineStore = TtsEngineStore(appContext)
    private var catalog: TtsController? = null

    fun currentEngineName(): String = engineStore.load()

    fun sync(settings: ReaderSettings) {
        catalog?.let { active ->
            active.setRate(settings.ttsRate)
            active.setPitch(settings.ttsPitch)
            active.setVoiceName(settings.ttsVoiceName)
        }
    }

    fun preview(voiceName: String, sample: String, settings: ReaderSettings) {
        ensure(settings).let { active ->
            active.setRate(settings.ttsRate)
            active.setPitch(settings.ttsPitch)
            active.previewVoice(voiceName, sample)
        }
    }

    fun refresh(settings: ReaderSettings, onReady: (TtsCatalogSnapshot) -> Unit) {
        val active = ensure(settings)
        active.runWhenReady {
            if (catalog !== active) return@runWhenReady
            onReady(
                TtsCatalogSnapshot(
                    voices = active.offlineVoices().map { TtsVoiceModel(it.name, it.label) },
                    engines = active.installedEngines().map { TtsEngineModel(it.name, it.label) },
                    engineName = engineStore.load(),
                ),
            )
        }
    }

    fun isEngineInstalled(packageName: String, settings: ReaderSettings): Boolean {
        val selected = normalizedTtsEnginePackage(packageName)
        return selected.isEmpty() || ensure(settings).installedEngines().any { it.name == selected }
    }

    fun selectEngine(packageName: String) {
        engineStore.save(normalizedTtsEnginePackage(packageName))
        catalog?.close()
        catalog = null
    }

    override fun close() {
        catalog?.close()
        catalog = null
    }

    private fun ensure(settings: ReaderSettings): TtsController =
        catalog ?: TtsController(appContext, engineName = engineStore.load().ifBlank { null }).also { created ->
            created.setRate(settings.ttsRate)
            created.setPitch(settings.ttsPitch)
            created.setVoiceName(settings.ttsVoiceName)
            catalog = created
        }
}
