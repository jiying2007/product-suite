package com.junchen.posestudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.calculatePan
import androidx.compose.ui.input.pointer.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.junchen.posestudio.engine.SceneProjection
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import com.junchen.posestudio.render.PoseRenderBuilder
import kotlin.math.abs

@Composable
fun PoseScene(
    project: PoseProject,
    selectedJoint: JointId?,
    selectedJointLabel: String?,
    sceneDescription: String,
    onSelect: (JointId?) -> Unit,
    onPoseStart: () -> Unit,
    onPoseEnd: () -> Unit,
    onDragJoint: (JointId, Vec3) -> Unit,
    onOrbit: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val hitRadiusPx = with(LocalDensity.current) { 48.dp.toPx() }
    val model = remember(project.joints, project.camera, project.light, canvasSize) {
        if (canvasSize.width == 0) null else PoseRenderBuilder.build(
            project,
            canvasSize.width.toFloat(),
            canvasSize.height.toFloat(),
        )
    }
    val currentModel by rememberUpdatedState(model)
    val currentCamera by rememberUpdatedState(project.camera)
    val background = MaterialTheme.colorScheme.background
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val selectionColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .semantics {
                contentDescription = sceneDescription
                selectedJointLabel?.let { stateDescription = it }
            }
            .pointerInput(canvasSize, hitRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startModel = currentModel
                    var activeJoint = startModel?.projected?.entries
                        ?.map { (joint, point) -> joint to Offset(point.x, point.y) }
                        ?.minByOrNull { (_, point) -> (point - down.position).getDistance() }
                        ?.takeIf { (_, point) -> (point - down.position).getDistance() <= hitRadiusPx }
                        ?.first
                    var activeDepth = activeJoint?.let { startModel?.projected?.get(it)?.depth } ?: currentCamera.distance
                    var poseStarted = activeJoint != null
                    onSelect(activeJoint)
                    if (poseStarted) onPoseStart()

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (poseStarted) {
                                onPoseEnd()
                                poseStarted = false
                            }
                            activeJoint = null
                            onSelect(null)
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom.isFinite() && abs(zoom - 1f) > 0.001f) onZoom(zoom)
                            if (pan.getDistance() > 0.01f) onOrbit(pan.x, pan.y)
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            val delta = change.positionChange()
                            if (delta.getDistance() > 0f) {
                                val joint = activeJoint
                                if (joint != null) {
                                    activeDepth = currentModel?.projected?.get(joint)?.depth ?: activeDepth
                                    onDragJoint(
                                        joint,
                                        SceneProjection.screenDeltaToWorld(
                                            delta.x,
                                            delta.y,
                                            currentCamera,
                                            canvasSize.width.toFloat(),
                                            activeDepth,
                                        ),
                                    )
                                } else {
                                    onOrbit(delta.x, delta.y)
                                }
                                change.consume()
                            }
                        }
                    }
                    if (poseStarted) onPoseEnd()
                }
            },
    ) {
        drawRect(background)
        val renderModel = model ?: return@Canvas
        renderModel.grid.forEach { line ->
            drawLine(gridColor, Offset(line.x1, line.y1), Offset(line.x2, line.y2), strokeWidth = line.width)
        }
        renderModel.volumes.forEach { volume ->
            drawOval(
                color = bodyColor.copy(alpha = (0.32f + volume.luminance * 0.55f).coerceAtMost(0.92f)),
                topLeft = Offset(volume.centerX - volume.radiusX, volume.centerY - volume.radiusY),
                size = Size(volume.radiusX * 2f, volume.radiusY * 2f),
            )
        }
        renderModel.bones.forEach { line ->
            drawLine(
                color = bodyColor.copy(alpha = (0.32f + line.luminance * 0.68f).coerceAtMost(0.98f)),
                start = Offset(line.x1, line.y1),
                end = Offset(line.x2, line.y2),
                strokeWidth = line.width,
                cap = StrokeCap.Round,
            )
        }
        JointId.entries.sortedByDescending { renderModel.projected.getValue(it).depth }.forEach { id ->
            val p = renderModel.projected.getValue(id)
            val chosen = id == selectedJoint
            val radius = (if (chosen) 9f else 5.5f) * (p.scale / 160f).coerceIn(0.75f, 1.7f)
            if (chosen) drawCircle(selectionColor.copy(alpha = 0.24f), radius * 2f, Offset(p.x, p.y))
            drawCircle(if (chosen) selectionColor else bodyColor, radius, Offset(p.x, p.y))
        }
    }
}
