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
    fun mirrorPoseIsAnInvolutionAndPreservesLengths() {
        val before = Mannequin.preset(com.junchen.posestudio.model.PosePreset.REACH)
        val mirrored = PoseMath.mirrorPose(before)
        val twice = PoseMath.mirrorPose(mirrored)

        JointId.entries.forEach { id ->
            assertEquals(before.getValue(id).x, twice.getValue(id).x, 0.0001f)
            assertEquals(before.getValue(id).y, twice.getValue(id).y, 0.0001f)
            assertEquals(before.getValue(id).z, twice.getValue(id).z, 0.0001f)
        }
        Mannequin.bones.forEach { bone ->
            assertEquals(
                distance(before.getValue(bone.parent), before.getValue(bone.child)),
                distance(mirrored.getValue(bone.parent), mirrored.getValue(bone.child)),
                0.0005f,
            )
        }
    }

    @Test
    fun copiedArmMirrorsSourceSegmentDirections() {
        val source = PoseMath.dragJoint(Mannequin.neutral(), JointId.LEFT_WRIST, Vec3(-0.15f, 0.42f, 0.35f))
        val copied = PoseMath.copyArm(source, fromLeft = true)

        val leftUpper = source.getValue(JointId.LEFT_ELBOW) - source.getValue(JointId.LEFT_SHOULDER)
        val rightUpper = copied.getValue(JointId.RIGHT_ELBOW) - copied.getValue(JointId.RIGHT_SHOULDER)
        val leftLower = source.getValue(JointId.LEFT_WRIST) - source.getValue(JointId.LEFT_ELBOW)
        val rightLower = copied.getValue(JointId.RIGHT_WRIST) - copied.getValue(JointId.RIGHT_ELBOW)

        assertEquals(-leftUpper.x, rightUpper.x, 0.0005f)
        assertEquals(leftUpper.y, rightUpper.y, 0.0005f)
        assertEquals(leftUpper.z, rightUpper.z, 0.0005f)
        assertEquals(-leftLower.x, rightLower.x, 0.0005f)
        assertEquals(leftLower.y, rightLower.y, 0.0005f)
        assertEquals(leftLower.z, rightLower.z, 0.0005f)
    }

    @Test
    fun groundFeetMovesWholePoseWithoutChangingBoneLengths() {
        val before = PoseMath.dragJoint(Mannequin.preset(com.junchen.posestudio.model.PosePreset.RUN), JointId.PELVIS, Vec3(0f, 0.6f, 0f))
        val after = PoseMath.groundFeet(before)
        val lowest = minOf(after.getValue(JointId.LEFT_FOOT).y, after.getValue(JointId.RIGHT_FOOT).y)

        assertEquals(-1.82f, lowest, 0.0001f)
        Mannequin.bones.forEach { bone ->
            assertEquals(
                distance(before.getValue(bone.parent), before.getValue(bone.child)),
                distance(after.getValue(bone.parent), after.getValue(bone.child)),
                0.0005f,
            )
        }
    }

    @Test
    fun presetsStayFinite() {
        com.junchen.posestudio.model.PosePreset.entries.forEach { preset ->
            assertTrue(PoseMath.allFinite(Mannequin.preset(preset)))
        }
    }
}
