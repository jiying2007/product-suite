package com.junchen.jingdu.macrobenchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    /** Startup Profile contains only the first-display path; runtime CUJs belong in Baseline only. */
    @Test fun readerStartup() {
        prepareProfileTarget("paged")
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 10,
            stableIterations = 3,
            includeInStartupProfile = true,
            outputFilePrefix = "jingdu-reader-startup",
        ) {
            pressHome()
            startActivityAndWait()
            openFixture()
        }
    }

    /** Page turn, continuous scroll and panel navigation are runtime-critical Baseline Profile CUJs. */
    @Test fun readerCriticalJourneys() {
        prepareProfileTarget("paged")
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 10,
            stableIterations = 3,
            includeInStartupProfile = false,
            outputFilePrefix = "jingdu-reader-critical",
        ) {
            pressHome()
            startActivityAndWait()
            openFixture()

            // Hosted API 35 software-emulator policy consumes injected hardware-volume keys before
            // MainActivity. Profile the same real reader tap-zone path used by the hosted frame gate;
            // physical-device release evidence owns hardware-volume delivery validation.
            repeat(10) {
                check(device.click(
                    (device.displayWidth * PAGE_FORWARD_TAP_X).toInt(),
                    (device.displayHeight * PAGE_FORWARD_TAP_Y).toInt(),
                )) { "Reader baseline forward page tap was not injected" }
                device.waitForIdle()
            }

            // Runtime profile CUJs are independent user journeys. Restart a fresh paged reader before
            // each panel path so page-turn/control-auto-hide state from one CUJ cannot contaminate the
            // next while still exercising the real launch, library-card tap, Reader and panel UI.
            restartProfileReader("paged")
            ensureTopControlsVisible()
            requireReadingSettingsClick()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            restartProfileReader("paged")
            ensureTopControlsVisible()
            requireChaptersClick()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            // Continuous is likewise an independent runtime CUJ rather than a stateful continuation
            // of the paged/panel path above. Keep the explicit mode call as a static contract marker.
            setProfileMode("continuous")
            startActivityAndWait()
            openFixture()
            device.waitForIdle()
            SystemClock.sleep(500)

            repeat(4) {
                check(device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.80).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.25).toInt(),
                    24,
                )) { "Reader baseline continuous-scroll swipe was not injected" }
            }
            device.waitForIdle()
        }
    }

    /**
     * Provision fixture state before BaselineProfileRule enters its compilation/reset loop. Provider
     * startup is test infrastructure, not part of the product CUJ being profiled, and launching it
     * from inside CompilationMode.Partial made provider publication race the profile compiler on CI.
     */
    private fun prepareProfileTarget(mode: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("logcat -c")
        val seed = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 10",
        )
        check(seed.contains("bytes=")) {
            "Reader baseline fixture seed failed before profile collection: $seed\n===== Reader profile target diagnostics =====\n${profileDiagnostics(device)}"
        }
        setProfileMode(device, mode)
    }

    private fun setProfileMode(device: UiDevice, mode: String) {
        val modeResult = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(modeResult.contains("Result: Bundle[{}]")) {
            "Reader baseline mode setup failed before profile collection: $modeResult\n===== Reader profile target diagnostics =====\n${profileDiagnostics(device)}"
        }
        // BaselineProfileRule owns product-process startup. Leave a fully provisioned but stopped
        // package so compilation/profile collection begins from a deterministic lifecycle boundary.
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
    }

    private fun MacrobenchmarkScope.setProfileMode(mode: String) {
        setProfileMode(device, mode)
    }

    private fun MacrobenchmarkScope.restartProfileReader(mode: String) {
        setProfileMode(mode)
        startActivityAndWait()
        openFixture()
        device.waitForIdle()
    }

    private fun profileDiagnostics(device: UiDevice): String = buildString {
        append("package-path:\n")
        append(device.executeShellCommand("pm path $PACKAGE_NAME").trim().ifEmpty { "<not-installed>" })
        append("\nprovider:\n")
        append(device.executeShellCommand("dumpsys package $PACKAGE_NAME | grep -A 12 -B 3 benchmarkfixture").takeLast(8_000))
        append("\npidof: ")
        append(device.executeShellCommand("pidof $PACKAGE_NAME").trim().ifEmpty { "<not-running>" })
        append("\nexit-info:\n")
        append(device.executeShellCommand("dumpsys activity exit-info $PACKAGE_NAME").takeLast(12_000))
        append("\nlogcat:\n")
        append(
            device.executeShellCommand(
                "logcat -d -t 300 AndroidRuntime:E ActivityManager:I ActivityTaskManager:I '*:S'",
            ).takeLast(20_000),
        )
    }

    private fun MacrobenchmarkScope.openFixture() {
        val title = "Benchmark Novel 10 MiB"
        check(device.wait(Until.hasObject(By.textContains(title)), 8_000)) { "Reader baseline fixture missing" }
        val target = device.findObject(By.textContains(title)) ?: error("Reader baseline fixture unavailable")
        val bounds = runCatching { target.visibleBounds }.getOrNull() ?: error("Reader baseline fixture became stale")
        check(device.click(bounds.centerX(), bounds.centerY())) { "Reader baseline fixture tap was not injected" }
        device.waitForIdle()
    }

    /** Accessibility nodes are live handles; only immutable on-screen rectangles cross helper calls. */
    private fun MacrobenchmarkScope.visibleBounds(selector: BySelector): android.graphics.Rect? =
        device.findObjects(selector)
            .asSequence()
            .mapNotNull { node -> runCatching { node.visibleBounds }.getOrNull() }
            .firstOrNull { bounds ->
                bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.right > 0 && bounds.bottom > 0 &&
                    bounds.left < device.displayWidth && bounds.top < device.displayHeight
            }

    private fun MacrobenchmarkScope.readingSettingsBounds(): android.graphics.Rect? =
        visibleBounds(By.desc("Reading settings")) ?: visibleBounds(By.text("Aa"))

    private fun MacrobenchmarkScope.ensureTopControlsVisible() {
        if (readingSettingsBounds() != null) return
        val taps = listOf(0.50f to 0.52f, 0.50f to 0.68f, 0.50f to 0.36f)
        repeat(2) {
            for ((x, y) in taps) {
                check(device.click((device.displayWidth * x).toInt(), (device.displayHeight * y).toInt())) {
                    "Reader baseline top-control tap was not injected"
                }
                if (device.wait(Until.hasObject(By.desc("Reading settings")), 900) && readingSettingsBounds() != null) return
            }
        }
        error("Reader baseline top reading controls did not become visibly on-screen")
    }

    private fun MacrobenchmarkScope.requireReadingSettingsClick() {
        val bounds = readingSettingsBounds() ?: error("Reader baseline quick reading settings control missing")
        check(device.click(bounds.centerX(), bounds.centerY())) { "Reader baseline quick reading settings tap was not injected" }
    }

    private fun MacrobenchmarkScope.requireClick(selector: BySelector, label: String) {
        check(device.wait(Until.hasObject(selector), 3_000)) { "Reader baseline $label missing" }
        val bounds = visibleBounds(selector) ?: error("Reader baseline $label exists only off-screen")
        check(device.click(bounds.centerX(), bounds.centerY())) { "Reader baseline $label tap was not injected" }
    }

    private fun MacrobenchmarkScope.requireChaptersClick() {
        visibleBounds(By.desc("Chapters"))?.let { bounds ->
            if (device.click(bounds.centerX(), bounds.centerY())) return
        }
        visibleBounds(By.descContains("Chapter"))?.let { bounds ->
            if (device.click(bounds.centerX(), bounds.centerY())) return
        }

        val anchor = readingSettingsBounds() ?: error("Reader baseline visible top settings control missing")
        val candidate = device.findObjects(By.clickable(true))
            .asSequence()
            .mapNotNull { node -> runCatching { node.visibleBounds }.getOrNull() }
            .filter { bounds ->
                bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.right > 0 && bounds.bottom > 0 &&
                    bounds.left < device.displayWidth && bounds.top < device.displayHeight
            }
            .filter { bounds ->
                bounds.centerX() > anchor.centerX() &&
                    abs(bounds.centerY() - anchor.centerY()) <= maxOf(anchor.height(), bounds.height())
            }
            .minByOrNull { bounds -> bounds.centerX() - anchor.centerX() }
            ?: error("Reader baseline chapters control missing beside Aa")
        check(device.click(candidate.centerX(), candidate.centerY())) { "Reader baseline chapters tap was not injected" }
    }

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
        const val PAGE_FORWARD_TAP_X = 0.86f
        const val PAGE_FORWARD_TAP_Y = 0.52f
    }
}
