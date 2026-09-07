package com.junchen.posestudio.model

import java.util.UUID

enum class JointId(val label: String) {
    PELVIS("Pelvis"),
    SPINE("Spine"),
    CHEST("Chest"),
    NECK("Neck"),
    HEAD("Head"),
    LEFT_SHOULDER("L Shoulder"),
    LEFT_ELBOW("L Elbow"),
    LEFT_WRIST("L Wrist"),
    RIGHT_SHOULDER("R Shoulder"),
    RIGHT_ELBOW("R Elbow"),
    RIGHT_WRIST("R Wrist"),
    LEFT_HIP("L Hip"),
    LEFT_KNEE("L Knee"),
    LEFT_ANKLE("L Ankle"),
    LEFT_FOOT("L Foot"),
    RIGHT_HIP("R Hip"),
    RIGHT_KNEE("R Knee"),
    RIGHT_ANKLE("R Ankle"),
    RIGHT_FOOT("R Foot"),
}

data class Bone(val parent: JointId, val child: JointId, val width: Float)

data class CameraState(
    val yawDegrees: Float = 18f,
    val pitchDegrees: Float = -4f,
    val distance: Float = 7.2f,
    val fovDegrees: Float = 38f,
)

data class LightState(
    val azimuthDegrees: Float = -35f,
    val elevationDegrees: Float = 48f,
    val intensity: Float = 0.82f,
)

data class PoseProject(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Pose",
    val modifiedAt: Long = System.currentTimeMillis(),
    val joints: Map<JointId, Vec3> = Mannequin.neutral(),
    val camera: CameraState = CameraState(),
    val light: LightState = LightState(),
)

enum class PosePreset(val label: String) {
    NEUTRAL("Neutral"),
    CONTRAPPOSTO("Contrapposto"),
    REACH("Reach"),
    RUN("Run"),
}

object Mannequin {
    val bones = listOf(
        Bone(JointId.PELVIS, JointId.SPINE, 17f),
        Bone(JointId.SPINE, JointId.CHEST, 20f),
        Bone(JointId.CHEST, JointId.NECK, 12f),
        Bone(JointId.NECK, JointId.HEAD, 14f),
        Bone(JointId.CHEST, JointId.LEFT_SHOULDER, 14f),
        Bone(JointId.LEFT_SHOULDER, JointId.LEFT_ELBOW, 12f),
        Bone(JointId.LEFT_ELBOW, JointId.LEFT_WRIST, 9f),
        Bone(JointId.CHEST, JointId.RIGHT_SHOULDER, 14f),
        Bone(JointId.RIGHT_SHOULDER, JointId.RIGHT_ELBOW, 12f),
        Bone(JointId.RIGHT_ELBOW, JointId.RIGHT_WRIST, 9f),
        Bone(JointId.PELVIS, JointId.LEFT_HIP, 17f),
        Bone(JointId.LEFT_HIP, JointId.LEFT_KNEE, 17f),
        Bone(JointId.LEFT_KNEE, JointId.LEFT_ANKLE, 13f),
        Bone(JointId.LEFT_ANKLE, JointId.LEFT_FOOT, 8f),
        Bone(JointId.PELVIS, JointId.RIGHT_HIP, 17f),
        Bone(JointId.RIGHT_HIP, JointId.RIGHT_KNEE, 17f),
        Bone(JointId.RIGHT_KNEE, JointId.RIGHT_ANKLE, 13f),
        Bone(JointId.RIGHT_ANKLE, JointId.RIGHT_FOOT, 8f),
    )

    val parent: Map<JointId, JointId> = bones.associate { it.child to it.parent }
    val children: Map<JointId, List<JointId>> = JointId.entries.associateWith { joint ->
        bones.filter { it.parent == joint }.map { it.child }
    }

