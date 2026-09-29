package com.junchen.jingdu.macrobenchmark

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device-only 200 MiB stability qualification.
 *
 * This intentionally is not a Macrobenchmark metric test. It proves the release-derived target can
 * import/open a deterministic 200 MiB TXT, execute indexed exact search and Smart Clean candidate
 * scanning on the same imported book, and remain alive/readable afterward.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalReleaseStabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun twoHundredMiBOpenSearchCleanNoOomAnr() {
        pressHome()
        val prepared = contentCall("prepareSource", FIXTURE_MIB)
        check(prepared.contains("mib=$FIXTURE_MIB")) {
            "200 MiB stability source preparation failed: $prepared"
        }
        check(prepared.contains("sha256=")) {
            "200 MiB stability source SHA-256 identity missing: $prepared"
        }
        contentCall("removeImportedSource", FIXTURE_MIB)
        contentCall("mode", "paged")

        device.executeShellCommand("am force-stop $PACKAGE_NAME")
        device.executeShellCommand("logcat -c")
        pressHome()

        val launch = device.executeShellCommand(
            "am start -W -a android.intent.action.VIEW " +
                "-d content://com.junchen.jingdu.benchmarkfixture/source/$FIXTURE_MIB " +
                "-t text/plain -f 0x00000001 -n $PACKAGE_NAME/.MainActivity",
        )
        check(launch.contains("Status: ok") && !launch.contains("Error:")) {
            "200 MiB ACTION_VIEW import launch failed: $launch"
        }

        val initialReady = waitForPagedReady()
        val pidBefore = processPid()
        check(pidBefore.isNotBlank()) { "200 MiB target process missing after first-readable ready" }

        val operations = contentCall("stability200Ops", FIXTURE_MIB)
        val bytes = Regex("""bytes=(\d+)""").find(operations)
            ?.groupValues?.get(1)?.toLongOrNull()
            ?: error("200 MiB byte-size proof missing: $operations")
        val sourceSha256 = Regex("""sourceSha256=([0-9a-f]{64})""").find(operations)
            ?.groupValues?.get(1)
            ?: error("200 MiB source SHA-256 proof missing: $operations")
        val normalizedSha256 = Regex("""normalizedSha256=([0-9a-f]{64})""").find(operations)
            ?.groupValues?.get(1)
            ?: error("200 MiB normalized SHA-256 proof missing: $operations")
        val hitCount = Regex("""searchHitCount=(\d+)""").find(operations)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: error("200 MiB indexed-search completion proof missing: $operations")
        check(hitCount > 0) { "200 MiB indexed-search returned no hits: $operations" }
        val cleanCandidateCount = Regex("""cleanCandidateCount=(\d+)""").find(operations)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: error("200 MiB Smart Clean completion proof missing: $operations")

        val finalReady = waitForPagedReady()
        val pidAfter = processPid()
        check(pidAfter.isNotBlank()) { "200 MiB target process missing after search/Clean operations" }
        check(pidAfter == pidBefore) {
            "200 MiB target process restarted during stability sequence: before=$pidBefore after=$pidAfter"
        }

        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString(
                    STATUS_KEY,
                    "fixtureMiB=$FIXTURE_MIB;" +
                        "fixtureBytes=$bytes;" +
                        "fixtureSha256=$sourceSha256;" +
                        "normalizedSha256=$normalizedSha256;" +
                        "pid=$pidAfter;" +
                        "initialPosition=${initialReady.first};" +
                        "initialLayoutGeneration=${initialReady.second};" +
                        "finalPosition=${finalReady.first};" +
                        "finalLayoutGeneration=${finalReady.second};" +
                        "searchHitCount=$hitCount;" +
                        "cleanCandidateCount=$cleanCandidateCount;" +
                        "operations=complete",
                )
            },
        )
    }

    private fun contentCall(method: String, arg: Any? = null): String {
        val command = buildString {
            append("content call --uri content://com.junchen.jingdu.benchmarkfixture ")
            append("--method ").append(method)
            if (arg != null) append(" --arg ").append(arg)
        }
        val result = device.executeShellCommand(command)
        check(!result.contains("Error") && !result.contains("Exception")) {
            "Benchmark provider call failed: method=$method arg=$arg result=$result"
        }
        return result
    }

    private fun waitForPagedReady(): Pair<Long, Long> {
        val deadlineNs = System.nanoTime() + READY_TIMEOUT_NS
        var position = -1L
        var generation = 0L
        while (System.nanoTime() < deadlineNs) {
            val state = contentCall("pageState")
            position = Regex("""position=(-?\d+)""").find(state)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
            generation = Regex("""layoutGeneration=(\d+)""").find(state)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (position >= 0L && generation > 0L) return position to generation
            Thread.sleep(POLL_MS)
        }
        error(
            "200 MiB Reader did not become/remain authoritative paged-ready: " +
                "position=$position layoutGeneration=$generation",
        )
    }

    private fun processPid(): String =
        device.executeShellCommand("pidof $PACKAGE_NAME").trim()

    private fun pressHome() {
        check(device.pressHome()) { "Could not press HOME before 200 MiB stability sequence" }
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
        const val FIXTURE_MIB = 200
        const val POLL_MS = 100L
        const val READY_TIMEOUT_NS = 180_000_000_000L
        const val STATUS_KEY = "jingdu.stability200MiB"
    }
}
