package com.junchen.posestudio.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.junchen.posestudio.R

@Composable
fun PoseSpeedToolbar(viewModel: PoseStudioViewModel, modifier: Modifier = Modifier) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.speed), style = MaterialTheme.typography.labelMedium)
            AssistChip(onClick = viewModel::mirrorPose, label = { Text(stringResource(R.string.mirror)) })
            AssistChip(onClick = viewModel::copyLeftArmToRight, label = { Text(stringResource(R.string.left_arm_to_right)) })
            AssistChip(onClick = viewModel::copyRightArmToLeft, label = { Text(stringResource(R.string.right_arm_to_left)) })
            AssistChip(onClick = viewModel::copyLeftLegToRight, label = { Text(stringResource(R.string.left_leg_to_right)) })
            AssistChip(onClick = viewModel::copyRightLegToLeft, label = { Text(stringResource(R.string.right_leg_to_left)) })
            AssistChip(onClick = viewModel::groundFeet, label = { Text(stringResource(R.string.ground)) })
        }
    }
}
