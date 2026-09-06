package com.junchen.jingdu

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Mirrors Android's animator-duration scale without persisting or overwriting Reader preferences. */
@Composable
internal fun rememberReaderSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    fun readScale(): Float = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    var scale by remember(context) { mutableFloatStateOf(readScale()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readScale()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return scale > 0f
}

internal fun readerEffectivePageAnimation(saved: ReaderPageAnimation, systemAnimationsEnabled: Boolean): ReaderPageAnimation =
    if (systemAnimationsEnabled) saved else ReaderPageAnimation.NONE
