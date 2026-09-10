package com.junchen.posestudio

import com.junchen.posestudio.engine.SceneProjection
import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionTest {
    @Test
    fun originProjectsToHorizontalCenter() {
        val point = SceneProjection.project(Vec3.ZERO, CameraState(yawDegrees = 0f, pitchDegrees = 0f), 1000f, 800f)
        assertEquals(500f, point.x, 0.01f)
        assertEquals(384f, point.y, 0.01f)
        assertTrue(point.depth > 0f)
        assertTrue(point.scale.isFinite())
    }

    @Test
    fun screenDeltaProducesFiniteWorldDelta() {
        val delta = SceneProjection.screenDeltaToWorld(40f, -20f, CameraState(), 1080f, depth = 6.5f)
        assertTrue(delta.isFinite())
        assertTrue(delta.length() > 0f)
    }

    @Test
    fun dragScaleUsesSelectedDepth() {
        val camera = CameraState(yawDegrees = 35f, pitchDegrees = 30f, fovDegrees = 45f)
        val near = SceneProjection.screenDeltaToWorld(60f, 0f, camera, 1080f, depth = 4f).length()
        val far = SceneProjection.screenDeltaToWorld(60f, 0f, camera, 1080f, depth = 8f).length()
        assertTrue(far > near * 1.9f)
    }

    @Test
    fun pitchedCameraVerticalDragHasDepthComponent() {
        val delta = SceneProjection.screenDeltaToWorld(0f, -50f, CameraState(pitchDegrees = 35f), 1080f, 7f)
        assertTrue(kotlin.math.abs(delta.z) > 0.001f)
    }

    @Test
    fun cameraTargetOffsetsCompositionWithoutMovingPose() {
        val base = CameraState(yawDegrees = 0f, pitchDegrees = 0f)
        val centered = SceneProjection.project(Vec3.ZERO, base, 1000f, 800f)
        val shifted = SceneProjection.project(Vec3.ZERO, base.copy(target = Vec3(-0.5f, 0f, 0f)), 1000f, 800f)
        assertTrue(shifted.x > centered.x)
    }

    @Test
    fun panTargetDeltaMovesSceneWithFinger() {
        val camera = CameraState(yawDegrees = 0f, pitchDegrees = 0f)
        val centered = SceneProjection.project(Vec3.ZERO, camera, 1000f, 800f)
        val targetDelta = SceneProjection.panTargetDelta(80f, 0f, camera, 1000f)
        val moved = SceneProjection.project(Vec3.ZERO, camera.copy(target = targetDelta), 1000f, 800f)
        assertTrue(moved.x > centered.x)
    }
}
