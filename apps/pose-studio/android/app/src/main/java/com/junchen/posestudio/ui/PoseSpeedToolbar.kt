package com.junchen.posestudio.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PoseSpeedToolbar(
    viewModel: PoseStudioViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(top = 68.dp, start = 12.dp, end = 12.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Speed", style = MaterialTheme.typography.labelMedium)
                AssistChip(onClick = viewModel::mirrorPose, label = { Text("Mirror") })
                AssistChip(onClick = viewModel::copyLeftArmToRight, label = { Text("L arm → R") })
                AssistChip(onClick = viewModel::copyRightArmToLeft, label = { Text("R arm → L") })
                AssistChip(onClick = viewModel::copyLeftLegToRight, label = { Text("L leg → R") })
                AssistChip(onClick = viewModel::copyRightLegToLeft, label = { Text("R leg → L") })
                AssistChip(onClick = viewModel::groundFeet, label = { Text("Ground") })
            }
        }
    }
}
