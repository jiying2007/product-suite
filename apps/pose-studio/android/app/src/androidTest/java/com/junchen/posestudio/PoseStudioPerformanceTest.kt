package com.junchen.posestudio

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.junchen.posestudio.data.ProjectCodec
import com.junchen.posestudio.model.PosePreset
import com.junchen.posestudio.model.PoseProject
import com.junchen.posestudio.render.PoseBitmapRenderer
import com.junchen.posestudio.render.PoseRenderBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseStudioPerformanceTest {
    @Test
    fun renderModelAndProjectCodecStayInteractiveScale() {
        val project = PoseProject(joints = com.junchen.posestudio.model.Mannequin.preset(PosePreset.RUN))
        repeat(20) { PoseRenderBuilder.build(project, 1080f, 1600f) }
        val start = SystemClock.elapsedRealtimeNanos()
        repeat(400) {
            PoseRenderBuilder.build(project, 1080f, 1600f)
            ProjectCodec.decode(ProjectCodec.encode(project))
        }
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        assertTrue("render-model + codec regression: ${elapsedMs}ms", elapsedMs < 3500.0)
    }

    @Test
    fun mediumPngExportFitsBroadDeviceBudget() {
        val project = PoseProject(joints = com.junchen.posestudio.model.Mannequin.preset(PosePreset.CONTRAPPOSTO))
        val start = SystemClock.elapsedRealtimeNanos()
        val bitmap = PoseBitmapRenderer.render(project, 720, 720)
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        assertTrue(bitmap.width == 720 && bitmap.height == 720)
        assertTrue("720px PNG render regression: ${elapsedMs}ms", elapsedMs < 2000.0)
        bitmap.recycle()
    }
}
