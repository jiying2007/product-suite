package com.junchen.posestudio.engine

import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class ProjectedPoint(val x: Float, val y: Float, val depth: Float, val scale: Float)

object SceneProjection {
    fun project(point: Vec3, camera: CameraState, width: Float, height: Float): ProjectedPoint {
        val yaw = radians(camera.yawDegrees)
        val pitch = radians(camera.pitchDegrees)

        val x1 = cos(yaw) * point.x - sin(yaw) * point.z
        val z1 = sin(yaw) * point.x + cos(yaw) * point.z
        val y2 = cos(pitch) * point.y - sin(pitch) * z1
        val z2 = sin(pitch) * point.y + cos(pitch) * z1

        val depth = (camera.distance + z2).coerceAtLeast(0.25f)
        val focal = focalLength(width, camera)
        val scale = focal / depth
        return ProjectedPoint(
            x = width / 2f + x1 * scale,
            y = height * 0.48f - y2 * scale,
            depth = depth,
            scale = scale,
        )
    }

    fun screenDeltaToWorld(
        dx: Float,
        dy: Float,
        camera: CameraState,
        width: Float,
        depth: Float = camera.distance,
    ): Vec3 {
        val yaw = radians(camera.yawDegrees)
        val pitch = radians(camera.pitchDegrees)
        val right = Vec3(cos(yaw), 0f, -sin(yaw))
        val up = Vec3(-sin(pitch) * sin(yaw), cos(pitch), -sin(pitch) * cos(yaw))
        val unitsPerPixel = depth.coerceAtLeast(0.25f) / focalLength(width, camera)
        return right * (dx * unitsPerPixel) + up * (-dy * unitsPerPixel)
    }

    private fun focalLength(width: Float, camera: CameraState): Float =
        (width.coerceAtLeast(1f) / 2f) / tan(radians(camera.fovDegrees.coerceIn(20f, 75f)) / 2f)

    private fun radians(degrees: Float): Float = degrees * PI.toFloat() / 180f
}
