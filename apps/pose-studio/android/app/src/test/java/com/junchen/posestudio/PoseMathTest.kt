package com.junchen.posestudio

import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PoseMath
import com.junchen.posestudio.model.Vec3
import com.junchen.posestudio.model.distance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMathTest {
    @Test
    fun wristDragPreservesUpperAndLowerArmLengths() {
        val before = Mannequin.neutral()
        val after = PoseMath.dragJoint(before, JointId.RIGHT_WRIST, Vec3(0.2f, 0.35f, 0.3f))

        assertEquals(
            distance(before.getValue(JointId.RIGHT_SHOULDER), before.getValue(JointId.RIGHT_ELBOW)),
            distance(after.getValue(JointId.RIGHT_SHOULDER), after.getValue(JointId.RIGHT_ELBOW)),
            0.0005f,
        )
        assertEquals(
            distance(before.getValue(JointId.RIGHT_ELBOW), before.getValue(JointId.RIGHT_WRIST)),
            distance(after.getValue(JointId.RIGHT_ELBOW), after.getValue(JointId.RIGHT_WRIST)),
            0.0005f,
        )
        assertTrue(PoseMath.allFinite(after))
    }

    @Test
    fun pelvisDragMovesEveryJointBySameDelta() {
        val before = Mannequin.neutral()
        val delta = Vec3(0.2f, -0.1f, 0.35f)
        val after = PoseMath.dragJoint(before, JointId.PELVIS, delta)
        JointId.entries.forEach { id -> assertEquals(before.getValue(id) + delta, after.getValue(id)) }
    }

    @Test
    fun presetsStayFinite() {
        com.junchen.posestudio.model.PosePreset.entries.forEach { preset ->
            assertTrue(PoseMath.allFinite(Mannequin.preset(preset)))
        }
    }
}
