package com.junchen.jingdu

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Publishes a fully-written private artifact without ever replacing an existing immutable target.
 * On failure the source temporary remains available to the caller for cleanup and the prior target
 * is untouched.
 */
internal object PrivateFilePublisher {
    @Throws(IOException::class)
    fun publishImmutable(source: File, target: File) {
        if (target.isFile) {
            deleteTemporary(source)
            return
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            try {
                Files.move(source.toPath(), target.toPath())
            } catch (_: FileAlreadyExistsException) {
                deleteTemporary(source)
            } catch (error: IOException) {
                throw IOException("private publish failed", error)
            }
        } catch (_: FileAlreadyExistsException) {
            deleteTemporary(source)
        } catch (error: IOException) {
            throw IOException("private publish failed", error)
        }
    }

    private fun deleteTemporary(file: File) {
        if (file.exists() && !file.delete()) file.deleteOnExit()
    }
}
