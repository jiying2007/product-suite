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
        val delta = SceneProjection.screenDeltaToWorld(40f, -20f, CameraState(), 1080f)
        assertTrue(delta.isFinite())
        assertTrue(delta.length() > 0f)
    }
}
