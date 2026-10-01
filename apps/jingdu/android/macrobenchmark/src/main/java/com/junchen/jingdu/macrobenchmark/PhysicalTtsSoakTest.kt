package com.junchen.jingdu.macrobenchmark

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalTtsSoakTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun backgroundTtsLongSessionSoak() {
        val minutes = InstrumentationRegistry.getArguments()
            .getString(DURATION_ARG)
            ?.toIntOrNull()
            ?: 30
        check(minutes in setOf(30, 60)) { "invalid TTS soak duration: $minutes" }

        seedFixture(FIXTURE_MIB)
        val launch = device.executeShellCommand("am start -W -n $PACKAGE_NAME/.MainActivity")
        check(launch.contains("Status: ok") && !launch.contains("Error:")) {
            "TTS soak Activity launch failed: $launch"
        }

        val startResult = fixtureCall("ttsStartSoak", FIXTURE_MIB)
        val bookId = value(startResult, "bookId") ?: error("TTS soak bookId missing: $startResult")
        val initialPid = waitForPid()
        val startProgress = waitForForwardProgress(0L)
        check(startProgress > 0L) { "TTS soak never advanced from zero" }

        check(device.pressHome()) { "TTS soak could not background the Activity" }
        Thread.sleep(1_000L)
        check(pid() == initialPid) { "TTS process changed while moving to background" }

        val deadline = System.nanoTime() + minutes.toLong() * 60L * 1_000_000_000L
        var sample = 0
        var previousProgress = startProgress
        var advancingSamples = 0
        var stalledSamples = 0
        var peakPssKb = totalPssKb().coerceAtLeast(1L)

        try {
            while (System.nanoTime() < deadline) {
                Thread.sleep(SAMPLE_INTERVAL_MS)
                check(pid() == initialPid) { "TTS process restarted during soak" }

                val state = fixtureCall("ttsSoakState", FIXTURE_MIB)
                val currentBookId = value(state, "bookId") ?: error("TTS soak state bookId missing: $state")
                check(currentBookId == bookId) {
                    "TTS soak book identity changed: expected=$bookId actual=$currentBookId"
                }
                val progress = longValue(state, "progress")
                check(progress >= previousProgress) {
                    "TTS progress moved backwards: previous=$previousProgress current=$progress"
                }
                val delta = progress - previousProgress
                if (delta > 0L) {
                    advancingSamples++
                    stalledSamples = 0
                } else {
                    stalledSamples++
                }
                check(stalledSamples < MAX_CONSECUTIVE_STALLED_SAMPLES) {
                    "TTS progress stalled for $stalledSamples consecutive retained samples at $progress"
                }
                previousProgress = progress
                val pssKb = totalPssKb()
                check(pssKb > 0L) { "TTS process PSS unavailable during soak" }
                peakPssKb = maxOf(peakPssKb, pssKb)
                check(peakPssKb <= MAX_PSS_KB) {
                    "TTS peak PSS exceeded soak ceiling: ${peakPssKb}KB > ${MAX_PSS_KB}KB"
                }

                sample++
                reportSample(
                    minutes = minutes,
                    sample = sample,
                    progress = progress,
                    delta = delta,
                    pssKb = pssKb,
                    pid = initialPid,
                    runtimePlaying = boolValue(state, "backgroundTtsPlaying"),
                )
            }

            check(previousProgress > startProgress) {
                "TTS soak did not advance beyond warm-up progress: start=$startProgress end=$previousProgress"
            }
            reportPass(
                minutes = minutes,
                samples = sample,
                start = startProgress,
                end = previousProgress,
                advancingSamples = advancingSamples,
                peakPssKb = peakPssKb,
                pid = initialPid,
            )
        } finally {
            runCatching { fixtureCall("ttsStopSoak", FIXTURE_MIB) }
        }
    }

    private fun seedFixture(mib: Int) {
        val result = fixtureCall("seed", mib)
        check(result.contains("bookId=") && result.contains("bytes=")) {
            "TTS soak fixture seed failed: $result"
        }
    }

    private fun waitForPid(): Int {
        repeat(100) {
            val value = pid()
            if (value > 0) return value
            Thread.sleep(100L)
        }
        error("TTS process PID unavailable")
    }

    private fun waitForForwardProgress(start: Long): Long {
        repeat(90) {
            val state = fixtureCall("ttsSoakState", FIXTURE_MIB)
            val progress = longValue(state, "progress")
            if (progress > start) return progress
            Thread.sleep(500L)
        }
        error("TTS service did not persist forward progress during warm-up")
    }

    private fun fixtureCall(method: String, arg: Int): String =
        device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method $method --arg $arg",
        ).trim()

    private fun value(bundle: String, key: String): String? =
        Regex("""(?:^|[,{ ])$key=([^,}\]]+)""").find(bundle)?.groupValues?.get(1)?.trim()

    private fun longValue(bundle: String, key: String): Long =
        value(bundle, key)?.toLongOrNull() ?: error("TTS soak $key missing: $bundle")

    private fun boolValue(bundle: String, key: String): Boolean =
        value(bundle, key)?.equals("true", ignoreCase = true) == true

    private fun pid(): Int =
        device.executeShellCommand("pidof $PACKAGE_NAME").trim().substringBefore(' ').toIntOrNull() ?: -1

    private fun totalPssKb(): Long {
        val meminfo = device.executeShellCommand("dumpsys meminfo $PACKAGE_NAME")
        return Regex("""TOTAL PSS:\s+(\d+)""").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("""^\s*TOTAL\s+(\d+)""", RegexOption.MULTILINE)
                .find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L
    }

    private fun reportSample(
        minutes: Int,
        sample: Int,
        progress: Long,
        delta: Long,
        pssKb: Long,
        pid: Int,
        runtimePlaying: Boolean,
    ) {
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString(
                    SAMPLE_KEY,
                    "durationMinutes=$minutes;sample=$sample;progress=$progress;delta=$delta;" +
                        "pssKb=$pssKb;pid=$pid;runtimePlaying=$runtimePlaying",
                )
            },
        )
    }

    private fun reportPass(
        minutes: Int,
        samples: Int,
        start: Long,
        end: Long,
        advancingSamples: Int,
        peakPssKb: Long,
        pid: Int,
    ) {
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString(
                    PASS_KEY,
                    "durationMinutes=$minutes;samples=$samples;start=$start;end=$end;" +
                        "advancingSamples=$advancingSamples;peakPssKb=$peakPssKb;pid=$pid",
                )
            },
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
        const val FIXTURE_MIB = 100
        const val DURATION_ARG = "jingdu.ttsSoakMinutes"
        const val SAMPLE_KEY = "jingdu.ttsSoakSample"
        const val PASS_KEY = "jingdu.ttsSoakPass"
        const val SAMPLE_INTERVAL_MS = 60_000L
        const val MAX_CONSECUTIVE_STALLED_SAMPLES = 3
        const val MAX_PSS_KB = 512L * 1024L
    }
}
