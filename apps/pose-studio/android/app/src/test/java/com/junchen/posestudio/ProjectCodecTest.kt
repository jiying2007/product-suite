package com.junchen.posestudio

import com.junchen.posestudio.data.ProjectCodec
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.PoseProject
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCodecTest {
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
