package com.junchen.posestudio.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFramingTest {
    @Test
    fun viewPresetRestoresDefaultFramingAndSetsRequestedAngle() {
        val camera = CameraState(
            yawDegrees = -63f,
            pitchDegrees = 21f,
            distance = 11.4f,
            fovDegrees = 67f,
            target = Vec3(1.3f, -0.8f, 2.1f),
        )

        val result = camera.withDefaultFraming(yawDegrees = 45f, pitchDegrees = -4f)
        val defaults = CameraState()

        assertEquals(45f, result.yawDegrees, 0f)
        assertEquals(-4f, result.pitchDegrees, 0f)
        assertEquals(defaults.distance, result.distance, 0f)
        assertEquals(defaults.fovDegrees, result.fovDegrees, 0f)
        assertEquals(defaults.target, result.target)
    }
}
