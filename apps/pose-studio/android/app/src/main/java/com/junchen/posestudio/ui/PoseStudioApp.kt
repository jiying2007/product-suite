package com.junchen.posestudio.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junchen.posestudio.data.ProjectStore
import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.LightState
import com.junchen.posestudio.model.PosePreset
import java.text.DateFormat
import java.util.Date

private enum class Panel(val label: String) { POSE("Pose"), CAMERA("Camera"), LIGHT("Light"), PROJECT("Project") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoseStudioApp(viewModel: PoseStudioViewModel) {
    val context = LocalContext.current
    var panel by remember { mutableStateOf(Panel.POSE) }
    var showOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportPng = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    check(viewModel.renderPng().compress(Bitmap.CompressFormat.PNG, 100, stream))
                } ?: error("Could not open export destination")
            }.onSuccess { message = "PNG exported" }
                .onFailure { message = "PNG export failed: ${it.message ?: "unknown error"}" }
        }
    }
    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(viewModel.exportJson()) }
                    ?: error("Could not open export destination")
            }.onSuccess { message = "Project exported" }
                .onFailure { message = "Project export failed: ${it.message ?: "unknown error"}" }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read project")
                viewModel.importJson(text)
            }.onSuccess { message = "Project imported" }
                .onFailure { message = "Import failed: ${it.message ?: "invalid project"}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pose Studio", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = viewModel.project.name + if (viewModel.dirty) " •" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(); message = "Saved locally" }) { Text("Save") }
                    TextButton(onClick = { showOpen = true }) { Text("Open") }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding),
        ) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SceneArea(viewModel, Modifier.weight(1f).fillMaxHeight())
                    ControlArea(
                        viewModel = viewModel,
                        panel = panel,
                        onPanel = { panel = it },
                        onOpen = { showOpen = true },
                        onExportPng = { exportPng.launch(fileName(viewModel.project.name, "png")) },
                        onExportProject = { exportJson.launch(fileName(viewModel.project.name, "pose.json")) },
                        onImportProject = { importJson.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SceneArea(viewModel, Modifier.weight(1f).fillMaxWidth())
                    ControlArea(
                        viewModel = viewModel,
                        panel = panel,
                        onPanel = { panel = it },
                        onOpen = { showOpen = true },
                        onExportPng = { exportPng.launch(fileName(viewModel.project.name, "png")) },
                        onExportProject = { exportJson.launch(fileName(viewModel.project.name, "pose.json")) },
                        onImportProject = { importJson.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }
            }
            message?.let { status ->
                androidx.compose.material3.Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = { TextButton(onClick = { message = null }) { Text("OK") } },
                ) { Text(status) }
            }
        }
    }

    if (showOpen) {
        OpenProjectDialog(
            projects = viewModel.savedProjects,
            onDismiss = { showOpen = false },
            onOpen = { id -> viewModel.load(id); showOpen = false },
        )
    }
}

