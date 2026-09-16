package com.junchen.posestudio.ui

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REFERENCE_MIN_SCALE = 0.5f
private const val REFERENCE_MAX_SCALE = 2.5f
private const val REFERENCE_MAX_OFFSET_DP = 220f

internal object ReferenceOverlaySession {
    var uri by mutableStateOf<String?>(null)
    private var visibleState by mutableStateOf(true)
    var visible: Boolean
        get() = visibleState
        set(value) {
            visibleState = value
            if (!value) alignmentMode = false
        }
    var opacity by mutableFloatStateOf(0.45f)
    var scale by mutableFloatStateOf(1f)
    var offsetXDp by mutableFloatStateOf(0f)
    var offsetYDp by mutableFloatStateOf(0f)
    var alignmentMode by mutableStateOf(false)
        private set

    fun use(uri: Uri) {
        this.uri = uri.toString()
        visible = true
        resetTransform()
        alignmentMode = true
    }

    fun toggleAlignment() {
        if (uri != null && visible) alignmentMode = !alignmentMode
    }

    fun endAlignment() {
        alignmentMode = false
    }

    fun panByDp(deltaXDp: Float, deltaYDp: Float) {
        offsetXDp = referenceOffsetAfterPan(offsetXDp, deltaXDp)
        offsetYDp = referenceOffsetAfterPan(offsetYDp, deltaYDp)
    }

    fun zoomBy(factor: Float) {
        scale = referenceScaleAfterZoom(scale, factor)
    }

    fun resetTransform() {
        opacity = 0.45f
        scale = 1f
        offsetXDp = 0f
        offsetYDp = 0f
    }

    fun clear() {
        uri = null
        alignmentMode = false
        visible = true
        resetTransform()
    }
}

@Composable
internal fun ReferenceImageOverlay(modifier: Modifier = Modifier) {
    val uriText = ReferenceOverlaySession.uri ?: return
    if (!ReferenceOverlaySession.visible) return

    val resolver = LocalContext.current.contentResolver
    val density = LocalDensity.current
    val image by produceState<ImageBitmap?>(initialValue = null, uriText) {
        value = withContext(Dispatchers.IO) {
            decodeReferenceBitmap(resolver, Uri.parse(uriText))?.asImageBitmap()
        }
    }
    val bitmap = image ?: return
    val translationX = with(density) { ReferenceOverlaySession.offsetXDp.dp.toPx() }
    val translationY = with(density) { ReferenceOverlaySession.offsetYDp.dp.toPx() }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer {
            alpha = ReferenceOverlaySession.opacity.coerceIn(0.1f, 0.95f)
            scaleX = ReferenceOverlaySession.scale.coerceIn(REFERENCE_MIN_SCALE, REFERENCE_MAX_SCALE)
            scaleY = ReferenceOverlaySession.scale.coerceIn(REFERENCE_MIN_SCALE, REFERENCE_MAX_SCALE)
            this.translationX = translationX
            this.translationY = translationY
        },
    )
}

private fun decodeReferenceBitmap(
    resolver: ContentResolver,
    uri: Uri,
    maxDimension: Int = 2048,
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = referenceSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

internal fun referenceSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sample = 1
    while (width / sample > maxDimension || height / sample > maxDimension) {
        sample *= 2
    }
    return sample
}

internal fun referenceScaleAfterZoom(currentScale: Float, factor: Float): Float {
    val base = currentScale.takeIf(Float::isFinite) ?: 1f
    if (!factor.isFinite() || factor <= 0f) return base.coerceIn(REFERENCE_MIN_SCALE, REFERENCE_MAX_SCALE)
    return (base * factor).coerceIn(REFERENCE_MIN_SCALE, REFERENCE_MAX_SCALE)
}

internal fun referenceOffsetAfterPan(currentOffsetDp: Float, deltaDp: Float): Float {
    val base = currentOffsetDp.takeIf(Float::isFinite) ?: 0f
    if (!deltaDp.isFinite()) return base.coerceIn(-REFERENCE_MAX_OFFSET_DP, REFERENCE_MAX_OFFSET_DP)
    return (base + deltaDp).coerceIn(-REFERENCE_MAX_OFFSET_DP, REFERENCE_MAX_OFFSET_DP)
}
