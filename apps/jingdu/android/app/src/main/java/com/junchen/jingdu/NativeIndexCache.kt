package com.junchen.jingdu

import java.io.File

/** Native index hygiene. `.jdx` exists only while its source document exists. */
internal object NativeIndexCache {
    fun pruneOrphans(directory: File?) {
        directory?.listFiles()?.forEach { file ->
            val name = file.name
            when {
                name.endsWith(".jdx.tmp") -> delete(file)
                name.endsWith(".jdx") -> {
                    val sourceName = name.removeSuffix(".jdx")
                    if (!File(directory, sourceName).isFile) delete(file)
                }
            }
        }
    }

    private fun delete(file: File) {
        if (file.exists() && !file.delete()) file.deleteOnExit()
    }
}
