package com.junchen.posestudio.ui

import com.junchen.posestudio.engine.ProjectedPoint
import com.junchen.posestudio.model.JointId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JointPickerTest {
    @Test
    fun nearestJointWinsWhenScreenPositionsAreClearlySeparated() {
        val picked = JointPicker.pick(
            projected = mapOf(
                JointId.LEFT_WRIST to point(100f, 100f, depth = 8f),
                JointId.RIGHT_WRIST to point(130f, 100f, depth = 4f),
            ),
            touchX = 101f,
            touchY = 100f,
            hitRadiusPx = 48f,
            overlapSlopPx = 12f,
        )
        assertEquals(JointId.LEFT_WRIST, picked)
    }

    @Test
    fun frontJointWinsWhenProjectedJointsNearlyOverlap() {
        val picked = JointPicker.pick(
            projected = mapOf(
                JointId.LEFT_WRIST to point(100f, 100f, depth = 8f),
                JointId.RIGHT_WRIST to point(108f, 100f, depth = 4f),
            ),
            touchX = 101f,
            touchY = 100f,
            hitRadiusPx = 48f,
            overlapSlopPx = 12f,
        )
        assertEquals(JointId.RIGHT_WRIST, picked)
    }

    @Test
    fun nearestJointBreaksTieWhenDepthMatches() {
        val picked = JointPicker.pick(
            projected = mapOf(
                JointId.LEFT_ANKLE to point(100f, 100f, depth = 6f),
                JointId.RIGHT_ANKLE to point(108f, 100f, depth = 6f),
            ),
            touchX = 101f,
            touchY = 100f,
            hitRadiusPx = 48f,
            overlapSlopPx = 12f,
        )
        assertEquals(JointId.LEFT_ANKLE, picked)
    }

    @Test
    fun touchOutsideHitRadiusSelectsNothing() {
        val picked = JointPicker.pick(
            projected = mapOf(JointId.HEAD to point(100f, 100f, depth = 5f)),
            touchX = 200f,
            touchY = 200f,
            hitRadiusPx = 48f,
            overlapSlopPx = 12f,
        )
        assertNull(picked)
    }

    private fun point(x: Float, y: Float, depth: Float) = ProjectedPoint(
        x = x,
        y = y,
        depth = depth,
        scale = 160f,
    )
}
