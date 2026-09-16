package com.junchen.posestudio

import com.junchen.posestudio.engine.PoseMath
import com.junchen.posestudio.model.BodyProportionPreset
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.Vec3
import com.junchen.posestudio.model.distance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProportionTest {
    @Test
    fun longLegsLengthenLegsAndShortenTorso() {
        val neutral = Mannequin.neutral()
        val styled = PoseMath.applyBodyProportions(
            Mannequin.preset(PosePreset.RUN),
            BodyProportionPreset.LONG_LEGS,
        )

        assertEquals(
            boneLength(neutral, JointId.LEFT_HIP, JointId.LEFT_KNEE) * 1.14f,
            boneLength(styled, JointId.LEFT_HIP, JointId.LEFT_KNEE),
            0.0005f,
        )
        assertEquals(
            boneLength(neutral, JointId.PELVIS, JointId.SPINE) * 0.95f,
            boneLength(styled, JointId.PELVIS, JointId.SPINE),
            0.0005f,
        )
        assertTrue(PoseMath.allFinite(styled))
    }

    @Test
    fun longTorsoLengthensTorsoAndShortensLegs() {
        val neutral = Mannequin.neutral()
        val styled = PoseMath.applyBodyProportions(
            Mannequin.preset(PosePreset.CONTRAPPOSTO),
            BodyProportionPreset.LONG_TORSO,
        )

        assertEquals(
            boneLength(neutral, JointId.SPINE, JointId.CHEST) * 1.14f,
            boneLength(styled, JointId.SPINE, JointId.CHEST),
            0.0005f,
        )
        assertEquals(
            boneLength(neutral, JointId.RIGHT_KNEE, JointId.RIGHT_ANKLE) * 0.95f,
            boneLength(styled, JointId.RIGHT_KNEE, JointId.RIGHT_ANKLE),
            0.0005f,
        )
        assertTrue(PoseMath.allFinite(styled))
    }

    @Test
    fun proportionPresetsAreAbsoluteInsteadOfCompounding() {
        val once = PoseMath.applyBodyProportions(
            Mannequin.preset(PosePreset.REACH),
            BodyProportionPreset.LONG_LEGS,
        )
        val twice = PoseMath.applyBodyProportions(once, BodyProportionPreset.LONG_LEGS)

        Mannequin.bones.forEach { bone ->
            assertEquals(
                boneLength(once, bone.parent, bone.child),
                boneLength(twice, bone.parent, bone.child),
                0.0005f,
            )
        }
    }

    @Test
    fun balancedRestoresNeutralSegmentLengthsWithoutResettingPoseDirection() {
        val run = Mannequin.preset(PosePreset.RUN)
        val longLegs = PoseMath.applyBodyProportions(run, BodyProportionPreset.LONG_LEGS)
        val balanced = PoseMath.applyBodyProportions(longLegs, BodyProportionPreset.BALANCED)
        val neutral = Mannequin.neutral()

        Mannequin.bones.forEach { bone ->
            assertEquals(
                boneLength(neutral, bone.parent, bone.child),
                boneLength(balanced, bone.parent, bone.child),
                0.0005f,
            )
        }
        val runDirection = segmentDirection(run, JointId.RIGHT_HIP, JointId.RIGHT_KNEE)
        val balancedDirection = segmentDirection(balanced, JointId.RIGHT_HIP, JointId.RIGHT_KNEE)
        assertTrue(runDirection.dot(balancedDirection) > 0.999f)
    }

    @Test
    fun fastPoseRetargetCanKeepCurrentStylizedLengths() {
        val longLegs = PoseMath.applyBodyProportions(
            Mannequin.preset(PosePreset.REACH),
            BodyProportionPreset.LONG_LEGS,
        )
        val runWithSameLengths = PoseMath.retargetBoneLengths(
            Mannequin.preset(PosePreset.RUN),
            longLegs,
        )

        Mannequin.bones.forEach { bone ->
            assertEquals(
                boneLength(longLegs, bone.parent, bone.child),
                boneLength(runWithSameLengths, bone.parent, bone.child),
                0.0005f,
            )
        }
        assertTrue(PoseMath.allFinite(runWithSameLengths))
    }

    private fun boneLength(joints: Map<JointId, Vec3>, parent: JointId, child: JointId): Float =
        distance(joints.getValue(parent), joints.getValue(child))

    private fun segmentDirection(joints: Map<JointId, Vec3>, parent: JointId, child: JointId): Vec3 =
        (joints.getValue(child) - joints.getValue(parent)).normalized()
}
