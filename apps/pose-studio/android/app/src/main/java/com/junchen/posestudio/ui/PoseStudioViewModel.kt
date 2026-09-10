package com.junchen.posestudio.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.junchen.posestudio.data.ProjectCodec
import com.junchen.posestudio.data.ProjectStore
import com.junchen.posestudio.engine.PoseMath
import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.LightState
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import com.junchen.posestudio.render.PoseBitmapRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class PoseStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val undo = ArrayDeque<Map<JointId, Vec3>>()
    private val redo = ArrayDeque<Map<JointId, Vec3>>()
    private var gestureSnapshot: Map<JointId, Vec3>? = null
    private var recoveryJob: Job? = null

    var project by mutableStateOf(PoseProject())
        private set
    var selectedJoint by mutableStateOf<JointId?>(JointId.RIGHT_WRIST)
        private set
    var dirty by mutableStateOf(false)
        private set
    var savedProjects by mutableStateOf(store.list())
        private set
    var corruptProjects by mutableStateOf(store.listCorrupt())
        private set
    var recoveryCandidate by mutableStateOf(store.latestRecovery())
        private set

    fun selectJoint(joint: JointId?) { selectedJoint = joint }

    fun beginPoseGesture() {
        if (gestureSnapshot == null) gestureSnapshot = project.joints.toMap()
    }

    fun dragJoint(joint: JointId, delta: Vec3) {
        val next = PoseMath.dragJoint(project.joints, joint, delta)
        if (!PoseMath.allFinite(next)) return
        updateProject(project.copy(joints = next, modifiedAt = System.currentTimeMillis()))
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

    fun updateSelectedRoll(deltaDegrees: Float) {
        val joint = selectedJoint ?: return
        val next = project.jointRollDegrees.toMutableMap().apply {
            this[joint] = ((get(joint) ?: 0f) + deltaDegrees).coerceIn(-180f, 180f)
        }
        updateProject(project.copy(jointRollDegrees = next, modifiedAt = System.currentTimeMillis()))
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(project.joints.toMap())
        updateProject(project.copy(joints = previous, modifiedAt = System.currentTimeMillis()))
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(project.joints.toMap())
        updateProject(project.copy(joints = next, modifiedAt = System.currentTimeMillis()))
    }

    fun updateCamera(transform: (CameraState) -> CameraState) {
        updateProject(project.copy(camera = transform(project.camera), modifiedAt = System.currentTimeMillis()))
    }

    fun orbit(dx: Float, dy: Float) {
        updateCamera { camera ->
            camera.copy(
                yawDegrees = camera.yawDegrees + dx * 0.32f,
                pitchDegrees = (camera.pitchDegrees + dy * 0.25f).coerceIn(-65f, 65f),
            )
        }
    }

    fun zoom(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        updateCamera { camera -> camera.copy(distance = (camera.distance / factor).coerceIn(3.8f, 12f)) }
    }

    fun updateLight(transform: (LightState) -> LightState) {
        updateProject(project.copy(light = transform(project.light), modifiedAt = System.currentTimeMillis()))
    }

    fun rename(name: String) {
        updateProject(project.copy(name = name.take(80), modifiedAt = System.currentTimeMillis()))
    }

    fun discardUnsavedRecovery() {
        recoveryJob?.cancel()
        store.discardRecovery(project.id)
    }

    fun newProject() {
        recoveryJob?.cancel()
        project = PoseProject()
        selectedJoint = JointId.RIGHT_WRIST
        undo.clear(); redo.clear(); gestureSnapshot = null
        dirty = false
    }

    fun save() {
        recoveryJob?.cancel()
        project = store.save(project)
        dirty = false
        refreshSaved()
    }

    fun load(id: String) {
        recoveryJob?.cancel()
        project = store.load(id)
        selectedJoint = null
        undo.clear(); redo.clear(); gestureSnapshot = null
        dirty = false
        refreshSaved()
    }

    fun duplicate(id: String) {
        store.duplicate(id)
        refreshSaved()
    }

    fun delete(id: String) {
        if (store.delete(id)) refreshSaved()
    }

    fun restoreRecovery() {
        val recovered = recoveryCandidate ?: return
        project = recovered.copy(schemaVersion = PoseProject.CURRENT_SCHEMA_VERSION)
        selectedJoint = null
        undo.clear(); redo.clear(); gestureSnapshot = null
        dirty = true
        recoveryCandidate = null
        scheduleRecovery()
    }

    fun dismissRecovery() {
        recoveryCandidate?.let { store.discardRecovery(it.id) }
        recoveryCandidate = null
    }

    fun importJson(text: String) {
        val imported = ProjectCodec.decode(text).copy(
            schemaVersion = PoseProject.CURRENT_SCHEMA_VERSION,
            id = UUID.randomUUID().toString(),
            modifiedAt = System.currentTimeMillis(),
        )
        project = imported
        selectedJoint = null
        undo.clear(); redo.clear(); gestureSnapshot = null
        dirty = true
        scheduleRecovery()
    }

    fun exportJson(): String = ProjectCodec.encode(project)
    fun renderPng(width: Int = 1440, height: Int = 1440): Bitmap = PoseBitmapRenderer.render(project, width, height)

    private fun refreshSaved() {
        savedProjects = store.list()
        corruptProjects = store.listCorrupt()
    }

    private fun applyPoseEdit(transform: (Map<JointId, Vec3>) -> Map<JointId, Vec3>) {
        val before = project.joints
        val next = transform(before)
        if (next == before || !PoseMath.allFinite(next)) return
        pushUndo()
        updateProject(project.copy(joints = next, modifiedAt = System.currentTimeMillis()))
    }

    private fun updateProject(next: PoseProject) {
        project = next
        dirty = true
        scheduleRecovery()
    }

    private fun scheduleRecovery() {
        recoveryJob?.cancel()
        val snapshot = project
        recoveryJob = viewModelScope.launch(Dispatchers.IO) {
            delay(650)
            runCatching { store.saveRecovery(snapshot) }
        }
    }

    private fun pushUndo() {
        undo.addLast(project.joints.toMap())
        while (undo.size > 40) undo.removeFirst()
        redo.clear()
    }
}
