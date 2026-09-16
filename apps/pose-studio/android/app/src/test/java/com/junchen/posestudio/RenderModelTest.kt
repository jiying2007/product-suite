package com.junchen.posestudio

import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.render.PoseRenderBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
