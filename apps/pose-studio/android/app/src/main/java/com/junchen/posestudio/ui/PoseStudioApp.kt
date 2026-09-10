package com.junchen.posestudio.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junchen.posestudio.R
import com.junchen.posestudio.data.ProjectStore
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Reader
import java.text.DateFormat
import java.util.Date

private enum class Panel { POSE, CAMERA, LIGHT, PROJECT }
private enum class PendingType { NEW, OPEN, IMPORT }
private data class PendingProjectAction(
    val type: PendingType,
    val id: String? = null,
    val payload: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoseStudioApp(viewModel: PoseStudioViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf(Panel.POSE) }
    var showOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingProjectAction?>(null) }
    var pendingDelete by remember { mutableStateOf<ProjectStore.SavedProject?>(null) }

    fun execute(action: PendingProjectAction) {
        runCatching {
            when (action.type) {
                PendingType.NEW -> viewModel.newProject()
                PendingType.OPEN -> viewModel.load(requireNotNull(action.id))
                PendingType.IMPORT -> viewModel.importJson(requireNotNull(action.payload))
            }
        }.onSuccess {
            if (action.type == PendingType.IMPORT) message = resources.getString(R.string.project_imported)
        }.onFailure { error ->
            message = if (action.type == PendingType.IMPORT) {
                resources.getString(R.string.import_failed, error.message ?: "invalid project")
            } else {
                resources.getString(R.string.action_failed, error.message ?: "unknown")
            }
        }
    }

    fun request(action: PendingProjectAction) {
        if (viewModel.dirty) pendingAction = action else execute(action)
    }

    fun saveWithFeedback(onSuccess: (() -> Unit)? = null) {
        runCatching(viewModel::save)
            .onSuccess {
                message = resources.getString(R.string.saved_locally)
                onSuccess?.invoke()
            }
            .onFailure { message = resources.getString(R.string.save_failed, it.message ?: "unknown") }
    }

    fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
        runCatching { context.startActivity(intent) }
            .onFailure { message = resources.getString(R.string.privacy_open_failed) }
    }

    fun writePng(uri: Uri, transparentBackground: Boolean) {
        val snapshot = viewModel.project
        scope.launch {
            val result = runCatching {
                val bitmap = withContext(Dispatchers.Default) {
                    viewModel.renderPng(snapshot, transparentBackground = transparentBackground)
                }
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        } ?: error("Could not open export destination")
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            if (result.isSuccess) {
                message = resources.getString(
                    if (transparentBackground) R.string.transparent_png_exported else R.string.png_exported,
                )
                viewModel.recordExportSuccess()
            } else {
                message = resources.getString(
                    R.string.png_export_failed,
                    result.exceptionOrNull()?.message ?: "unknown",
                )
            }
        }
    }

    val exportPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) writePng(uri, transparentBackground = false)
    }
    val exportTransparentPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) writePng(uri, transparentBackground = true)
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val json = viewModel.exportJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                            ?: error("Could not open export destination")
                    }
                }
                if (result.isSuccess) {
                    message = resources.getString(R.string.project_exported)
                    viewModel.recordExportSuccess()
                } else message = resources.getString(R.string.project_export_failed, result.exceptionOrNull()?.message ?: "unknown")
            }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use(::readLimited)
                            ?: error("Could not read project")
                    }
                }
                result.onSuccess { text -> request(PendingProjectAction(PendingType.IMPORT, payload = text)) }
                    .onFailure { error ->
                        message = resources.getString(R.string.import_failed, error.message ?: "invalid project")
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = viewModel.project.name + if (viewModel.dirty) " •" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { saveWithFeedback() }) { Text(stringResource(R.string.save)) }
                    TextButton(onClick = { showOpen = true }) { Text(stringResource(R.string.open)) }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight * 1.2f)
            val inspectorHeight = (maxHeight * 0.42f).coerceIn(220.dp, 340.dp)
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SceneArea(viewModel, Modifier.weight(1f).fillMaxHeight())
                    ControlArea(
                        viewModel, panel, { panel = it },
                        onSave = { saveWithFeedback() },
                        onOpen = { showOpen = true },
                        onNew = { request(PendingProjectAction(PendingType.NEW)) },
                        onExportPng = { exportPng.launch(exportFileName(viewModel.project.name, "png")) },
                        onExportTransparentPng = {
                            exportTransparentPng.launch(exportFileName(viewModel.project.name + "-transparent", "png"))
                        },
                        onExportProject = { exportJson.launch(exportFileName(viewModel.project.name, "pose.json")) },
                        onImportProject = { importJson.launch(arrayOf("application/json", "text/plain")) },
                        onPrivacy = ::openPrivacyPolicy,
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SceneArea(viewModel, Modifier.weight(1f).fillMaxWidth())
                    ControlArea(
                        viewModel, panel, { panel = it },
                        onSave = { saveWithFeedback() },
                        onOpen = { showOpen = true },
                        onNew = { request(PendingProjectAction(PendingType.NEW)) },
                        onExportPng = { exportPng.launch(exportFileName(viewModel.project.name, "png")) },
                        onExportTransparentPng = {
                            exportTransparentPng.launch(exportFileName(viewModel.project.name + "-transparent", "png"))
                        },
                        onExportProject = { exportJson.launch(exportFileName(viewModel.project.name, "pose.json")) },
                        onImportProject = { importJson.launch(arrayOf("application/json", "text/plain")) },
                        onPrivacy = ::openPrivacyPolicy,
                        modifier = Modifier.fillMaxWidth().height(inspectorHeight),
                    )
                }
            }
            message?.let { status ->
                androidx.compose.material3.Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    action = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } },
                ) { Text(status) }
            }
        }
    }

    if (showOpen) {
        OpenProjectDialog(
            projects = viewModel.savedProjects,
            onDismiss = { showOpen = false },
            onOpen = { id -> showOpen = false; request(PendingProjectAction(PendingType.OPEN, id)) },
            onDuplicate = { id ->
                runCatching { viewModel.duplicate(id) }
                    .onFailure { message = resources.getString(R.string.action_failed, it.message ?: "unknown") }
            },
            onDelete = { project ->
                showOpen = false
                pendingDelete = project
            },
        )
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_body)) },
            confirmButton = {
                TextButton(onClick = {
                    saveWithFeedback {
                        pendingAction = null
                        execute(action)
                    }
                }) { Text(stringResource(R.string.save_continue)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.discardUnsavedRecovery()
                        pendingAction = null
                        execute(action)
                    }) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = { pendingAction = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_project_body, project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        check(viewModel.delete(project.id)) { "Could not delete project" }
                    }.onFailure { message = resources.getString(R.string.action_failed, it.message ?: "unknown") }
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    viewModel.recoveryCandidate?.let { recovered ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRecovery,
            title = { Text(stringResource(R.string.recovery_title)) },
            text = { Text(stringResource(R.string.recovery_body, recovered.name)) },
            confirmButton = { TextButton(onClick = viewModel::restoreRecovery) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissRecovery) { Text(stringResource(R.string.discard)) } },
        )
    }
}

