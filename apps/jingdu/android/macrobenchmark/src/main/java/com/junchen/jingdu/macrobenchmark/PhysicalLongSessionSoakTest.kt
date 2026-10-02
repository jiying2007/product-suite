package com.junchen.jingdu.macrobenchmark

import android.os.Bundle
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device-only long reading soak. Never part of hosted Macrobenchmark CI.
 * The workflow restricts production qualification to 60 or 180 minutes.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalLongSessionSoakTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun longReadingSoak() {
        val minutes = InstrumentationRegistry.getArguments().getString(DURATION_ARG)?.toIntOrNull() ?: 60
        check(minutes in 1..180) { "invalid soak duration: $minutes" }
        val mode = InstrumentationRegistry.getArguments().getString(MODE_ARG) ?: MODE_PAGED
        check(mode in setOf(MODE_PAGED, MODE_CONTINUOUS_STRESS)) { "invalid soak mode: $mode" }

        seedFixture(FIXTURE_MIB)
        setReaderMode(if (mode == MODE_CONTINUOUS_STRESS) "continuous-stress" else "paged")
        device.executeShellCommand("am force-stop $PACKAGE_NAME")
        val launch = device.executeShellCommand(
            "am start -W -n $PACKAGE_NAME/.MainActivity",
        )
        check(launch.contains("Status: ok") && !launch.contains("Error:")) { "Reader launch failed: $launch" }

        val title = "Benchmark Novel $FIXTURE_MIB MiB"
        check(device.wait(Until.hasObject(By.textContains(title)), LIBRARY_TIMEOUT_MS)) { "soak fixture missing: $title" }
        val card = device.findObject(By.textContains(title)) ?: error("soak fixture unavailable")
        val bounds = card.visibleBounds
        check(device.click(bounds.centerX(), bounds.centerY())) { "soak fixture tap failed" }
        val startingPosition = waitForPosition()

        val initialPid = pid()
        check(initialPid > 0) { "Reader PID unavailable at soak start" }
        val deadline = System.nanoTime() + minutes.toLong() * 60L * 1_000_000_000L
        var interactions = 0
        var lastPosition = startingPosition
        var nextSampleNs = System.nanoTime()
        var peakPssKb = 0L

        while (System.nanoTime() < deadline) {
            check(pid() == initialPid) { "Reader process restarted during soak" }
            if (mode == MODE_CONTINUOUS_STRESS) {
                val width = device.displayWidth
                val height = device.displayHeight
                check(device.swipe(width / 2, (height * 0.78f).toInt(), width / 2, (height * 0.28f).toInt(), 24)) {
                    "physical continuous swipe input failed"
                }
            } else {
                check(device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)) { "physical volume page input failed" }
            }
            interactions++
            Thread.sleep(PAGE_INTERVAL_MS)

            if (System.nanoTime() >= nextSampleNs) {
                val position = readerPosition()
                val pssKb = totalPssKb()
                check(pssKb > 0L) { "Reader total PSS unavailable during soak" }
                peakPssKb = maxOf(peakPssKb, pssKb)
                reportSample(minutes, mode, interactions, position, pssKb, initialPid)
                lastPosition = position
                nextSampleNs = System.nanoTime() + SAMPLE_INTERVAL_NS
            }
        }

        val finalPosition = readerPosition()
        check(finalPosition > startingPosition || lastPosition > startingPosition) {
            "Reader did not advance during soak: start=$startingPosition last=$lastPosition final=$finalPosition"
        }
        check(pid() == initialPid) { "Reader process changed at soak completion" }
        check(peakPssKb in 1..MAX_PSS_KB) {
            "Reader peak PSS exceeded soak ceiling: ${peakPssKb}KB > ${MAX_PSS_KB}KB"
        }
        reportPass(minutes, mode, interactions, startingPosition, finalPosition, peakPssKb, initialPid)
    }

    private fun seedFixture(mib: Int) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg $mib",
        )
        check(result.contains("bytes=")) { "soak fixture seed failed: $result" }
    }

    private fun setReaderMode(mode: String) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(result.contains("Result: Bundle[{}]")) { "soak Reader mode setup failed: $result" }
    }

    private fun waitForPosition(): Long {
        val deadline = System.nanoTime() + READY_TIMEOUT_NS
        while (System.nanoTime() < deadline) {
            val position = readerPosition()
            if (position >= 0L) return position
            Thread.sleep(100)
        }
        error("Reader never became authoritative-ready for soak")
    }

    private fun readerPosition(): Long {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method position",
        )
        return Regex("""position=(-?\d+)""").find(result)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
    }

    private fun pid(): Int =
        device.executeShellCommand("pidof $PACKAGE_NAME").trim().substringBefore(' ').toIntOrNull() ?: -1

    private fun totalPssKb(): Long {
        val meminfo = device.executeShellCommand("dumpsys meminfo $PACKAGE_NAME")
        return Regex("""TOTAL PSS:\s+(\d+)""").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("""^\s*TOTAL\s+(\d+)""", RegexOption.MULTILINE).find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L
    }

    private fun reportSample(minutes: Int, mode: String, interactions: Int, position: Long, pssKb: Long, pid: Int) {
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString(
                    SAMPLE_KEY,
                    "durationMinutes=$minutes;mode=$mode;interactions=$interactions;position=$position;pssKb=$pssKb;pid=$pid",
                )
            },
        )
    }

    private fun reportPass(minutes: Int, mode: String, interactions: Int, start: Long, end: Long, peakPssKb: Long, pid: Int) {
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString(
                    PASS_KEY,
                    "durationMinutes=$minutes;mode=$mode;interactions=$interactions;start=$start;end=$end;peakPssKb=$peakPssKb;pid=$pid",
                )
            },
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
        const val FIXTURE_MIB = 100
        const val DURATION_ARG = "jingdu.soakMinutes"
        const val MODE_ARG = "jingdu.soakMode"
        const val MODE_PAGED = "paged"
        const val MODE_CONTINUOUS_STRESS = "continuous-stress"
        const val SAMPLE_KEY = "jingdu.readerSoakSample"
        const val PASS_KEY = "jingdu.readerSoakPass"
        const val LIBRARY_TIMEOUT_MS = 10_000L
        const val READY_TIMEOUT_NS = 15_000_000_000L
        const val PAGE_INTERVAL_MS = 2_000L
        const val SAMPLE_INTERVAL_NS = 15_000_000_000L
        const val MAX_PSS_KB = 512L * 1024L
    }
}
