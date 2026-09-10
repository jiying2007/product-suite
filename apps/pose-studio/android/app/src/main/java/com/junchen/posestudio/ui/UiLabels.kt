package com.junchen.posestudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.junchen.posestudio.R
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.PosePreset

@Composable
fun jointLabel(id: JointId): String = stringResource(
    when (id) {
        JointId.PELVIS -> R.string.joint_pelvis
        JointId.SPINE -> R.string.joint_spine
        JointId.CHEST -> R.string.joint_chest
        JointId.NECK -> R.string.joint_neck
        JointId.HEAD -> R.string.joint_head
        JointId.LEFT_SHOULDER -> R.string.joint_left_shoulder
        JointId.LEFT_ELBOW -> R.string.joint_left_elbow
        JointId.LEFT_WRIST -> R.string.joint_left_wrist
        JointId.RIGHT_SHOULDER -> R.string.joint_right_shoulder
        JointId.RIGHT_ELBOW -> R.string.joint_right_elbow
        JointId.RIGHT_WRIST -> R.string.joint_right_wrist
        JointId.LEFT_HIP -> R.string.joint_left_hip
        JointId.LEFT_KNEE -> R.string.joint_left_knee
        JointId.LEFT_ANKLE -> R.string.joint_left_ankle
        JointId.LEFT_FOOT -> R.string.joint_left_foot
        JointId.RIGHT_HIP -> R.string.joint_right_hip
        JointId.RIGHT_KNEE -> R.string.joint_right_knee
        JointId.RIGHT_ANKLE -> R.string.joint_right_ankle
        JointId.RIGHT_FOOT -> R.string.joint_right_foot
    },
)

@Composable
fun presetLabel(preset: PosePreset): String = stringResource(
    when (preset) {
        PosePreset.NEUTRAL -> R.string.preset_neutral
        PosePreset.CONTRAPPOSTO -> R.string.preset_contrapposto
        PosePreset.REACH -> R.string.preset_reach
        PosePreset.RUN -> R.string.preset_run
    },
)
