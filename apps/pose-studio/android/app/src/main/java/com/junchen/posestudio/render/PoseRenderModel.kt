package com.junchen.posestudio.render

import com.junchen.posestudio.engine.ProjectedPoint
import com.junchen.posestudio.engine.SceneProjection
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class RenderLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val depth: Float,
    val width: Float,
    val luminance: Float,
)

data class RenderOval(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val depth: Float,
    val luminance: Float,
)

data class PoseRenderModel(
    val projected: Map<JointId, ProjectedPoint>,
    val grid: List<RenderLine>,
    val bones: List<RenderLine>,
    val volumes: List<RenderOval>,
)

object PoseRenderBuilder {
    fun build(project: PoseProject, width: Float, height: Float): PoseRenderModel {
        val projected = JointId.entries.associateWith { id ->
            SceneProjection.project(project.joints.getValue(id), project.camera, width, height)
        }
        val grid = buildList {
            for (i in -5..5) {
                val a = SceneProjection.project(Vec3(i.toFloat(), -1.84f, -4f), project.camera, width, height)
                val b = SceneProjection.project(Vec3(i.toFloat(), -1.84f, 4f), project.camera, width, height)
                add(RenderLine(a.x, a.y, b.x, b.y, (a.depth + b.depth) / 2f, 1.2f, 0.86f))
                val c = SceneProjection.project(Vec3(-4f, -1.84f, i.toFloat()), project.camera, width, height)
                val d = SceneProjection.project(Vec3(4f, -1.84f, i.toFloat()), project.camera, width, height)
                add(RenderLine(c.x, c.y, d.x, d.y, (c.depth + d.depth) / 2f, 1.2f, 0.86f))
            }
        }
        val light = lightDirection(project)
        val bones = Mannequin.bones.map { bone ->
            val a = projected.getValue(bone.parent)
            val b = projected.getValue(bone.child)
            val direction = (project.joints.getValue(bone.child) - project.joints.getValue(bone.parent)).normalized()
            val facing = max(0f, direction.dot(light))
            val luminance = (0.3f + project.light.intensity * 0.3f + facing * 0.24f).coerceIn(0.24f, 0.86f)
            val depthScale = ((a.scale + b.scale) / 2f / 160f).coerceIn(0.68f, 2.35f)
            RenderLine(a.x, a.y, b.x, b.y, (a.depth + b.depth) / 2f, bone.width * depthScale, luminance)
        }.sortedByDescending { it.depth }
        val head = projected.getValue(JointId.HEAD)
        val chest = projected.getValue(JointId.CHEST)
        val pelvis = projected.getValue(JointId.PELVIS)
        val volumes = listOf(
            RenderOval(head.x, head.y, head.scale * 0.18f, head.scale * 0.23f, head.depth, 0.62f),
            RenderOval(chest.x, chest.y + chest.scale * 0.06f, chest.scale * 0.38f, chest.scale * 0.28f, chest.depth, 0.56f),
            RenderOval(pelvis.x, pelvis.y, pelvis.scale * 0.29f, pelvis.scale * 0.19f, pelvis.depth, 0.5f),
        ).sortedByDescending { it.depth }
        return PoseRenderModel(projected, grid, bones, volumes)
    }

    private fun lightDirection(project: PoseProject): Vec3 {
        val az = project.light.azimuthDegrees * PI.toFloat() / 180f
        val el = project.light.elevationDegrees * PI.toFloat() / 180f
        return Vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az)).normalized()
    }
}
