package com.junchen.posestudio.data

import android.content.Context
import android.util.AtomicFile
import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.LightState
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.UUID

class ProjectStore(context: Context) {
    private val directory = File(context.filesDir, "pose-projects").apply { mkdirs() }
    private val recoveryDirectory = File(context.filesDir, "pose-recovery").apply { mkdirs() }

    data class SavedProject(val id: String, val name: String, val modifiedAt: Long)
    data class CorruptProject(val fileName: String, val modifiedAt: Long)
    data class ProjectIndex(val saved: List<SavedProject>, val corrupt: List<CorruptProject>)

    fun save(project: PoseProject): PoseProject {
        val saved = project.copy(schemaVersion = PoseProject.CURRENT_SCHEMA_VERSION, modifiedAt = System.currentTimeMillis())
        writeAtomically(fileFor(saved.id), ProjectCodec.encode(saved))
        discardRecovery(saved.id)
        return saved
    }

    fun saveRecovery(project: PoseProject) {
        val snapshot = project.copy(schemaVersion = PoseProject.CURRENT_SCHEMA_VERSION, modifiedAt = System.currentTimeMillis())
        writeAtomically(recoveryFileFor(snapshot.id), ProjectCodec.encode(snapshot))
    }

    fun load(id: String): PoseProject = ProjectCodec.decode(readAtomically(fileFor(id)))

