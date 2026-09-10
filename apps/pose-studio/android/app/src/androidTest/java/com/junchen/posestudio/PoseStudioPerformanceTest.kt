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
    fun renderModelSteadyStateFitsInteractiveBudget() {
        val project = PoseProject(joints = com.junchen.posestudio.model.Mannequin.preset(PosePreset.RUN))

        repeat(100) { PoseRenderBuilder.build(project, 1080f, 1600f) }
        val batchMeansMs = measureBatchMeans(batchCount = 7, operationsPerBatch = 100) {
            PoseRenderBuilder.build(project, 1080f, 1600f)
        }
        val medianMs = percentile(batchMeansMs, 0.50)
        val p90Ms = percentile(batchMeansMs, 0.90)

        assertTrue("render-model median regression: ${medianMs}ms/op", medianMs < 8.0)
        assertTrue("render-model p90 regression: ${p90Ms}ms/op", p90Ms < 16.0)
    }

    @Test
    fun projectCodecRoundTripFitsNonInteractiveBudget() {
        val project = PoseProject(joints = com.junchen.posestudio.model.Mannequin.preset(PosePreset.RUN))

        repeat(20) { ProjectCodec.decode(ProjectCodec.encode(project)) }
        val batchMeansMs = measureBatchMeans(batchCount = 7, operationsPerBatch = 20) {
            ProjectCodec.decode(ProjectCodec.encode(project))
        }
        val medianMs = percentile(batchMeansMs, 0.50)
        val p90Ms = percentile(batchMeansMs, 0.90)

        assertTrue("project-codec median regression: ${medianMs}ms/round-trip", medianMs < 50.0)
        assertTrue("project-codec p90 regression: ${p90Ms}ms/round-trip", p90Ms < 100.0)
    }

    @Test
    fun mediumPngExportFitsBroadDeviceBudget() {
        val project = PoseProject(joints = com.junchen.posestudio.model.Mannequin.preset(PosePreset.CONTRAPPOSTO))

        PoseBitmapRenderer.render(project, 360, 360).recycle()
        val samplesMs = buildList {
            repeat(5) {
                val start = SystemClock.elapsedRealtimeNanos()
                val bitmap = PoseBitmapRenderer.render(project, 720, 720)
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                assertTrue(bitmap.width == 720 && bitmap.height == 720)
                bitmap.recycle()
                add(elapsedMs)
            }
        }
        val medianMs = percentile(samplesMs, 0.50)
        val p90Ms = percentile(samplesMs, 0.90)

        assertTrue("720px bitmap median regression: ${medianMs}ms", medianMs < 1000.0)
        assertTrue("720px bitmap p90 regression: ${p90Ms}ms", p90Ms < 2000.0)
    }

    private fun measureBatchMeans(
        batchCount: Int,
        operationsPerBatch: Int,
        operation: () -> Unit,
    ): List<Double> = buildList {
        repeat(batchCount) {
            val start = SystemClock.elapsedRealtimeNanos()
            repeat(operationsPerBatch) { operation() }
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            add(elapsedMs / operationsPerBatch)
        }
    }

    private fun percentile(samples: List<Double>, percentile: Double): Double {
        val sorted = samples.sorted()
        val index = ((sorted.lastIndex) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
