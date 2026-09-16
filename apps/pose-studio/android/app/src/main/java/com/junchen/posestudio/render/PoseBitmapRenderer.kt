package com.junchen.posestudio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.PoseProject

enum class PoseBitmapMode { SHADED, SILHOUETTE, CONSTRUCTION }

object PoseBitmapRenderer {
    fun render(
        project: PoseProject,
        width: Int = 1440,
        height: Int = 1440,
        transparentBackground: Boolean = false,
        mode: PoseBitmapMode = PoseBitmapMode.SHADED,
    ): Bitmap {
        require(width > 0 && height > 0) { "PNG dimensions must be positive" }
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (!transparentBackground) canvas.drawColor(Color.rgb(247, 247, 245))
        val model = PoseRenderBuilder.build(project, width.toFloat(), height.toFloat())

        if (!transparentBackground && mode == PoseBitmapMode.SHADED) {
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(218, 218, 214)
                strokeWidth = 2f
            }
            model.grid.forEach { line -> canvas.drawLine(line.x1, line.y1, line.x2, line.y2, gridPaint) }
        }

        model.bodySegments.forEach { segment ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (mode) {
                    PoseBitmapMode.SHADED -> shadedColor(segment.luminance)
                    PoseBitmapMode.SILHOUETTE -> Color.BLACK
                    PoseBitmapMode.CONSTRUCTION -> Color.argb(54, 28, 28, 32)
                }
                strokeWidth = segment.width
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(segment.x1, segment.y1, segment.x2, segment.y2, paint)
        }

        model.volumes.forEach { volume ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (mode) {
                    PoseBitmapMode.SHADED -> shadedColor(volume.luminance)
                    PoseBitmapMode.SILHOUETTE -> Color.BLACK
                    PoseBitmapMode.CONSTRUCTION -> Color.argb(44, 28, 28, 32)
                }
            }
            canvas.save()
            canvas.rotate(volume.rotationDegrees, volume.centerX, volume.centerY)
            canvas.drawOval(
                RectF(
                    volume.centerX - volume.radiusX,
                    volume.centerY - volume.radiusY,
                    volume.centerX + volume.radiusX,
                    volume.centerY + volume.radiusY,
                ),
                paint,
            )
            canvas.restore()
        }

        model.endpoints.forEach { endpoint ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (mode) {
                    PoseBitmapMode.SHADED -> shadedColor(endpoint.luminance)
                    PoseBitmapMode.SILHOUETTE -> Color.BLACK
                    PoseBitmapMode.CONSTRUCTION -> Color.argb(60, 28, 28, 32)
                }
            }
            canvas.save()
            canvas.rotate(endpoint.rotationDegrees, endpoint.centerX, endpoint.centerY)
            canvas.drawOval(
                RectF(
                    endpoint.centerX - endpoint.radiusX,
                    endpoint.centerY - endpoint.radiusY,
                    endpoint.centerX + endpoint.radiusX,
                    endpoint.centerY + endpoint.radiusY,
                ),
                paint,
            )
            canvas.restore()
        }

        if (mode != PoseBitmapMode.SILHOUETTE) {
            model.bones.forEach { line ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (mode) {
                        PoseBitmapMode.SHADED -> shadedColor(line.luminance)
                        PoseBitmapMode.CONSTRUCTION -> Color.argb(224, 34, 34, 40)
                        PoseBitmapMode.SILHOUETTE -> Color.BLACK
                    }
                    strokeWidth = if (mode == PoseBitmapMode.CONSTRUCTION) line.width.coerceAtLeast(2.5f) else line.width
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(line.x1, line.y1, line.x2, line.y2, paint)
            }

            val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (mode == PoseBitmapMode.CONSTRUCTION) Color.rgb(24, 24, 30) else Color.rgb(46, 46, 50)
            }
            JointId.entries.sortedByDescending { model.projected.getValue(it).depth }.forEach { id ->
                val p = model.projected.getValue(id)
                val radius = if (id == JointId.HEAD) 4f else 7f
                canvas.drawCircle(p.x, p.y, radius * (p.scale / 160f).coerceIn(0.7f, 2.2f), jointPaint)
            }
        }
        return bitmap
    }

    private fun shadedColor(luminance: Float): Int {
        val shade = (luminance.coerceIn(0.18f, 0.9f) * 255f).toInt()
        return Color.rgb(shade, shade, (shade + 7).coerceAtMost(255))
    }
}
