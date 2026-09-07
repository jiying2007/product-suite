package com.junchen.posestudio.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.junchen.posestudio.data.ProjectCodec
import com.junchen.posestudio.data.ProjectStore
import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.LightState
import com.junchen.posestudio.model.PoseMath
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import com.junchen.posestudio.render.PoseBitmapRenderer

class PoseStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val undo = ArrayDeque<Map<JointId, Vec3>>()
    private val redo = ArrayDeque<Map<JointId, Vec3>>()
    private var gestureSnapshot: Map<JointId, Vec3>? = null

    var project by mutableStateOf(PoseProject())
        private set
    var selectedJoint by mutableStateOf<JointId?>(JointId.RIGHT_WRIST)
        private set
    var dirty by mutableStateOf(false)
        private set
    var savedProjects by mutableStateOf(store.list())
        private set

    fun selectJoint(joint: JointId?) { selectedJoint = joint }

    fun beginPoseGesture() {
        if (gestureSnapshot == null) gestureSnapshot = project.joints.toMap()
    }

    fun dragJoint(joint: JointId, delta: Vec3) {
        val next = PoseMath.dragJoint(project.joints, joint, delta)
        if (!PoseMath.allFinite(next)) return
        project = project.copy(joints = next, modifiedAt = System.currentTimeMillis())
        dirty = true
    }

    fun endPoseGesture() {
        val before = gestureSnapshot ?: return
        gestureSnapshot = null
        if (before != project.joints) {
            undo.addLast(before)
            while (undo.size > 40) undo.removeFirst()
            redo.clear()
        }
    }

    fun applyPreset(preset: PosePreset) {
        applyPoseEdit { com.junchen.posestudio.model.Mannequin.preset(preset) }
    }

    fun mirrorPose() = applyPoseEdit(PoseMath::mirrorPose)
    fun copyLeftArmToRight() = applyPoseEdit { PoseMath.copyArm(it, fromLeft = true) }
    fun copyRightArmToLeft() = applyPoseEdit { PoseMath.copyArm(it, fromLeft = false) }
    fun copyLeftLegToRight() = applyPoseEdit { PoseMath.copyLeg(it, fromLeft = true) }
    fun copyRightLegToLeft() = applyPoseEdit { PoseMath.copyLeg(it, fromLeft = false) }
    fun groundFeet() = applyPoseEdit(PoseMath::groundFeet)

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(project.joints.toMap())
        project = project.copy(joints = previous, modifiedAt = System.currentTimeMillis())
        dirty = true
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(project.joints.toMap())
        project = project.copy(joints = next, modifiedAt = System.currentTimeMillis())
        dirty = true
    }

    fun updateCamera(transform: (CameraState) -> CameraState) {
        project = project.copy(camera = transform(project.camera), modifiedAt = System.currentTimeMillis())
        dirty = true
    }

    fun orbit(dx: Float, dy: Float) {
        updateCamera { camera ->
            camera.copy(
                yawDegrees = camera.yawDegrees + dx * 0.32f,
                pitchDegrees = (camera.pitchDegrees + dy * 0.25f).coerceIn(-65f, 65f),
            )
        }
    }

    fun updateLight(transform: (LightState) -> LightState) {
        project = project.copy(light = transform(project.light), modifiedAt = System.currentTimeMillis())
        dirty = true
    }

    fun rename(name: String) {
        project = project.copy(name = name.take(80), modifiedAt = System.currentTimeMillis())
        dirty = true
    }

    fun newProject() {
        project = PoseProject()
        selectedJoint = JointId.RIGHT_WRIST
        undo.clear(); redo.clear(); gestureSnapshot = null
        dirty = false
    }

    fun save() {
        project = store.save(project)
        dirty = false
        refreshSaved()
    }

    fun load(id: String) {
        project = store.load(id)
        selectedJoint = null
        undo.clear(); redo.clear(); gestureSnapshot = null
        dirty = false
        refreshSaved()
    }

    fun importJson(text: String) {
        val imported = ProjectCodec.decode(text).copy(id = java.util.UUID.randomUUID().toString(), modifiedAt = System.currentTimeMillis())
        project = imported
        selectedJoint = null
        undo.clear(); redo.clear(); gestureSnapshot = null
        dirty = true
    }

    fun exportJson(): String = ProjectCodec.encode(project)
    fun renderPng(width: Int = 1440, height: Int = 1440): Bitmap = PoseBitmapRenderer.render(project, width, height)

    private fun refreshSaved() { savedProjects = store.list() }

    private fun applyPoseEdit(transform: (Map<JointId, Vec3>) -> Map<JointId, Vec3>) {
        val before = project.joints
        val next = transform(before)
        if (next == before || !PoseMath.allFinite(next)) return
        pushUndo()
        project = project.copy(joints = next, modifiedAt = System.currentTimeMillis())
        dirty = true
    }

    private fun pushUndo() {
        undo.addLast(project.joints.toMap())
        while (undo.size > 40) undo.removeFirst()
        redo.clear()
    }
}
