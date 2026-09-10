package com.junchen.posestudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.junchen.posestudio.data.ProjectCodec
import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectCodecInstrumentedTest {
    @Test
    fun schemaOneProjectMigratesToCurrentSchema() {
        val v1 = JSONObject()
            .put("schemaVersion", 1)
            .put("id", "legacy")
            .put("name", "Legacy")
            .put("joints", JSONObject())
            .toString()

        val decoded = ProjectCodec.decode(v1)
        assertEquals(PoseProject.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(0f, decoded.jointRollDegrees.getValue(JointId.RIGHT_WRIST), 0f)
        assertEquals(Vec3.ZERO, decoded.camera.target)
    }

    @Test
    fun roundTripPreservesJointRollSemanticState() {
        val project = PoseProject(
            jointRollDegrees = JointId.entries.associateWith { if (it == JointId.RIGHT_WRIST) 42f else 0f },
        )
        val decoded = ProjectCodec.decode(ProjectCodec.encode(project))
        assertEquals(42f, decoded.jointRollDegrees.getValue(JointId.RIGHT_WRIST), 0.001f)
    }

    @Test
    fun roundTripPreservesCameraPanTarget() {
        val project = PoseProject(camera = CameraState(target = Vec3(0.35f, -0.22f, 0.18f)))
        val decoded = ProjectCodec.decode(ProjectCodec.encode(project))
        assertEquals(project.camera.target.x, decoded.camera.target.x, 0.001f)
        assertEquals(project.camera.target.y, decoded.camera.target.y, 0.001f)
        assertEquals(project.camera.target.z, decoded.camera.target.z, 0.001f)
    }

    @Test
    fun invalidCoordinateFallsBackInsteadOfPoisoningProject() {
        val json = JSONObject(ProjectCodec.encode(PoseProject()))
        json.getJSONObject("joints").put(JointId.RIGHT_WRIST.name, JSONArray(listOf(1e300, 0.0, 0.0)))
        val decoded = ProjectCodec.decode(json.toString())
        assertTrue(decoded.joints.getValue(JointId.RIGHT_WRIST).isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedProjectIsRejected() {
        ProjectCodec.decode(" ".repeat(2_000_001))
    }
}