@Composable
private fun SceneArea(viewModel: PoseStudioViewModel, modifier: Modifier) {
    Box(modifier) {
        PoseScene(
            project = viewModel.project,
            selectedJoint = viewModel.selectedJoint,
            onSelect = viewModel::selectJoint,
            onPoseStart = viewModel::beginPoseGesture,
            onPoseEnd = viewModel::endPoseGesture,
            onDragJoint = viewModel::dragJoint,
            onOrbit = viewModel::orbit,
        )
        Text(
            text = viewModel.selectedJoint?.let { "${it.label} selected • drag joint" }
                ?: "Drag empty space to orbit • drag a joint to pose",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ControlArea(
    viewModel: PoseStudioViewModel,
    panel: Panel,
    onPanel: (Panel) -> Unit,
    onOpen: () -> Unit,
    onExportPng: () -> Unit,
    onExportProject: () -> Unit,
    onImportProject: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        TabRow(selectedTabIndex = panel.ordinal) {
            Panel.entries.forEach { item ->
                Tab(selected = item == panel, onClick = { onPanel(item) }, text = { Text(item.label) })
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (panel) {
                Panel.POSE -> PoseControls(viewModel)
                Panel.CAMERA -> CameraControls(viewModel)
                Panel.LIGHT -> LightControls(viewModel)
                Panel.PROJECT -> ProjectControls(viewModel, onOpen, onExportPng, onExportProject, onImportProject)
            }
        }
    }
}

@Composable
private fun PoseControls(viewModel: PoseStudioViewModel) {
    Text("Fast pose", style = MaterialTheme.typography.titleMedium)
    Text("Tap a preset, then drag joints directly. Wrists and ankles use two-bone IK.")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosePreset.entries.forEach { preset ->
            FilterChip(selected = false, onClick = { viewModel.applyPreset(preset) }, label = { Text(preset.label) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::undo) { Text("Undo") }
        OutlinedButton(onClick = viewModel::redo) { Text("Redo") }
    }
    HorizontalDivider()
    Text("Interaction", style = MaterialTheme.typography.titleSmall)
    Text("• Drag wrist/ankle: two-bone IK\n• Drag another joint: rotate that branch around its parent\n• Drag pelvis: move the full mannequin\n• Drag empty scene: orbit camera")
}

@Composable
private fun CameraControls(viewModel: PoseStudioViewModel) {
    val camera = viewModel.project.camera
    Text("Camera", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 0f, pitchDegrees = 0f) } }) { Text("Front") }
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 45f, pitchDegrees = -4f) } }) { Text("3/4") }
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 90f, pitchDegrees = 0f) } }) { Text("Side") }
    }
    LabeledSlider("Yaw", camera.yawDegrees, -180f..180f) { value -> viewModel.updateCamera { it.copy(yawDegrees = value) } }
    LabeledSlider("Pitch", camera.pitchDegrees, -65f..65f) { value -> viewModel.updateCamera { it.copy(pitchDegrees = value) } }
    LabeledSlider("Distance", camera.distance, 4.2f..11f) { value -> viewModel.updateCamera { it.copy(distance = value) } }
    LabeledSlider("Field of view", camera.fovDegrees, 22f..70f) { value -> viewModel.updateCamera { it.copy(fovDegrees = value) } }
}

@Composable
private fun LightControls(viewModel: PoseStudioViewModel) {
    val light = viewModel.project.light
    Text("Directional light", style = MaterialTheme.typography.titleMedium)
    Text("Use light direction to clarify form; exported PNG uses the same setup.")
    LabeledSlider("Azimuth", light.azimuthDegrees, -180f..180f) { value -> viewModel.updateLight { it.copy(azimuthDegrees = value) } }
    LabeledSlider("Elevation", light.elevationDegrees, -20f..85f) { value -> viewModel.updateLight { it.copy(elevationDegrees = value) } }
    LabeledSlider("Intensity", light.intensity, 0.15f..1f) { value -> viewModel.updateLight { it.copy(intensity = value) } }
}

@Composable
private fun ProjectControls(
    viewModel: PoseStudioViewModel,
    onOpen: () -> Unit,
    onExportPng: () -> Unit,
    onExportProject: () -> Unit,
    onImportProject: () -> Unit,
) {
    Text("Project", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = viewModel.project.name,
        onValueChange = viewModel::rename,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::save) { Text("Save") }
        OutlinedButton(onClick = onOpen) { Text("Open") }
        OutlinedButton(onClick = viewModel::newProject) { Text("New") }
    }
    HorizontalDivider()
    Text("Portable files", style = MaterialTheme.typography.titleSmall)
    Text("Project files are versioned JSON and never contain an entitlement gate.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExportProject) { Text("Export JSON") }
        OutlinedButton(onClick = onImportProject) { Text("Import JSON") }
        OutlinedButton(onClick = onExportPng) { Text("Export PNG") }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(String.format("%.1f", value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun OpenProjectDialog(
    projects: List<ProjectStore.SavedProject>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open local project") },
        text = {
            Column(Modifier.fillMaxWidth().height(320.dp).verticalScroll(rememberScrollState())) {
                if (projects.isEmpty()) {
                    Text("No saved projects yet.")
                } else {
                    projects.forEach { project ->
                        TextButton(onClick = { onOpen(project.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(project.name, fontWeight = FontWeight.Medium)
                                Text(DateFormat.getDateTimeInstance().format(Date(project.modifiedAt)), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun fileName(name: String, extension: String): String {
    val safe = name.trim().ifBlank { "pose" }.replace(Regex("[^A-Za-z0-9._-]+"), "-").take(48)
    return "$safe.$extension"
}
