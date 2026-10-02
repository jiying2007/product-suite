package com.junchen.jingdu

import android.content.Context
import java.io.Closeable

internal data class TtsCatalogSnapshot(
    val voices: List<TtsVoiceModel>,
    val engines: List<TtsEngineModel>,
    val engineName: String,
)

/**
 * Owns the settings/preview TextToSpeech instance separately from playback service state.
 *
 * The coordinator stays lazy: users who never open TTS settings or preview a voice never allocate
 * this second engine. MainActivity only owns entitlement/navigation decisions and UI publication.
 */
internal class TtsCatalogCoordinator(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val engineStore = TtsEngineStore(appContext)
    private var catalog: TtsController? = null

    fun selectedEngineName(): String = engineStore.load()

    fun refresh(settings: ReaderSettings, publish: (TtsCatalogSnapshot) -> Unit) {
        val active = controller(settings)
        active.runWhenReady {
            if (catalog !== active) return@runWhenReady
            publish(
                TtsCatalogSnapshot(
                    voices = active.offlineVoices().map { TtsVoiceModel(it.name, it.label) },
                    engines = active.installedEngines().map { TtsEngineModel(it.name, it.label) },
                    engineName = engineStore.load(),
                ),
            )
        }
    }

    fun preview(settings: ReaderSettings, voiceName: String, sample: String) {
        controller(settings).apply {
            setRate(settings.ttsRate)
            setPitch(settings.ttsPitch)
            setVoiceName(settings.ttsVoiceName)
            previewVoice(voiceName, sample)
        }
    }

    fun engineAvailable(settings: ReaderSettings, packageName: String): Boolean {
        val selected = packageName.trim()
        return selected.isEmpty() || controller(settings).installedEngines().any { it.name == selected }
    }

    fun selectEngine(packageName: String) {
        engineStore.save(packageName.trim().take(255))
        closeCatalog()
    }

    fun applySettings(settings: ReaderSettings) {
        catalog?.apply {
            setRate(settings.ttsRate)
            setPitch(settings.ttsPitch)
            setVoiceName(settings.ttsVoiceName)
        }
    }

    private fun controller(settings: ReaderSettings): TtsController =
        catalog ?: TtsController(appContext, engineName = engineStore.load().ifBlank { null }).also { created ->
            created.setRate(settings.ttsRate)
            created.setPitch(settings.ttsPitch)
            created.setVoiceName(settings.ttsVoiceName)
            catalog = created
        }

    private fun closeCatalog() {
        catalog?.close()
        catalog = null
    }

    override fun close() = closeCatalog()
}
