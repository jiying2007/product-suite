package com.junchen.jingdu

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Single-line native-canvas text for hot reader panels; avoids Compose Text/StaticLayout nodes. */
@Composable
internal fun ReaderPanelText(
    text: String,
    modifier: Modifier,
    fontSizeSp: Float = 14f,
    bold: Boolean = false,
    centered: Boolean = false,
    color: Color? = null,
) {
    val density = LocalDensity.current
    val resolvedColor = color ?: MaterialTheme.colorScheme.onSurface
    val sizePx = with(density) { fontSizeSp.sp.toPx() }
    val paint = remember(resolvedColor, sizePx, bold) {
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = resolvedColor.toArgb()
            textSize = sizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }
    Canvas(modifier.semantics { contentDescription = text }) {
        val available = (size.width - 4.dp.toPx()).coerceAtLeast(1f)
        val shown = TextUtils.ellipsize(text, paint, available, TextUtils.TruncateAt.END).toString()
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        val measured = paint.measureText(shown)
        val x = if (centered) ((size.width - measured) / 2f).coerceAtLeast(0f) else 2.dp.toPx()
        drawContext.canvas.nativeCanvas.drawText(shown, x, baseline, paint)
    }
}

/** Lightweight Canvas action used on panel hot paths instead of Material text buttons. */
@Composable
internal fun ReaderPanelAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    filled: Boolean = false,
) {
    val foreground = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val background = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent
    val outline = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val textSizePx = with(density) { 14.sp.toPx() }
    val paint = remember(foreground, textSizePx) {
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = foreground.toArgb()
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    Canvas(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text },
    ) {
        val radius = 12.dp.toPx()
        if (filled) {
            drawRoundRect(background, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        } else {
            drawRoundRect(
                outline,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }
        val available = (size.width - 24.dp.toPx()).coerceAtLeast(1f)
        val shown = TextUtils.ellipsize(text, paint, available, TextUtils.TruncateAt.END).toString()
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        val x = ((size.width - paint.measureText(shown)) / 2f).coerceAtLeast(0f)
        drawContext.canvas.nativeCanvas.drawText(shown, x, baseline, paint)
    }
}