@Composable
private fun SceneArea(viewModel: PoseStudioViewModel, modifier: Modifier) {
    val selectedLabel = viewModel.selectedJoint?.let { jointLabel(it) }
    Box(modifier) {
        PoseScene(
            project = viewModel.project,
            selectedJoint = viewModel.selectedJoint,
            selectedJointLabel = selectedLabel,
            sceneDescription = stringResource(R.string.scene_accessibility),
            onSelect = viewModel::selectJoint,
            onPoseStart = viewModel::beginPoseGesture,
            onPoseEnd = viewModel::endPoseGesture,
            onDragJoint = viewModel::dragJoint,
            onOrbit = viewModel::orbit,
            onPan = viewModel::pan,
            onZoom = viewModel::zoom,
        )
        PoseSpeedToolbar(
            viewModel,
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 12.dp),
        )
        Text(
            text = selectedLabel?.let { stringResource(R.string.selected_drag, it) }
                ?: stringResource(R.string.scene_hint),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
        if (viewModel.onboardingStep >= 0) OnboardingCard(
            step = viewModel.onboardingStep,
            onSkip = viewModel::skipOnboarding,
            modifier = Modifier.align(Alignment.CenterStart).padding(12.dp),
        )
    }
}

@Composable
private fun OnboardingCard(step: Int, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (step) {
        0 -> stringResource(R.string.onboarding_drag_wrist)
        1 -> stringResource(R.string.onboarding_orbit)
        2 -> stringResource(R.string.onboarding_preset)
        else -> stringResource(R.string.onboarding_export)
    }
    Card(modifier = modifier.width(260.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.quick_start), style = MaterialTheme.typography.titleSmall)
            Text(text)
            TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
        }
    }
}

@Composable
private fun ControlArea(
    viewModel: PoseStudioViewModel,
    panel: Panel,
    onPanel: (Panel) -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onNew: () -> Unit,
    onExportPng: () -> Unit,
    onExportTransparentPng: () -> Unit,
    onExportProject: () -> Unit,
    onImportProject: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier,
) {
    val labels = listOf(R.string.pose, R.string.camera, R.string.light, R.string.project)
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = panel.ordinal) {
            Panel.entries.forEach { item ->
                Tab(
                    selected = item == panel,
                    onClick = { onPanel(item) },
                    text = { Text(stringResource(labels[item.ordinal])) },
                )
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
                Panel.PROJECT -> ProjectControls(
                    viewModel,
                    onSave,
                    onOpen,
                    onNew,
                    onExportPng,
                    onExportTransparentPng,
                    onExportProject,
                    onImportProject,
                    onPrivacy,
                )
            }
        }
    }
}

