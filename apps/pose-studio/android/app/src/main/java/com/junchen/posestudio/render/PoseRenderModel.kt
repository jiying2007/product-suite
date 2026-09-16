package com.junchen.posestudio.render

import com.junchen.posestudio.engine.ProjectedPoint
import com.junchen.posestudio.engine.SceneProjection
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import kotlin.math.PI
import kotlin.math.atan2
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

data class RenderCapsule(
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
    val rotationDegrees: Float = 0f,
)

data class RenderEndpoint(
    val joint: JointId,
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val depth: Float,
    val luminance: Float,
    val rotationDegrees: Float,
)

data class PoseRenderModel(
    val projected: Map<JointId, ProjectedPoint>,
    val grid: List<RenderLine>,
    val bodySegments: List<RenderCapsule>,
    val bones: List<RenderLine>,
    val volumes: List<RenderOval>,
    val endpoints: List<RenderEndpoint>,
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
        val bodySegments = Mannequin.bones.map { bone ->
            val a = projected.getValue(bone.parent)
            val b = projected.getValue(bone.child)
            val direction = (project.joints.getValue(bone.child) - project.joints.getValue(bone.parent)).normalized()
            val facing = max(0f, direction.dot(light))
            val luminance = (0.28f + project.light.intensity * 0.28f + facing * 0.22f).coerceIn(0.22f, 0.82f)
            val depthScale = ((a.scale + b.scale) / 2f / 160f).coerceIn(0.68f, 2.35f)
            val widthMultiplier = bodyWidthMultiplier(bone.child)
            RenderCapsule(
                x1 = a.x,
                y1 = a.y,
                x2 = b.x,
                y2 = b.y,
                depth = (a.depth + b.depth) / 2f,
                width = bone.width * depthScale * widthMultiplier,
                luminance = luminance,
            )
        }.sortedByDescending { it.depth }
        val bones = Mannequin.bones.map { bone ->
            val a = projected.getValue(bone.parent)
            val b = projected.getValue(bone.child)
            val direction = (project.joints.getValue(bone.child) - project.joints.getValue(bone.parent)).normalized()
            val facing = max(0f, direction.dot(light))
            val luminance = (0.3f + project.light.intensity * 0.3f + facing * 0.24f).coerceIn(0.24f, 0.86f)
            val depthScale = ((a.scale + b.scale) / 2f / 160f).coerceIn(0.68f, 2.35f)
            RenderLine(
                a.x,
                a.y,
                b.x,
                b.y,
                (a.depth + b.depth) / 2f,
                (bone.width * depthScale * 0.18f).coerceAtLeast(1.5f),
                luminance,
            )
        }.sortedByDescending { it.depth }
        val head = projected.getValue(JointId.HEAD)
        val chest = projected.getValue(JointId.CHEST)
        val pelvis = projected.getValue(JointId.PELVIS)
        val chestRotation = screenAngle(
            projected.getValue(JointId.LEFT_SHOULDER),
            projected.getValue(JointId.RIGHT_SHOULDER),
        )
        val pelvisRotation = screenAngle(
            projected.getValue(JointId.LEFT_HIP),
            projected.getValue(JointId.RIGHT_HIP),
        )
        val volumes = listOf(
            RenderOval(
                head.x,
                head.y,
                head.scale * 0.19f,
                head.scale * 0.24f,
                head.depth,
                0.62f,
            ),
            RenderOval(
                chest.x,
                chest.y + chest.scale * 0.06f,
                chest.scale * 0.40f,
                chest.scale * 0.30f,
                chest.depth,
                0.56f,
                chestRotation,
            ),
            RenderOval(
                pelvis.x,
                pelvis.y,
                pelvis.scale * 0.31f,
                pelvis.scale * 0.21f,
                pelvis.depth,
                0.5f,
                pelvisRotation,
            ),
        ).sortedByDescending { it.depth }
        val endpoints = listOf(
            endpoint(project, projected, JointId.LEFT_ELBOW, JointId.LEFT_WRIST, hand = true),
            endpoint(project, projected, JointId.RIGHT_ELBOW, JointId.RIGHT_WRIST, hand = true),
            endpoint(project, projected, JointId.LEFT_ANKLE, JointId.LEFT_FOOT, hand = false),
            endpoint(project, projected, JointId.RIGHT_ANKLE, JointId.RIGHT_FOOT, hand = false),
        ).sortedByDescending { it.depth }
        return PoseRenderModel(projected, grid, bodySegments, bones, volumes, endpoints)
    }

    private fun endpoint(
        project: PoseProject,
        projected: Map<JointId, ProjectedPoint>,
        parent: JointId,
        joint: JointId,
        hand: Boolean,
    ): RenderEndpoint {
        val parentPoint = projected.getValue(parent)
        val point = projected.getValue(joint)
        val baseAngle = screenAngle(parentPoint, point)
        val roll = project.jointRollDegrees[joint] ?: 0f
        val radiusX = point.scale * if (hand) 0.14f else 0.17f
        val radiusY = point.scale * if (hand) 0.07f else 0.075f
        return RenderEndpoint(
            joint = joint,
            centerX = point.x,
            centerY = point.y,
            radiusX = radiusX,
            radiusY = radiusY,
            depth = point.depth,
            luminance = if (hand) 0.58f else 0.52f,
            rotationDegrees = normalizeDegrees(baseAngle + roll),
        )
    }

    private fun bodyWidthMultiplier(child: JointId): Float = when (child) {
        JointId.SPINE, JointId.CHEST, JointId.NECK, JointId.HEAD -> 1.7f
        JointId.LEFT_SHOULDER, JointId.RIGHT_SHOULDER -> 1.55f
        JointId.LEFT_ELBOW, JointId.RIGHT_ELBOW -> 2.15f
        JointId.LEFT_WRIST, JointId.RIGHT_WRIST -> 2.0f
        JointId.LEFT_HIP, JointId.RIGHT_HIP -> 1.7f
        JointId.LEFT_KNEE, JointId.RIGHT_KNEE -> 2.45f
        JointId.LEFT_ANKLE, JointId.RIGHT_ANKLE -> 2.25f
        JointId.LEFT_FOOT, JointId.RIGHT_FOOT -> 2.1f
        JointId.PELVIS -> 1.7f
    }

    private fun screenAngle(a: ProjectedPoint, b: ProjectedPoint): Float =
        atan2(b.y - a.y, b.x - a.x) * 180f / PI.toFloat()

    private fun normalizeDegrees(value: Float): Float {
        var normalized = value % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }

    private fun lightDirection(project: PoseProject): Vec3 {
        val az = project.light.azimuthDegrees * PI.toFloat() / 180f
        val el = project.light.elevationDegrees * PI.toFloat() / 180f
        return Vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az)).normalized()
    }
}
