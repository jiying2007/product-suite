package com.junchen.posestudio.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object PoseMath {
    fun enforceBoneLengths(candidate: Map<JointId, Vec3>): Map<JointId, Vec3> {
        val reference = Mannequin.neutral()
        val out = candidate.toMutableMap()
        for (bone in Mannequin.bones) {
            val parent = out.getValue(bone.parent)
            val child = out.getValue(bone.child)
            val expected = distance(reference.getValue(bone.parent), reference.getValue(bone.child))
            out[bone.child] = parent + (child - parent).normalized(reference.getValue(bone.child) - reference.getValue(bone.parent)) * expected
        }
        return out
    }

    fun dragJoint(joints: Map<JointId, Vec3>, joint: JointId, delta: Vec3): Map<JointId, Vec3> {
        if (!delta.isFinite()) return joints
        if (joint == JointId.PELVIS) return joints.mapValues { (_, value) -> value + delta }

        val chain = endpointChain(joint)
        if (chain != null) {
            val (root, mid, end) = chain
            val solved = solveTwoBone(
                root = joints.getValue(root),
                mid = joints.getValue(mid),
                end = joints.getValue(end),
                target = joints.getValue(end) + delta,
            )
            return joints.toMutableMap().apply {
                this[mid] = solved.first
                val endDelta = solved.second - getValue(end)
                this[end] = solved.second
                translateDescendants(this, end, endDelta)
            }
        }

        val parent = Mannequin.parent[joint] ?: return joints
        val parentPosition = joints.getValue(parent)
        val old = joints.getValue(joint)
        val length = distance(parentPosition, old)
        val target = old + delta
        val fallback = old - parentPosition
        val next = parentPosition + (target - parentPosition).normalized(fallback) * length
        val move = next - old
        return joints.toMutableMap().apply {
            this[joint] = next
            translateDescendants(this, joint, move)
        }
    }

    fun solveTwoBone(root: Vec3, mid: Vec3, end: Vec3, target: Vec3): Pair<Vec3, Vec3> {
        val l1 = distance(root, mid)
        val l2 = distance(mid, end)
        if (l1 <= Vec3.EPSILON || l2 <= Vec3.EPSILON) return mid to end

        val raw = target - root
        val rawDistance = raw.length()
        val direction = raw.normalized((end - root).normalized())
        val minReach = abs(l1 - l2) + 1e-4f
        val maxReach = max(minReach, l1 + l2 - 1e-4f)
        val reach = min(max(rawDistance, minReach), maxReach)
        val solvedEnd = root + direction * reach

        val along = (l1 * l1 + reach * reach - l2 * l2) / (2f * reach)
        val height = sqrt(max(0f, l1 * l1 - along * along))
        val currentOffset = (mid - root) - direction * (mid - root).dot(direction)
        val bend = if (currentOffset.length() > 1e-4f) {
            currentOffset.normalized()
        } else {
            val seed = if (abs(direction.y) < 0.9f) Vec3.Y else Vec3.X
            direction.cross(seed).cross(direction).normalized(Vec3.Z)
        }
        val solvedMid = root + direction * along + bend * height
        return solvedMid to solvedEnd
    }

    fun allFinite(joints: Map<JointId, Vec3>): Boolean =
        JointId.entries.all { joints[it]?.isFinite() == true }

    private fun endpointChain(joint: JointId): Triple<JointId, JointId, JointId>? = when (joint) {
        JointId.LEFT_WRIST -> Triple(JointId.LEFT_SHOULDER, JointId.LEFT_ELBOW, JointId.LEFT_WRIST)
        JointId.RIGHT_WRIST -> Triple(JointId.RIGHT_SHOULDER, JointId.RIGHT_ELBOW, JointId.RIGHT_WRIST)
        JointId.LEFT_ANKLE -> Triple(JointId.LEFT_HIP, JointId.LEFT_KNEE, JointId.LEFT_ANKLE)
        JointId.RIGHT_ANKLE -> Triple(JointId.RIGHT_HIP, JointId.RIGHT_KNEE, JointId.RIGHT_ANKLE)
        else -> null
    }

    private fun translateDescendants(map: MutableMap<JointId, Vec3>, joint: JointId, delta: Vec3) {
        for (child in Mannequin.children[joint].orEmpty()) {
            map[child] = map.getValue(child) + delta
            translateDescendants(map, child, delta)
        }
    }
}
