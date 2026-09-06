package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PrivateFilePublisherTest {
    @Test
    fun publishesCompletedTemporaryWithoutCopying() {
        val root = Files.createTempDirectory("jingdu-publish").toFile()
        try {
            val source = File(root, "source.tmp").apply { writeText("new revision") }
            val target = File(root, "document.txt")
            PrivateFilePublisher.publishImmutable(source, target)
            assertFalse(source.exists())
            assertEquals("new revision", target.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun existingImmutableTargetWinsAndTemporaryIsDiscarded() {
        val root = Files.createTempDirectory("jingdu-publish-existing").toFile()
        try {
            val source = File(root, "source.tmp").apply { writeText("replacement") }
            val target = File(root, "document.txt").apply { writeText("last valid") }
            PrivateFilePublisher.publishImmutable(source, target)
            assertFalse(source.exists())
            assertEquals("last valid", target.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun publishFailureLeavesTemporaryAndPriorFilesystemStateIntact() {
        val root = Files.createTempDirectory("jingdu-publish-failure").toFile()
        try {
            val source = File(root, "source.tmp").apply { writeText("candidate") }
            val invalidParent = File(root, "not-a-directory").apply { writeText("sentinel") }
            val target = File(invalidParent, "document.txt")
            val failure = runCatching { PrivateFilePublisher.publishImmutable(source, target) }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertEquals("private publish failed", failure?.message)
            assertTrue(source.isFile)
            assertEquals("candidate", source.readText())
            assertEquals("sentinel", invalidParent.readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
