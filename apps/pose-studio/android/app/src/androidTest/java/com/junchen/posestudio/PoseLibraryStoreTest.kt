package com.junchen.posestudio

import androidx.test.core.app.ApplicationProvider
import com.junchen.posestudio.data.PoseLibraryStore
import com.junchen.posestudio.model.JointId
import com.junchen.posestudio.model.Mannequin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PoseLibraryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directory get() = File(context.filesDir, "pose-library")

    @Before
    fun clearBefore() {
        directory.deleteRecursively()
    }

    @After
    fun clearAfter() {
        directory.deleteRecursively()
    }

    @Test
    fun savedPoseRoundTripsJointsAndRollAndCanBeDeleted() {
        val store = PoseLibraryStore(context)
        val joints = Mannequin.neutral().toMutableMap().apply {
            this[JointId.RIGHT_WRIST] = getValue(JointId.RIGHT_WRIST).copy(z = 0.42f)
        }
        val rolls = mapOf(JointId.RIGHT_WRIST to 45f)

        val saved = store.save("Foreshortened arm", joints, rolls)
        val listed = store.list()
        val loaded = store.load(saved.id)

        assertEquals(1, listed.size)
        assertEquals(saved.id, listed.single().id)
        assertEquals(joints.getValue(JointId.RIGHT_WRIST), loaded.joints.getValue(JointId.RIGHT_WRIST))
        assertEquals(45f, loaded.jointRollDegrees.getValue(JointId.RIGHT_WRIST), 0.001f)
        assertTrue(store.delete(saved.id))
        assertTrue(store.list().isEmpty())
    }
}