    fun latestRecovery(): PoseProject? = recoveryDirectory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == "json" }
        .sortedByDescending { it.lastModified() }
        .mapNotNull { file -> runCatching { ProjectCodec.decode(readAtomically(file)) }.getOrNull() }
        .firstOrNull { recovered ->
            val saved = fileFor(recovered.id)
            !saved.exists() || recovered.modifiedAt > saved.lastModified()
        }

    fun discardRecovery(id: String) {
        AtomicFile(recoveryFileFor(id)).delete()
    }

    fun scan(): ProjectIndex {
        val saved = mutableListOf<SavedProject>()
        val corrupt = mutableListOf<CorruptProject>()
        directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                runCatching { ProjectCodec.decode(readAtomically(file)) }
                    .onSuccess { project -> saved += SavedProject(project.id, project.name, project.modifiedAt) }
                    .onFailure { corrupt += CorruptProject(file.name, file.lastModified()) }
            }
        return ProjectIndex(
            saved = saved.sortedByDescending { it.modifiedAt },
            corrupt = corrupt.sortedByDescending { it.modifiedAt },
        )
    }

    fun list(): List<SavedProject> = scan().saved
    fun listCorrupt(): List<CorruptProject> = scan().corrupt

    fun duplicate(id: String): PoseProject {
        val source = load(id)
        return save(
            source.copy(
                id = UUID.randomUUID().toString(),
                name = source.name.take(68) + " Copy",
                modifiedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun delete(id: String): Boolean {
        discardRecovery(id)
        val file = fileFor(id)
        val existed = file.exists() || File(file.path + ".bak").exists()
        AtomicFile(file).delete()
        return !existed || (!file.exists() && !File(file.path + ".bak").exists())
    }

    private fun readAtomically(source: File): String =
        AtomicFile(source).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun writeAtomically(destination: File, text: String) {
        val atomicFile = AtomicFile(destination)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            OutputStreamWriter(output, Charsets.UTF_8).apply {
                write(text)
                flush()
            }
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            output?.let { atomicFile.failWrite(it) }
            throw error
        }
    }

    private fun fileFor(id: String): File = File(directory, "${safeId(id)}.json")
    private fun recoveryFileFor(id: String): File = File(recoveryDirectory, "${safeId(id)}.json")
    private fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

object ProjectCodec {
    private const val MAX_PROJECT_TEXT_CHARS = 2_000_000
    private const val MAX_ABS_COORDINATE = 100f

    fun encode(project: PoseProject): String {
        val joints = JSONObject()
        JointId.entries.forEach { id ->
            val value = project.joints[id] ?: Mannequin.neutral().getValue(id)
            joints.put(id.name, JSONArray(listOf(value.x, value.y, value.z)))
        }
        val rolls = JSONObject()
        JointId.entries.forEach { id -> rolls.put(id.name, project.jointRollDegrees[id] ?: 0f) }
        return JSONObject()
            .put("schemaVersion", PoseProject.CURRENT_SCHEMA_VERSION)
            .put("id", project.id)
            .put("name", project.name)
            .put("modifiedAt", project.modifiedAt)
            .put("camera", JSONObject()
                .put("yaw", project.camera.yawDegrees)
                .put("pitch", project.camera.pitchDegrees)
                .put("distance", project.camera.distance)
                .put("fov", project.camera.fovDegrees)
                .put("target", JSONArray(listOf(project.camera.target.x, project.camera.target.y, project.camera.target.z))))
            .put("light", JSONObject()
                .put("azimuth", project.light.azimuthDegrees)
                .put("elevation", project.light.elevationDegrees)
                .put("intensity", project.light.intensity))
            .put("joints", joints)
            .put("jointRollDegrees", rolls)
            .toString(2)
    }

    fun decode(text: String): PoseProject {
        require(text.length <= MAX_PROJECT_TEXT_CHARS) { "Project is too large" }
        val json = JSONObject(text)
        val schema = json.optInt("schemaVersion", 1)
        require(schema in 1..PoseProject.CURRENT_SCHEMA_VERSION) { "Unsupported project schema: $schema" }
        val defaults = Mannequin.neutral()
        val jsonJoints = json.optJSONObject("joints") ?: JSONObject()
        val joints = JointId.entries.associateWith { id ->
            val array = jsonJoints.optJSONArray(id.name)
            readVec(array, defaults.getValue(id))
        }
        val rollsJson = json.optJSONObject("jointRollDegrees") ?: JSONObject()
        val rolls = JointId.entries.associateWith { id ->
            val value = rollsJson.optDouble(id.name, 0.0).toFloat()
            if (value.isFinite()) value.coerceIn(-180f, 180f) else 0f
        }
        val cameraJson = json.optJSONObject("camera") ?: JSONObject()
        val lightJson = json.optJSONObject("light") ?: JSONObject()
        return PoseProject(
            schemaVersion = PoseProject.CURRENT_SCHEMA_VERSION,
            id = json.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = json.optString("name", "Imported Pose").take(80),
            modifiedAt = json.optLong("modifiedAt", System.currentTimeMillis()),
            joints = joints,
            jointRollDegrees = rolls,
            camera = CameraState(
                yawDegrees = finiteOr(cameraJson.optDouble("yaw", 18.0).toFloat(), 18f).coerceIn(-720f, 720f),
                pitchDegrees = finiteOr(cameraJson.optDouble("pitch", -4.0).toFloat(), -4f).coerceIn(-65f, 65f),
                distance = finiteOr(cameraJson.optDouble("distance", 7.2).toFloat(), 7.2f).coerceIn(3.8f, 12f),
                fovDegrees = finiteOr(cameraJson.optDouble("fov", 38.0).toFloat(), 38f).coerceIn(20f, 75f),
                target = readVec(cameraJson.optJSONArray("target"), Vec3.ZERO),
            ),
            light = LightState(
                azimuthDegrees = finiteOr(lightJson.optDouble("azimuth", -35.0).toFloat(), -35f).coerceIn(-720f, 720f),
                elevationDegrees = finiteOr(lightJson.optDouble("elevation", 48.0).toFloat(), 48f).coerceIn(-20f, 85f),
                intensity = finiteOr(lightJson.optDouble("intensity", 0.82).toFloat(), 0.82f).coerceIn(0.15f, 1f),
            ),
        )
    }

    private fun readVec(array: JSONArray?, fallback: Vec3): Vec3 {
        if (array == null || array.length() < 3) return fallback
        val candidate = Vec3(
            array.optDouble(0, fallback.x.toDouble()).toFloat(),
            array.optDouble(1, fallback.y.toDouble()).toFloat(),
            array.optDouble(2, fallback.z.toDouble()).toFloat(),
        )
        return if (candidate.isFinite() &&
            kotlin.math.abs(candidate.x) <= MAX_ABS_COORDINATE &&
            kotlin.math.abs(candidate.y) <= MAX_ABS_COORDINATE &&
            kotlin.math.abs(candidate.z) <= MAX_ABS_COORDINATE
        ) candidate else fallback
    }

    private fun finiteOr(value: Float, fallback: Float): Float = if (value.isFinite()) value else fallback
}
