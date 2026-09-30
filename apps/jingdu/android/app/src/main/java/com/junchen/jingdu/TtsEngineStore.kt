package com.junchen.jingdu

import android.content.Context

/**
 * Device-local TTS engine preference. Engine packages are capabilities of this installation,
 * so this value is intentionally not treated as portable Reader backup identity.
 */
internal class TtsEngineStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): String = prefs.getString(KEY_ENGINE, "").orEmpty().take(MAX_PACKAGE_CHARS)

    fun save(packageName: String) {
        prefs.edit().putString(KEY_ENGINE, packageName.trim().take(MAX_PACKAGE_CHARS)).apply()
    }

    private companion object {
        const val PREFS = "jingdu.tts-engine.v1"
        const val KEY_ENGINE = "engine.package"
        const val MAX_PACKAGE_CHARS = 255
    }
}
