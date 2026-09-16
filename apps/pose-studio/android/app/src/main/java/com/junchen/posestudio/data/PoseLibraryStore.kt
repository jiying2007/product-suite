package com.junchen.posestudio.data

import android.content.Context
import android.util.AtomicFile
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import com.junchen.posestudio.model.Vec3
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.UUID

class PoseLibraryStore(context: Context) {
    private val directory = File(context.filesDir, "pose-library").apply { mkdirs() }

    data class SavedPoseInfo(val id: String, val name: String, val modifiedAt: Long)
    data class SavedPose(
        val id: String,
        val name: String,
        val modifiedAt: Long,
        val joints: Map<JointId, Vec3>,
        val jointRollDegrees: Map<JointId, Float>,
    )

    fun save(
        name: String,
        joints: Map<JointId, Vec3>,
        jointRollDegrees: Map<JointId, Float>,
    ): SavedPose {
        require(JointId.entries.all { joints[it]?.isFinite() == true }) { "Pose contains invalid joints" }
        val saved = SavedPose(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(80).ifBlank { "Saved Pose" },
            modifiedAt = System.currentTimeMillis(),
            joints = JointId.entries.associateWith { joints.getValue(it) },
            jointRollDegrees = JointId.entries.associateWith {
                (jointRollDegrees[it] ?: 0f).takeIf(Float::isFinite)?.coerceIn(-180f, 180f) ?: 0f
            },
        )
        writeAtomically(fileFor(saved.id), encode(saved))
        return saved
    }

    fun load(id: String): SavedPose = decode(readAtomically(fileFor(id)))

    fun list(): List<SavedPoseInfo> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == "json" }
        .mapNotNull { file ->
            runCatching { decode(readAtomically(file)) }
                .getOrNull()
                ?.let { SavedPoseInfo(it.id, it.name, it.modifiedAt) }
        }
        .sortedByDescending { it.modifiedAt }
        .toList()

    fun delete(id: String): Boolean {
        val file = fileFor(id)
        val existed = file.exists() || File(file.path + ".bak").exists()
        AtomicFile(file).delete()
        return !existed || (!file.exists() && !File(file.path + ".bak").exists())
    }

    private fun encode(pose: SavedPose): String {
        val jointsJson = JSONObject()
        val rollsJson = JSONObject()
        JointId.entries.forEach { id ->
            val value = pose.joints.getValue(id)
            jointsJson.put(id.name, JSONArray(listOf(value.x, value.y, value.z)))
            rollsJson.put(id.name, pose.jointRollDegrees[id] ?: 0f)
        }
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("id", pose.id)
            .put("name", pose.name)
            .put("modifiedAt", pose.modifiedAt)
            .put("joints", jointsJson)
            .put("jointRollDegrees", rollsJson)
            .toString(2)
    }

    private fun decode(text: String): SavedPose {
        require(text.length <= MAX_TEXT_CHARS) { "Saved pose is too large" }
        val json = JSONObject(text)
        require(json.optInt("schemaVersion", 0) == SCHEMA_VERSION) { "Unsupported saved pose schema" }
        val defaults = Mannequin.neutral()
        val jointsJson = json.optJSONObject("joints") ?: error("Saved pose joints missing")
        val joints = JointId.entries.associateWith { id ->
            readVec(jointsJson.optJSONArray(id.name), defaults.getValue(id))
        }
        val rollsJson = json.optJSONObject("jointRollDegrees") ?: JSONObject()
        val rolls = JointId.entries.associateWith { id ->
            val value = rollsJson.optDouble(id.name, 0.0).toFloat()
            if (value.isFinite()) value.coerceIn(-180f, 180f) else 0f
        }
        return SavedPose(
            id = json.optString("id").takeIf { it.isNotBlank() } ?: error("Saved pose id missing"),
            name = json.optString("name", "Saved Pose").take(80).ifBlank { "Saved Pose" },
            modifiedAt = json.optLong("modifiedAt", 0L).coerceAtLeast(0L),
            joints = joints,
            jointRollDegrees = rolls,
        )
    }

    private fun readVec(array: JSONArray?, fallback: Vec3): Vec3 {
        if (array == null || array.length() < 3) return fallback
        val candidate = Vec3(
            array.optDouble(0, fallback.x.toDouble()).toFloat(),
            array.optDouble(1, fallback.y.toDouble()).toFloat(),
            array.optDouble(2, fallback.z.toDouble()).toFloat(),
        )
        return if (
            candidate.isFinite() &&
            kotlin.math.abs(candidate.x) <= MAX_ABS_COORDINATE &&
            kotlin.math.abs(candidate.y) <= MAX_ABS_COORDINATE &&
            kotlin.math.abs(candidate.z) <= MAX_ABS_COORDINATE
        ) candidate else fallback
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
    private fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_TEXT_CHARS = 256_000
        const val MAX_ABS_COORDINATE = 100f
    }
}
