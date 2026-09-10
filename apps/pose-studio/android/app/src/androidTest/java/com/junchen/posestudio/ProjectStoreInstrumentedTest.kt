package com.junchen.posestudio

import androidx.test.core.app.ApplicationProvider
import com.junchen.posestudio.data.ProjectCodec
import com.junchen.posestudio.data.ProjectStore
import com.junchen.posestudio.model.PoseProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import java.util.UUID

class ProjectStoreInstrumentedTest {
    @Test
    fun saveLoadDuplicateDeleteRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ProjectStore(context)
        val source = PoseProject(id = UUID.randomUUID().toString(), name = "Instrumented ${System.nanoTime()}")
        val saved = store.save(source)
        val loaded = store.load(saved.id)
        assertEquals(saved.name, loaded.name)

        val duplicate = store.duplicate(saved.id)
        assertNotEquals(saved.id, duplicate.id)
        assertEquals(saved.name + " Copy", duplicate.name)

        store.delete(saved.id)
        store.delete(duplicate.id)
        assertFalse(store.list().any { it.id == saved.id || it.id == duplicate.id })
    }

    @Test
    fun replacingExistingProjectKeepsLatestCommittedValue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ProjectStore(context)
        val id = UUID.randomUUID().toString()
        store.save(PoseProject(id = id, name = "First"))
        store.save(PoseProject(id = id, name = "Second"))
        assertEquals("Second", store.load(id).name)
        store.delete(id)
    }

    @Test
    fun interruptedAtomicWriteRecoversBackupAndDeleteCleansIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ProjectStore(context)
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "pose-projects").apply { mkdirs() }
        val base = File(directory, "$id.json")
        val backup = File(base.path + ".bak")
        val expected = PoseProject(id = id, name = "Last good pose")

        base.writeText("{broken", Charsets.UTF_8)
        backup.writeText(ProjectCodec.encode(expected), Charsets.UTF_8)

        assertEquals(expected.name, store.load(id).name)
        store.delete(id)
        assertFalse(base.exists())
        assertFalse(backup.exists())
    }
}
