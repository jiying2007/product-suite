package com.junchen.posestudio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.junchen.posestudio.engine.SceneProjection
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object PoseBitmapRenderer {
    fun render(project: PoseProject, width: Int = 1440, height: Int = 1440): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 247, 245))

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 218, 214)
            strokeWidth = 2f
        }
        for (i in -5..5) {
            val a = SceneProjection.project(Vec3(i.toFloat(), -1.84f, -4f), project.camera, width.toFloat(), height.toFloat())
            val b = SceneProjection.project(Vec3(i.toFloat(), -1.84f, 4f), project.camera, width.toFloat(), height.toFloat())
            canvas.drawLine(a.x, a.y, b.x, b.y, grid)
            val c = SceneProjection.project(Vec3(-4f, -1.84f, i.toFloat()), project.camera, width.toFloat(), height.toFloat())
            val d = SceneProjection.project(Vec3(4f, -1.84f, i.toFloat()), project.camera, width.toFloat(), height.toFloat())
            canvas.drawLine(c.x, c.y, d.x, d.y, grid)
        }

        val lightDirection = lightDirection(project)
        val bones = Mannequin.bones.sortedByDescending { bone ->
            val p = SceneProjection.project(project.joints.getValue(bone.parent), project.camera, width.toFloat(), height.toFloat())
            val c = SceneProjection.project(project.joints.getValue(bone.child), project.camera, width.toFloat(), height.toFloat())
            (p.depth + c.depth) / 2f
        }
        for (bone in bones) {
            val parent = project.joints.getValue(bone.parent)
            val child = project.joints.getValue(bone.child)
            val a = SceneProjection.project(parent, project.camera, width.toFloat(), height.toFloat())
            val b = SceneProjection.project(child, project.camera, width.toFloat(), height.toFloat())
            val facing = max(0f, (child - parent).normalized().dot(lightDirection))
            val shade = (72 + 80 * project.light.intensity + 50 * facing).toInt().coerceIn(60, 220)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(shade, shade, shade + 8.coerceAtMost(255 - shade))
                strokeWidth = bone.width * ((a.scale + b.scale) / 2f / 160f).coerceIn(0.7f, 2.4f)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }

        val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 46, 50) }
        JointId.entries.sortedByDescending { id ->
            SceneProjection.project(project.joints.getValue(id), project.camera, width.toFloat(), height.toFloat()).depth
        }.forEach { id ->
            val p = SceneProjection.project(project.joints.getValue(id), project.camera, width.toFloat(), height.toFloat())
            val radius = if (id == JointId.HEAD) 34f else 10f
            canvas.drawCircle(p.x, p.y, radius * (p.scale / 160f).coerceIn(0.7f, 2.2f), jointPaint)
        }
        return bitmap
    }

    private fun lightDirection(project: PoseProject): Vec3 {
        val az = project.light.azimuthDegrees * PI.toFloat() / 180f
        val el = project.light.elevationDegrees * PI.toFloat() / 180f
        return Vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az)).normalized()
    }
}
