package com.junchen.posestudio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.PoseProject

object PoseBitmapRenderer {
    fun render(
        project: PoseProject,
        width: Int = 1440,
        height: Int = 1440,
        transparentBackground: Boolean = false,
    ): Bitmap {
        require(width > 0 && height > 0) { "PNG dimensions must be positive" }
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (!transparentBackground) canvas.drawColor(Color.rgb(247, 247, 245))
        val model = PoseRenderBuilder.build(project, width.toFloat(), height.toFloat())

        if (!transparentBackground) {
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(218, 218, 214)
                strokeWidth = 2f
            }
            model.grid.forEach { line -> canvas.drawLine(line.x1, line.y1, line.x2, line.y2, gridPaint) }
        }

        model.volumes.forEach { volume ->
            val shade = shade(volume.luminance)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(shade, shade, (shade + 7).coerceAtMost(255))
            }
            canvas.drawOval(
                RectF(
                    volume.centerX - volume.radiusX,
                    volume.centerY - volume.radiusY,
                    volume.centerX + volume.radiusX,
                    volume.centerY + volume.radiusY,
                ),
                paint,
            )
        }

        model.bones.forEach { line ->
            val shade = shade(line.luminance)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(shade, shade, (shade + 8).coerceAtMost(255))
                strokeWidth = line.width
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(line.x1, line.y1, line.x2, line.y2, paint)
        }

        val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 46, 50) }
        JointId.entries.sortedByDescending { model.projected.getValue(it).depth }.forEach { id ->
            val p = model.projected.getValue(id)
            val radius = if (id == JointId.HEAD) 4f else 7f
            canvas.drawCircle(p.x, p.y, radius * (p.scale / 160f).coerceIn(0.7f, 2.2f), jointPaint)
        }
        return bitmap
    }

    private fun shade(luminance: Float): Int = (luminance.coerceIn(0.18f, 0.9f) * 255f).toInt()
}