@Composable
private fun PoseControls(viewModel: PoseStudioViewModel) {
    Text(stringResource(R.string.fast_pose), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.fast_pose_help))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PosePreset.entries.forEach { preset ->
            FilterChip(selected = false, onClick = { viewModel.applyPreset(preset) }, label = { Text(presetLabel(preset)) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::undo) { Text(stringResource(R.string.undo)) }
        OutlinedButton(onClick = viewModel::redo) { Text(stringResource(R.string.redo)) }
    }
    viewModel.selectedJoint?.let { joint ->
        HorizontalDivider()
        Text(stringResource(R.string.selected_joint, jointLabel(joint)), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.accessible_adjustment_help))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun CameraControls(viewModel: PoseStudioViewModel) {
    val camera = viewModel.project.camera
    Text(stringResource(R.string.camera), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 0f, pitchDegrees = 0f) } }) { Text(stringResource(R.string.front)) }
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 45f, pitchDegrees = -4f) } }) { Text(stringResource(R.string.three_quarter)) }
        OutlinedButton(onClick = { viewModel.updateCamera { it.copy(yawDegrees = 90f, pitchDegrees = 0f) } }) { Text(stringResource(R.string.side)) }
    }
    LabeledSlider(stringResource(R.string.yaw), camera.yawDegrees, -180f..180f) { value -> viewModel.updateCamera { it.copy(yawDegrees = value) } }
    LabeledSlider(stringResource(R.string.pitch), camera.pitchDegrees, -65f..65f) { value -> viewModel.updateCamera { it.copy(pitchDegrees = value) } }
    LabeledSlider(stringResource(R.string.distance), camera.distance, 3.8f..12f) { value -> viewModel.updateCamera { it.copy(distance = value) } }
    LabeledSlider(stringResource(R.string.field_of_view), camera.fovDegrees, 22f..70f) { value -> viewModel.updateCamera { it.copy(fovDegrees = value) } }
}

@Composable
private fun LightControls(viewModel: PoseStudioViewModel) {
    val light = viewModel.project.light
    Text(stringResource(R.string.directional_light), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.light_help))
    LabeledSlider(stringResource(R.string.azimuth), light.azimuthDegrees, -180f..180f) { value -> viewModel.updateLight { it.copy(azimuthDegrees = value) } }
    LabeledSlider(stringResource(R.string.elevation), light.elevationDegrees, -20f..85f) { value -> viewModel.updateLight { it.copy(elevationDegrees = value) } }
    LabeledSlider(stringResource(R.string.intensity), light.intensity, 0.15f..1f) { value -> viewModel.updateLight { it.copy(intensity = value) } }
}

@Composable
private fun ProjectControls(
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
    Text(stringResource(R.string.project), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = viewModel.project.name,
        onValueChange = viewModel::rename,
        label = { Text(stringResource(R.string.name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave) { Text(stringResource(R.string.save)) }
        OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
        OutlinedButton(onClick = onNew) { Text(stringResource(R.string.new_project)) }
    }
    HorizontalDivider()
    Text(stringResource(R.string.portable_files), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.portable_files_help))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExportProject) { Text(stringResource(R.string.export_json)) }
        OutlinedButton(onClick = onImportProject) { Text(stringResource(R.string.import_json)) }
        OutlinedButton(onClick = onExportPng) { Text(stringResource(R.string.export_png)) }
        OutlinedButton(onClick = onExportTransparentPng) { Text(stringResource(R.string.export_transparent_png)) }
    }
    HorizontalDivider()
    OutlinedButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_policy)) }
    if (viewModel.corruptProjects.isNotEmpty()) {
        HorizontalDivider()
        Text(stringResource(R.string.corrupt_projects, viewModel.corruptProjects.size), color = MaterialTheme.colorScheme.error)
        viewModel.corruptProjects.take(3).forEach { Text(it.fileName, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
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
private fun OpenProjectDialog(
    projects: List<ProjectStore.SavedProject>,
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
                        Text(DateFormat.getDateTimeInstance().format(Date(project.modifiedAt)), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onOpen(project.id) }) { Text(stringResource(R.string.open)) }
                            TextButton(onClick = { onDuplicate(project.id) }) { Text(stringResource(R.string.duplicate)) }
                            TextButton(onClick = { onDelete(project) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun readLimited(reader: Reader, maxChars: Int = 2_000_000): String {
    val out = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        check(out.length + count <= maxChars) { "Project is too large" }
        out.append(buffer, 0, count)
    }
    return out.toString()
}

internal fun exportFileName(name: String, extension: String): String {
    val cleaned = name.trim()
        .ifBlank { "pose" }
        .replace(Regex("[\\p{Cc}/\\\\:*?\"<>|]+"), "-")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
        .ifBlank { "pose" }
    val codePoints = cleaned.codePoints().limit(48).toArray()
    val stem = String(codePoints, 0, codePoints.size).ifBlank { "pose" }
    return "$stem.$extension"
}

private const val PRIVACY_POLICY_URL =
    "https://github.com/jiying2007/product-suite/blob/main/apps/pose-studio/docs/PRIVACY_POLICY.md"
