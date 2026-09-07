package com.junchen.posestudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.junchen.posestudio.engine.SceneProjection
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun PoseScene(
    project: PoseProject,
    selectedJoint: JointId?,
    onSelect: (JointId?) -> Unit,
    onPoseStart: () -> Unit,
    onPoseEnd: () -> Unit,
    onDragJoint: (JointId, Vec3) -> Unit,
    onOrbit: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val projected = remember(project.joints, project.camera, canvasSize) {
        if (canvasSize.width == 0) emptyMap() else JointId.entries.associateWith { id ->
            SceneProjection.project(
                project.joints.getValue(id), project.camera,
                canvasSize.width.toFloat(), canvasSize.height.toFloat(),
            )
        }
    }
    val currentProjected by rememberUpdatedState(projected)
    val currentCamera by rememberUpdatedState(project.camera)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                var activeJoint: JointId? = null
                detectDragGestures(
                    onDragStart = { start ->
                        activeJoint = currentProjected.entries
                            .map { (joint, point) -> joint to Offset(point.x, point.y) }
                            .minByOrNull { (_, point) -> (point - start).getDistance() }
                            ?.takeIf { (_, point) -> (point - start).getDistance() <= 42f }
                            ?.first
                        onSelect(activeJoint)
                        if (activeJoint != null) onPoseStart()
                    },
                    onDragEnd = {
                        if (activeJoint != null) onPoseEnd()
                        activeJoint = null
                    },
                    onDragCancel = {
                        if (activeJoint != null) onPoseEnd()
                        activeJoint = null
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val joint = activeJoint
                    if (joint != null) {
                        onDragJoint(
                            joint,
                            SceneProjection.screenDeltaToWorld(
                                dragAmount.x, dragAmount.y, currentCamera, canvasSize.width.toFloat(),
                            ),
                        )
                    } else {
                        onOrbit(dragAmount.x, dragAmount.y)
                    }
                }
            },
    ) {
        drawRect(Color(0xFFF7F7F5))

        for (i in -5..5) {
            val a = SceneProjection.project(Vec3(i.toFloat(), -1.84f, -4f), project.camera, size.width, size.height)
            val b = SceneProjection.project(Vec3(i.toFloat(), -1.84f, 4f), project.camera, size.width, size.height)
            drawLine(Color(0xFFD8D8D4), Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 1.2f)
            val c = SceneProjection.project(Vec3(-4f, -1.84f, i.toFloat()), project.camera, size.width, size.height)
            val d = SceneProjection.project(Vec3(4f, -1.84f, i.toFloat()), project.camera, size.width, size.height)
            drawLine(Color(0xFFD8D8D4), Offset(c.x, c.y), Offset(d.x, d.y), strokeWidth = 1.2f)
        }

        val light = lightDirection(project)
        Mannequin.bones.sortedByDescending { bone ->
            (projected.getValue(bone.parent).depth + projected.getValue(bone.child).depth) / 2f
        }.forEach { bone ->
            val a = projected.getValue(bone.parent)
            val b = projected.getValue(bone.child)
            val direction = (project.joints.getValue(bone.child) - project.joints.getValue(bone.parent)).normalized()
            val facing = max(0f, direction.dot(light))
            val luminance = (0.28f + project.light.intensity * 0.32f + facing * 0.22f).coerceIn(0.25f, 0.82f)
            val color = Color(luminance, luminance, (luminance + 0.035f).coerceAtMost(1f))
            val depthScale = ((a.scale + b.scale) / 2f / 160f).coerceIn(0.7f, 2.2f)
            drawLine(
                color = color,
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = bone.width * depthScale,
                cap = StrokeCap.Round,
            )
        }

        JointId.entries.sortedByDescending { projected.getValue(it).depth }.forEach { id ->
            val p = projected.getValue(id)
            val chosen = id == selectedJoint
            val radius = (if (id == JointId.HEAD) 22f else if (chosen) 9f else 5.5f) * (p.scale / 160f).coerceIn(0.75f, 1.7f)
            if (chosen) drawCircle(Color(0x443F6FFF), radius * 1.9f, Offset(p.x, p.y))
            drawCircle(if (chosen) Color(0xFF315BDB) else Color(0xFF303034), radius, Offset(p.x, p.y))
        }
    }
}

private fun lightDirection(project: PoseProject): Vec3 {
    val az = project.light.azimuthDegrees * PI.toFloat() / 180f
    val el = project.light.elevationDegrees * PI.toFloat() / 180f
    return Vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az)).normalized()
}
