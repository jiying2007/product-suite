package com.junchen.posestudio.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

private val ORIENTABLE_ENDPOINTS = setOf(
    JointId.LEFT_WRIST,
    JointId.RIGHT_WRIST,
    JointId.LEFT_FOOT,
    JointId.RIGHT_FOOT,
)

@Composable
internal fun PoseControls(viewModel: PoseStudioViewModel) {
    ReferenceOverlayControls()
    HorizontalDivider()

    Text(stringResource(R.string.select_joint), style = MaterialTheme.typography.titleSmall)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JointId.entries.forEach { joint ->
            FilterChip(
                selected = viewModel.selectedJoint == joint,
                onClick = { viewModel.selectJoint(joint) },
                label = { Text(jointLabel(joint)) },
            )
        }
    }

    viewModel.selectedJoint?.let { joint ->
        Text(stringResource(R.string.selected_joint, jointLabel(joint)), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.accessible_adjustment_help))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.nudgeSelected(Vec3(-0.06f, 0f, 0f)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.left)) }
                OutlinedButton(
                    onClick = { viewModel.nudgeSelected(Vec3(0.06f, 0f, 0f)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.right)) }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.nudgeSelected(Vec3(0f, 0.06f, 0f)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.up)) }
                OutlinedButton(
                    onClick = { viewModel.nudgeSelected(Vec3(0f, -0.06f, 0f)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.down)) }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.nudgeSelected(Vec3(0f, 0f, 0.06f)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.forward)) }
                OutlinedButton(
                    onClick = { viewModel.nudgeSelected(Vec3(0f, 0f, -0.06f)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.back)) }
            }
            if (joint in ORIENTABLE_ENDPOINTS) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.updateSelectedRoll(-15f) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.roll_minus)) }
                    OutlinedButton(
                        onClick = { viewModel.updateSelectedRoll(15f) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.roll_plus)) }
                }
            }
        }
    }

    HorizontalDivider()
    Text(stringResource(R.string.fast_pose), style = MaterialTheme.typography.titleMedium)
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
    PoseLibraryControls(viewModel)
}

@Composable
private fun PoseLibraryControls(viewModel: PoseStudioViewModel) {
    val scope = rememberCoroutineScope()
    val enabled = !viewModel.projectBusy
    Text(stringResource(R.string.pose_library), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.pose_library_help))
    Button(
        onClick = { scope.launch { viewModel.saveCurrentPoseToLibrary() } },
        enabled = enabled,
    ) { Text(stringResource(R.string.save_pose_to_library)) }

    if (viewModel.savedPoses.isEmpty()) {
        Text(stringResource(R.string.no_saved_poses), style = MaterialTheme.typography.labelMedium)
    } else {
        viewModel.savedPoses.forEach { saved ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(saved.name, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { viewModel.applySavedPose(saved.id) } },
                        enabled = enabled,
                    ) { Text(stringResource(R.string.apply_pose)) }
                    TextButton(
                        onClick = { scope.launch { viewModel.deleteSavedPose(saved.id) } },
                        enabled = enabled,
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
private fun ReferenceOverlayControls() {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ReferenceOverlaySession.use(uri)
        }
    }

    Text(stringResource(R.string.reference_overlay), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.reference_overlay_help))

    if (ReferenceOverlaySession.uri == null) {
        Button(onClick = { picker.launch(arrayOf("image/*")) }) {
            Text(stringResource(R.string.choose_reference))
        }
        return
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { picker.launch(arrayOf("image/*")) }) {
            Text(stringResource(R.string.choose_reference))
        }
        OutlinedButton(onClick = { ReferenceOverlaySession.visible = !ReferenceOverlaySession.visible }) {
            Text(
                stringResource(
                    if (ReferenceOverlaySession.visible) R.string.hide_reference else R.string.show_reference,
                ),
            )
        }
        OutlinedButton(onClick = ReferenceOverlaySession::resetTransform) {
            Text(stringResource(R.string.reset_reference))
        }
        OutlinedButton(onClick = ReferenceOverlaySession::clear) {
            Text(stringResource(R.string.clear_reference))
        }
    }
    LabeledSlider(stringResource(R.string.reference_opacity), ReferenceOverlaySession.opacity, 0.1f..0.95f) {
        ReferenceOverlaySession.opacity = it
    }
    LabeledSlider(stringResource(R.string.reference_scale), ReferenceOverlaySession.scale, 0.5f..2.5f) {
        ReferenceOverlaySession.scale = it
    }
    LabeledSlider(stringResource(R.string.reference_horizontal), ReferenceOverlaySession.offsetXDp, -220f..220f) {
        ReferenceOverlaySession.offsetXDp = it
    }
    LabeledSlider(stringResource(R.string.reference_vertical), ReferenceOverlaySession.offsetYDp, -220f..220f) {
        ReferenceOverlaySession.offsetYDp = it
    }
    Text(stringResource(R.string.reference_session_only), style = MaterialTheme.typography.labelSmall)
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
    onExportSilhouettePng: () -> Unit,
    onExportConstructionPng: () -> Unit,
    onExportProject: () -> Unit,
    onImportProject: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val enabled = !viewModel.projectBusy

    Text(stringResource(R.string.drawing_exports), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.drawing_exports_help))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onExportPng, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_png))
            }
            OutlinedButton(onClick = onExportTransparentPng, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_transparent_png))
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onExportSilhouettePng, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_silhouette_png))
            }
            OutlinedButton(onClick = onExportConstructionPng, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_construction_png))
            }
        }
    }

    HorizontalDivider()
    Text(stringResource(R.string.project), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = viewModel.project.name,
        onValueChange = viewModel::rename,
        label = { Text(stringResource(R.string.name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
        OutlinedButton(onClick = onOpen, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.open)) }
    }
    OutlinedButton(onClick = onNew, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.new_project))
    }

    HorizontalDivider()
    Text(stringResource(R.string.portable_files), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.portable_files_help))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onExportProject, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.export_json))
        }
        OutlinedButton(onClick = onImportProject, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.import_json))
        }
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