    fun neutral(): Map<JointId, Vec3> = linkedMapOf(
        JointId.PELVIS to Vec3(0f, 0f, 0f),
        JointId.SPINE to Vec3(0f, 0.62f, 0f),
        JointId.CHEST to Vec3(0f, 1.2f, 0f),
        JointId.NECK to Vec3(0f, 1.55f, 0f),
        JointId.HEAD to Vec3(0f, 1.9f, 0f),
        JointId.LEFT_SHOULDER to Vec3(-0.42f, 1.43f, 0f),
        JointId.LEFT_ELBOW to Vec3(-0.82f, 1.12f, 0.03f),
        JointId.LEFT_WRIST to Vec3(-1.08f, 0.78f, 0f),
        JointId.RIGHT_SHOULDER to Vec3(0.42f, 1.43f, 0f),
        JointId.RIGHT_ELBOW to Vec3(0.82f, 1.12f, 0.03f),
        JointId.RIGHT_WRIST to Vec3(1.08f, 0.78f, 0f),
        JointId.LEFT_HIP to Vec3(-0.22f, -0.12f, 0f),
        JointId.LEFT_KNEE to Vec3(-0.24f, -0.92f, 0.04f),
        JointId.LEFT_ANKLE to Vec3(-0.25f, -1.72f, 0f),
        JointId.LEFT_FOOT to Vec3(-0.25f, -1.82f, 0.28f),
        JointId.RIGHT_HIP to Vec3(0.22f, -0.12f, 0f),
        JointId.RIGHT_KNEE to Vec3(0.24f, -0.92f, 0.04f),
        JointId.RIGHT_ANKLE to Vec3(0.25f, -1.72f, 0f),
        JointId.RIGHT_FOOT to Vec3(0.25f, -1.82f, 0.28f),
    )

    fun preset(preset: PosePreset): Map<JointId, Vec3> {
        val p = neutral().toMutableMap()
        when (preset) {
            PosePreset.NEUTRAL -> Unit
            PosePreset.CONTRAPPOSTO -> {
                p[JointId.PELVIS] = Vec3(0.08f, 0.02f, 0f)
                p[JointId.CHEST] = Vec3(-0.06f, 1.2f, 0.02f)
                p[JointId.LEFT_HIP] = Vec3(-0.18f, -0.1f, 0.03f)
                p[JointId.LEFT_KNEE] = Vec3(-0.34f, -0.88f, 0.08f)
                p[JointId.LEFT_ANKLE] = Vec3(-0.38f, -1.68f, 0.06f)
                p[JointId.RIGHT_KNEE] = Vec3(0.15f, -0.86f, -0.06f)
                p[JointId.RIGHT_ANKLE] = Vec3(0.08f, -1.65f, -0.1f)
                p[JointId.LEFT_ELBOW] = Vec3(-0.64f, 0.98f, 0.18f)
                p[JointId.LEFT_WRIST] = Vec3(-0.48f, 0.66f, 0.22f)
            }
            PosePreset.REACH -> {
                p[JointId.RIGHT_SHOULDER] = Vec3(0.42f, 1.45f, 0f)
                p[JointId.RIGHT_ELBOW] = Vec3(0.78f, 1.77f, 0.03f)
                p[JointId.RIGHT_WRIST] = Vec3(0.98f, 2.15f, 0.02f)
                p[JointId.LEFT_ELBOW] = Vec3(-0.72f, 1.18f, 0.14f)
                p[JointId.LEFT_WRIST] = Vec3(-0.92f, 0.92f, 0.2f)
                p[JointId.CHEST] = Vec3(0.04f, 1.23f, 0.04f)
            }
            PosePreset.RUN -> {
                p[JointId.CHEST] = Vec3(0f, 1.18f, 0.16f)
                p[JointId.HEAD] = Vec3(0f, 1.86f, 0.18f)
                p[JointId.LEFT_ELBOW] = Vec3(-0.55f, 1.2f, -0.34f)
                p[JointId.LEFT_WRIST] = Vec3(-0.3f, 0.92f, -0.52f)
                p[JointId.RIGHT_ELBOW] = Vec3(0.55f, 1.2f, 0.36f)
                p[JointId.RIGHT_WRIST] = Vec3(0.28f, 0.9f, 0.58f)
                p[JointId.LEFT_KNEE] = Vec3(-0.18f, -0.76f, 0.48f)
                p[JointId.LEFT_ANKLE] = Vec3(-0.16f, -1.45f, 0.82f)
                p[JointId.LEFT_FOOT] = Vec3(-0.16f, -1.53f, 1.1f)
                p[JointId.RIGHT_KNEE] = Vec3(0.32f, -0.7f, -0.48f)
                p[JointId.RIGHT_ANKLE] = Vec3(0.48f, -1.25f, -0.88f)
                p[JointId.RIGHT_FOOT] = Vec3(0.48f, -1.32f, -1.12f)
            }
        }
        return PoseMath.enforceBoneLengths(p)
    }
}
