package com.junchen.posestudio

import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.render.PoseRenderBuilder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderModelTest {
    @Test
    fun volumetricSegmentsCoverEveryBoneAndRemainWiderThanConstructionLines() {
        val model = PoseRenderBuilder.build(PoseProject(), width = 1080f, height = 1920f)

        assertEquals(Mannequin.bones.size, model.bodySegments.size)
        assertEquals(Mannequin.bones.size, model.bones.size)
        model.bodySegments.zip(model.bones).forEach { (volume, construction) ->
            assertTrue(volume.width > construction.width)
            assertTrue(volume.width.isFinite() && volume.width > 0f)
            assertTrue(volume.luminance in 0f..1f)
        }
    }

    @Test
    fun torsoVolumesCarryFiniteScreenOrientationAcrossPoses() {
        PosePreset.entries.forEach { preset ->
            val project = PoseProject(joints = Mannequin.preset(preset))
            val model = PoseRenderBuilder.build(project, width = 1440f, height = 1440f)

            assertEquals(3, model.volumes.size)
            model.volumes.forEach { volume ->
                assertTrue(volume.rotationDegrees.isFinite())
                assertTrue(volume.radiusX > 0f)
                assertTrue(volume.radiusY > 0f)
            }
        }
    }

    @Test
    fun endpointRollChangesVisibleHandOrientationWithoutChangingSchema() {
        val base = PoseProject()
        val rotated = base.copy(
            jointRollDegrees = base.jointRollDegrees.toMutableMap().apply {
                this[JointId.RIGHT_WRIST] = 45f
            },
        )
        val baseModel = PoseRenderBuilder.build(base, width = 1080f, height = 1920f)
        val rotatedModel = PoseRenderBuilder.build(rotated, width = 1080f, height = 1920f)

        assertEquals(4, baseModel.endpoints.size)
        val before = baseModel.endpoints.single { it.joint == JointId.RIGHT_WRIST }
        val after = rotatedModel.endpoints.single { it.joint == JointId.RIGHT_WRIST }
        assertTrue(before.radiusX > before.radiusY)
        assertTrue(abs(normalizedDelta(before.rotationDegrees, after.rotationDegrees) - 45f) < 0.01f)
        assertEquals(PoseProject.CURRENT_SCHEMA_VERSION, rotated.schemaVersion)
    }

    private fun normalizedDelta(before: Float, after: Float): Float {
        var delta = (after - before) % 360f
        if (delta < 0f) delta += 360f
        return delta
    }
}
