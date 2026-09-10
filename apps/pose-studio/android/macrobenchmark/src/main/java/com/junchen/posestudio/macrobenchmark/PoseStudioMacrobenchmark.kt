package com.junchen.posestudio.macrobenchmark

import android.graphics.Rect
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseStudioMacrobenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = { pressHome() },
            measureBlock = {
                startActivityAndWait()
                sceneBounds()
            },
        )
    }

    @Test
    fun directManipulationFrames() {
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = 8,
            setupBlock = {
                device.executeShellCommand("pm clear $PACKAGE")
                startActivityAndWait()
                sceneBounds()
            },
            measureBlock = {
                val bounds = sceneBounds()
                val startX = bounds.left + (bounds.width() * 0.70f).roundToInt()
                val startY = bounds.top + (bounds.height() * 0.32f).roundToInt()
                val endX = bounds.left + (bounds.width() * 0.79f).roundToInt()
                val endY = bounds.top + (bounds.height() * 0.40f).roundToInt()
                check(device.swipe(startX, startY, endX, endY, 60)) {
                    "UiAutomator could not perform the direct-manipulation gesture"
                }
                device.waitForIdle()
            },
        )
    }

    private fun MacrobenchmarkScope.sceneBounds(): Rect {
        check(device.wait(Until.hasObject(By.descContains("Pose canvas")), 5_000)) {
            "Pose canvas did not become accessible"
        }
        return checkNotNull(device.findObject(By.descContains("Pose canvas"))).visibleBounds
    }

    private companion object {
        const val PACKAGE = "com.junchen.posestudio"
    }
}
