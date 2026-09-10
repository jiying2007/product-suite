package com.junchen.posestudio.ui

import com.junchen.posestudio.engine.ProjectedPoint
import com.junchen.posestudio.model.JointId
import kotlin.math.hypot

internal object JointPicker {
    fun pick(
        projected: Map<JointId, ProjectedPoint>,
        touchX: Float,
        touchY: Float,
        hitRadiusPx: Float,
        overlapSlopPx: Float,
    ): JointId? {
        if (!touchX.isFinite() || !touchY.isFinite() || hitRadiusPx <= 0f || overlapSlopPx < 0f) return null

        val candidates = projected.mapNotNull { (joint, point) ->
            val distance = hypot(point.x - touchX, point.y - touchY)
            if (distance <= hitRadiusPx) Candidate(joint, point.depth, distance) else null
        }
        if (candidates.isEmpty()) return null

        val nearestDistance = candidates.minOf { it.distance }
        return candidates
            .asSequence()
            .filter { it.distance <= nearestDistance + overlapSlopPx }
            .minWithOrNull(compareBy<Candidate> { it.depth }.thenBy { it.distance })
            ?.joint
    }

    private data class Candidate(
        val joint: JointId,
        val depth: Float,
        val distance: Float,
    )
}
