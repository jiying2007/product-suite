package com.junchen.posestudio.data

import android.content.Context
import com.junchen.posestudio.model.CameraState
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.LightState
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.model.Vec3
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectStore(context: Context) {
    private val directory = File(context.filesDir, "pose-projects").apply { mkdirs() }

    data class SavedProject(val id: String, val name: String, val modifiedAt: Long)

    fun save(project: PoseProject): PoseProject {
        val saved = project.copy(modifiedAt = System.currentTimeMillis())
        val destination = fileFor(saved.id)
        val temp = File(directory, ".${saved.id}.tmp")
        temp.writeText(ProjectCodec.encode(saved), Charsets.UTF_8)
        check(temp.renameTo(destination) || run {
            destination.delete()
            temp.renameTo(destination)
        }) { "Could not atomically save pose project" }
        return saved
    }

    fun load(id: String): PoseProject = ProjectCodec.decode(fileFor(id).readText(Charsets.UTF_8))

    fun list(): List<SavedProject> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == "json" }
        .mapNotNull { file ->
            runCatching { ProjectCodec.decode(file.readText(Charsets.UTF_8)) }
                .getOrNull()
                ?.let { SavedProject(it.id, it.name, it.modifiedAt) }
        }
        .sortedByDescending { it.modifiedAt }
        .toList()

    private fun fileFor(id: String): File = File(directory, "${id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
}

object ProjectCodec {
    fun encode(project: PoseProject): String {
        val joints = JSONObject()
        JointId.entries.forEach { id ->
            val value = project.joints[id] ?: Mannequin.neutral().getValue(id)
            joints.put(id.name, JSONArray(listOf(value.x, value.y, value.z)))
        }
        return JSONObject()
            .put("schemaVersion", project.schemaVersion)
            .put("id", project.id)
            .put("name", project.name)
            .put("modifiedAt", project.modifiedAt)
            .put("camera", JSONObject()
                .put("yaw", project.camera.yawDegrees)
                .put("pitch", project.camera.pitchDegrees)
                .put("distance", project.camera.distance)
                .put("fov", project.camera.fovDegrees))
            .put("light", JSONObject()
                .put("azimuth", project.light.azimuthDegrees)
                .put("elevation", project.light.elevationDegrees)
                .put("intensity", project.light.intensity))
            .put("joints", joints)
            .toString(2)
    }

    fun decode(text: String): PoseProject {
        val json = JSONObject(text)
        val schema = json.optInt("schemaVersion", 1)
        require(schema in 1..1) { "Unsupported project schema: $schema" }
        val defaults = Mannequin.neutral()
        val jsonJoints = json.optJSONObject("joints") ?: JSONObject()
        val joints = JointId.entries.associateWith { id ->
            val array = jsonJoints.optJSONArray(id.name)
            if (array == null || array.length() < 3) defaults.getValue(id) else Vec3(
                array.optDouble(0, defaults.getValue(id).x.toDouble()).toFloat(),
                array.optDouble(1, defaults.getValue(id).y.toDouble()).toFloat(),
                array.optDouble(2, defaults.getValue(id).z.toDouble()).toFloat(),
            )
        }
        val cameraJson = json.optJSONObject("camera") ?: JSONObject()
        val lightJson = json.optJSONObject("light") ?: JSONObject()
        return PoseProject(
            schemaVersion = schema,
            id = json.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
            name = json.optString("name", "Imported Pose"),
            modifiedAt = json.optLong("modifiedAt", System.currentTimeMillis()),
            joints = joints,
            camera = CameraState(
                yawDegrees = cameraJson.optDouble("yaw", 18.0).toFloat(),
                pitchDegrees = cameraJson.optDouble("pitch", -4.0).toFloat(),
                distance = cameraJson.optDouble("distance", 7.2).toFloat().coerceIn(3.8f, 12f),
                fovDegrees = cameraJson.optDouble("fov", 38.0).toFloat().coerceIn(20f, 75f),
            ),
            light = LightState(
                azimuthDegrees = lightJson.optDouble("azimuth", -35.0).toFloat(),
                elevationDegrees = lightJson.optDouble("elevation", 48.0).toFloat(),
                intensity = lightJson.optDouble("intensity", 0.82).toFloat().coerceIn(0.15f, 1f),
            ),
        )
    }
}
