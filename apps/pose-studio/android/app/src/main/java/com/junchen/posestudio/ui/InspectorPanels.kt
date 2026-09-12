package com.junchen.posestudio.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junchen.posestudio.R
import com.junchen.posestudio.data.ProjectStore
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.Vec3
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PoseControls(viewModel: PoseStudioViewModel) {
    Text(stringResource(R.string.fast_pose), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.fast_pose_help))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosePreset.entries.forEach { preset ->
            FilterChip(
                selected = false,
                onClick = { viewModel.applyPreset(preset) },
                label = { Text(presetLabel(preset)) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::undo) { Text(stringResource(R.string.undo)) }
        OutlinedButton(onClick = viewModel::redo) { Text(stringResource(R.string.redo)) }
    }

    HorizontalDivider()
    Text(stringResource(R.string.select_joint), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.select_joint_help))
    val jointRows = listOf(
        listOf(JointId.PELVIS, JointId.SPINE, JointId.CHEST, JointId.NECK, JointId.HEAD),
        listOf(
            JointId.LEFT_SHOULDER,
            JointId.LEFT_ELBOW,
            JointId.LEFT_WRIST,
            JointId.RIGHT_SHOULDER,
            JointId.RIGHT_ELBOW,
            JointId.RIGHT_WRIST,
        ),
        listOf(
            JointId.LEFT_HIP,
            JointId.LEFT_KNEE,
            JointId.LEFT_ANKLE,
            JointId.LEFT_FOOT,
            JointId.RIGHT_HIP,
            JointId.RIGHT_KNEE,
            JointId.RIGHT_ANKLE,
            JointId.RIGHT_FOOT,
        ),
    )
    jointRows.forEach { joints ->
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            joints.forEach { joint ->
                FilterChip(
                    selected = viewModel.selectedJoint == joint,
                    onClick = { viewModel.selectJoint(joint) },
                    label = { Text(jointLabel(joint)) },
                )
            }
        }
    }

    viewModel.selectedJoint?.let { joint ->
        Text(stringResource(R.string.selected_joint, jointLabel(joint)), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.accessible_adjustment_help))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { viewModel.nudgeSelected(Vec3(-0.06f, 0f, 0f)) }) { Text(stringResource(R.string.left)) }
            OutlinedButton(onClick = { viewModel.nudgeSelected(Vec3(0.06f, 0f, 0f)) }) { Text(stringResource(R.string.right)) }
            OutlinedButton(onClick = { viewModel.nudgeSelected(Vec3(0f, 0.06f, 0f)) }) { Text(stringResource(R.string.up)) }
            OutlinedButton(onClick = { viewModel.nudgeSelected(Vec3(0f, -0.06f, 0f)) }) { Text(stringResource(R.string.down)) }
            OutlinedButton(onClick = { viewModel.nudgeSelected(Vec3(0f, 0f, 0.06f)) }) { Text(stringResource(R.string.forward)) }
            OutlinedButton(onClick = { viewModel.nudgeSelected(Vec3(0f, 0f, -0.06f)) }) { Text(stringResource(R.string.back)) }
        }
    }
}

@Composable
internal fun CameraControls(viewModel: PoseStudioViewModel) {
    val camera = viewModel.project.camera
    Text(stringResource(R.string.camera), style = MaterialTheme.typography.titleMedium)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 0f, pitchDegrees = 0f) } }) {
            Text(stringResource(R.string.front))
        }
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 45f, pitchDegrees = -4f) } }) {
            Text(stringResource(R.string.three_quarter))
        }
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 90f, pitchDegrees = 0f) } }) {
            Text(stringResource(R.string.side))
        }
    }
    LabeledSlider(stringResource(R.string.yaw), camera.yawDegrees, -180f..180f) { value ->
        viewModel.updateCamera { it.copy(yawDegrees = value) }
    }
    LabeledSlider(stringResource(R.string.pitch), camera.pitchDegrees, -65f..65f) { value ->
        viewModel.updateCamera { it.copy(pitchDegrees = value) }
    }
    LabeledSlider(stringResource(R.string.distance), camera.distance, 3.8f..12f) { value ->
        viewModel.updateCamera { it.copy(distance = value) }
    }
    LabeledSlider(stringResource(R.string.field_of_view), camera.fovDegrees, 22f..70f) { value ->
        viewModel.updateCamera { it.copy(fovDegrees = value) }
    }
}

@Composable
internal fun LightControls(viewModel: PoseStudioViewModel) {
    val light = viewModel.project.light
    Text(stringResource(R.string.directional_light), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.light_help))
    LabeledSlider(stringResource(R.string.azimuth), light.azimuthDegrees, -180f..180f) { value ->
        viewModel.updateLight { it.copy(azimuthDegrees = value) }
    }
    LabeledSlider(stringResource(R.string.elevation), light.elevationDegrees, -20f..85f) { value ->
        viewModel.updateLight { it.copy(elevationDegrees = value) }
    }
    LabeledSlider(stringResource(R.string.intensity), light.intensity, 0.15f..1f) { value ->
        viewModel.updateLight { it.copy(intensity = value) }
    }
}

@Composable
internal fun ProjectControls(
    viewModel: PoseStudioViewModel,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onNew: () -> Unit,
    onExportPng: () -> Unit,
    onExportTransparentPng: () -> Unit,
    onExportProject: () -> Unit,
    onImportProject: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val enabled = !viewModel.projectBusy
    Text(stringResource(R.string.project), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = viewModel.project.name,
        onValueChange = viewModel::rename,
        label = { Text(stringResource(R.string.name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, enabled = enabled) { Text(stringResource(R.string.save)) }
        OutlinedButton(onClick = onOpen, enabled = enabled) { Text(stringResource(R.string.open)) }
        OutlinedButton(onClick = onNew, enabled = enabled) { Text(stringResource(R.string.new_project)) }
    }
    HorizontalDivider()
    Text(stringResource(R.string.portable_files), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.portable_files_help))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onExportProject, enabled = enabled) { Text(stringResource(R.string.export_json)) }
        OutlinedButton(onClick = onImportProject, enabled = enabled) { Text(stringResource(R.string.import_json)) }
        OutlinedButton(onClick = onExportPng, enabled = enabled) { Text(stringResource(R.string.export_png)) }
        OutlinedButton(onClick = onExportTransparentPng, enabled = enabled) { Text(stringResource(R.string.export_transparent_png)) }
    }
    HorizontalDivider()
    OutlinedButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_policy)) }
    if (viewModel.corruptProjects.isNotEmpty()) {
        HorizontalDivider()
        Text(
            stringResource(R.string.corrupt_projects, viewModel.corruptProjects.size),
            color = MaterialTheme.colorScheme.error,
        )
        viewModel.corruptProjects.take(3).forEach { Text(it.fileName, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(String.format(locale, "%.1f", value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
internal fun OpenProjectDialog(
    projects: List<ProjectStore.SavedProject>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (ProjectStore.SavedProject) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.open_local_project)) },
        text = {
            Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                if (projects.isEmpty()) Text(stringResource(R.string.no_saved_projects))
                else projects.forEach { project ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(project.name, fontWeight = FontWeight.Medium)
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(project.modifiedAt)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onOpen(project.id) }, enabled = !busy) { Text(stringResource(R.string.open)) }
                            TextButton(onClick = { onDuplicate(project.id) }, enabled = !busy) { Text(stringResource(R.string.duplicate)) }
                            TextButton(onClick = { onDelete(project) }, enabled = !busy) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
