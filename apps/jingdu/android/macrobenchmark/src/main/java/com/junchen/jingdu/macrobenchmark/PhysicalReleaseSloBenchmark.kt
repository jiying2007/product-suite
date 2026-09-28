package com.junchen.jingdu.macrobenchmark

import android.os.Bundle
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

private data class PhysicalFixtureIdentity(
    val mib: Int,
    val bytes: Long,
    val sha256: String,
)

private data class PhysicalPagedReadyState(
    val position: Long,
    val layoutGeneration: Long,
)

private data class PhysicalSourceIdentity(
    val mib: Int,
    val bytes: Long,
    val sha256: String,
    val uri: String,
)

/**
 * Physical-device-only Release SLO journeys.
 *
 * This class is intentionally excluded from Hosted Reader performance CI. It emits retained
 * machine-readable samples that the physical runner validates with product SLO-specific checkers.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalReleaseSloBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun unchangedImportedBookFirstReadable10MiB() {
        var sampleIndex = 0
        var fixtureIdentity: PhysicalFixtureIdentity? = null

        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = PHYSICAL_COMPILATION_MODE,
            startupMode = StartupMode.WARM,
            iterations = REQUIRED_SAMPLES,
            setupBlock = {
                pressHome()
                seedFixture(FIXTURE_MIB)
                if (fixtureIdentity == null) fixtureIdentity = fixtureInfo(FIXTURE_MIB)

                // Prewarm the already-imported book outside the measured interval. The product SLO
                // is reopen-to-first-readable for unchanged content, not new-import latency.
                setReaderMode("paged")
                startTargetAndWait()
                openFixture(FIXTURE_MIB)
                waitForPagedLayoutReady()
                check(device.pressBack()) { "Reader BACK input did not return toward Library" }
                waitForLibraryFixture(FIXTURE_MIB)
                pressHome()

                // Reset runtime-only readiness immediately before the measured reopen.
                setReaderMode("paged")
            },
            measureBlock = {
                startTargetAndWait()
                val startedNs = System.nanoTime()
                openFixture(FIXTURE_MIB)
                val ready = waitForPagedLayoutReady()
                val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
                val fixture = checkNotNull(fixtureIdentity) { "Physical fixture identity missing" }
                sampleIndex += 1
                reportFirstReadableSample(sampleIndex, durationMs, fixture, ready)
            },
        )

        check(sampleIndex == REQUIRED_SAMPLES) {
            "Physical first-readable benchmark emitted $sampleIndex samples, expected $REQUIRED_SAMPLES"
        }
    }

    @Test
    fun new20MiBTxtFirstReadable() = newImportedTxtFirstReadable(
        mib = NEW_20_IMPORT_MIB,
        iterations = NEW_IMPORT_SAMPLES,
    )

    @Test
    fun new100MiBTxtFirstReadable() = newImportedTxtFirstReadable(
        mib = NEW_100_IMPORT_MIB,
        iterations = NEW_IMPORT_SAMPLES,
    )

    private fun newImportedTxtFirstReadable(mib: Int, iterations: Int) {
        var sampleIndex = 0
        var sourceIdentity: PhysicalSourceIdentity? = null

        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = PHYSICAL_COMPILATION_MODE,
            startupMode = StartupMode.COLD,
            iterations = iterations,
            setupBlock = {
                pressHome()
                val prepared = prepareSource(mib)
                sourceIdentity = prepared
                removeImportedSource(prepared)
                setReaderMode("paged")
                device.executeShellCommand("am force-stop $PACKAGE_NAME")
                pressHome()
            },
            measureBlock = {
                val source = checkNotNull(sourceIdentity) { "Physical new-import source identity missing" }
                val startedNs = System.nanoTime()
                launchImportSource(source)
                val ready = waitForPagedLayoutReady()
                val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
                sampleIndex += 1
                reportNewImportSample(sampleIndex, durationMs, source, ready)
            },
        )

        check(sampleIndex == iterations) {
            "Physical $mib MiB new-import benchmark emitted $sampleIndex samples, expected $iterations"
        }
    }

    private fun MacrobenchmarkScope.seedFixture(mib: Int) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg $mib",
        )
        check(result.contains("bytes=")) { "Reader physical fixture seed failed: $result" }
    }

    private fun MacrobenchmarkScope.prepareSource(mib: Int): PhysicalSourceIdentity {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method prepareSource --arg $mib",
        )
        val bytes = Regex("""bytes=(\d+)""").find(result)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Reader physical source byte identity missing: $result")
        val reportedMib = Regex("""mib=(\d+)""").find(result)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Reader physical source MiB identity missing: $result")
        val sha256 = Regex("""sha256=([0-9a-f]{64})""").find(result)?.groupValues?.get(1)
            ?: error("Reader physical source SHA-256 identity missing: $result")
        val uri = Regex("""uri=(content://[^,}\]]+)""").find(result)?.groupValues?.get(1)
            ?: error("Reader physical source URI missing: $result")
        check(reportedMib == mib) { "Reader physical source identity mismatch: requested=$mib result=$result" }
        return PhysicalSourceIdentity(reportedMib, bytes, sha256, uri)
    }

    private fun MacrobenchmarkScope.removeImportedSource(source: PhysicalSourceIdentity) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method removeImportedSource --arg ${source.mib}",
        )
        check(result.contains("sha256=${source.sha256}")) {
            "Reader physical source cleanup identity mismatch: expected=${source.sha256} result=$result"
        }
    }

    private fun MacrobenchmarkScope.fixtureInfo(mib: Int): PhysicalFixtureIdentity {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method fixtureInfo --arg $mib",
        )
        val bytes = Regex("""bytes=(\d+)""").find(result)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Reader physical fixture byte identity missing: $result")
        val reportedMib = Regex("""mib=(\d+)""").find(result)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Reader physical fixture MiB identity missing: $result")
        val sha256 = Regex("""sha256=([0-9a-f]{64})""").find(result)?.groupValues?.get(1)
            ?: error("Reader physical fixture SHA-256 identity missing: $result")
        check(reportedMib == mib) { "Reader physical fixture identity mismatch: requested=$mib result=$result" }
        return PhysicalFixtureIdentity(reportedMib, bytes, sha256)
    }

    private fun MacrobenchmarkScope.setReaderMode(mode: String) {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode",
        )
        check(result.contains("Result: Bundle[{}]")) { "Reader physical mode setup failed: $result" }
    }

    private fun MacrobenchmarkScope.launchImportSource(source: PhysicalSourceIdentity) {
        val result = device.executeShellCommand(
            "am start -W -a android.intent.action.VIEW -d ${source.uri} -t text/plain " +
                "-f 0x00000001 -n $PACKAGE_NAME/.MainActivity",
        )
        check(result.contains("Status: ok") && !result.contains("Error:")) {
            "Reader ACTION_VIEW import launch failed: $result"
        }
    }

    private fun MacrobenchmarkScope.startTargetAndWait() {
        try {
            startActivityAndWait()
        } catch (error: IllegalStateException) {
            throw IllegalStateException(
                "${error.message ?: "Reader target launch failed"}\n===== Reader target diagnostics =====\n${failureDiagnostics()}",
                error,
            )
        }
    }

    private fun MacrobenchmarkScope.openFixture(mib: Int) {
        val title = "Benchmark Novel $mib MiB"
        if (!device.wait(Until.hasObject(By.textContains(title)), LIBRARY_TIMEOUT_MS)) {
            error("fixture card missing: $title\n===== Reader target diagnostics =====\n${failureDiagnostics()}")
        }
        val card = device.findObject(By.textContains(title)) ?: error("fixture card unavailable: $title")
        val bounds = runCatching { card.visibleBounds }.getOrNull() ?: error("fixture card stale: $title")
        check(device.click(bounds.centerX(), bounds.centerY())) { "fixture card tap was not injected: $title" }
    }

    private fun MacrobenchmarkScope.waitForLibraryFixture(mib: Int) {
        val title = "Benchmark Novel $mib MiB"
        device.waitForIdle()
        check(device.wait(Until.hasObject(By.textContains(title)), LIBRARY_TIMEOUT_MS)) {
            "Reader did not return to Library fixture card after prewarm: $title"
        }
    }

    private fun MacrobenchmarkScope.pagedReadyState(): PhysicalPagedReadyState {
        val result = device.executeShellCommand(
            "content call --uri content://com.junchen.jingdu.benchmarkfixture --method pageState",
        )
        val position = Regex("""position=(-?\d+)""").find(result)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Reader physical paged position query failed: $result")
        val generation = Regex("""layoutGeneration=(\d+)""").find(result)?.groupValues?.get(1)?.toLongOrNull()
            ?: error("Reader physical layout generation query failed: $result")
        return PhysicalPagedReadyState(position, generation)
    }

    private fun MacrobenchmarkScope.waitForPagedLayoutReady(): PhysicalPagedReadyState {
        val deadline = System.nanoTime() + READY_TIMEOUT_NS
        var state = PhysicalPagedReadyState(-1L, 0L)
        while (System.nanoTime() < deadline) {
            state = pagedReadyState()
            if (state.position >= 0L && state.layoutGeneration > 0L) return state
            Thread.sleep(POLL_MS)
        }
        error("Reader physical first-readable page did not become authoritative/raster-ready: $state")
    }

    private fun reportFirstReadableSample(
        iteration: Int,
        durationMs: Double,
        fixture: PhysicalFixtureIdentity,
        ready: PhysicalPagedReadyState,
    ) {
        check(durationMs.isFinite() && durationMs > 0.0) { "Invalid physical first-readable duration: $durationMs" }
        val value = buildString {
            append("metric=unchanged-imported-book")
            append(";iteration=").append(iteration)
            append(";durationMs=").append(String.format(Locale.US, "%.3f", durationMs))
            append(";fixtureMiB=").append(fixture.mib)
            append(";fixtureBytes=").append(fixture.bytes)
            append(";fixtureSha256=").append(fixture.sha256)
            append(";position=").append(ready.position)
            append(";layoutGeneration=").append(ready.layoutGeneration)
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString(SAMPLE_STATUS_KEY, value) },
        )
    }

    private fun reportNewImportSample(
        iteration: Int,
        durationMs: Double,
        source: PhysicalSourceIdentity,
        ready: PhysicalPagedReadyState,
    ) {
        check(durationMs.isFinite() && durationMs > 0.0) { "Invalid physical new-import duration: $durationMs" }
        val value = buildString {
            append("metric=new-import-").append(source.mib).append("mib")
            append(";iteration=").append(iteration)
            append(";durationMs=").append(String.format(Locale.US, "%.3f", durationMs))
            append(";fixtureMiB=").append(source.mib)
            append(";fixtureBytes=").append(source.bytes)
            append(";fixtureSha256=").append(source.sha256)
            append(";position=").append(ready.position)
            append(";layoutGeneration=").append(ready.layoutGeneration)
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString(NEW_IMPORT_STATUS_KEY, value) },
        )
    }

    private fun MacrobenchmarkScope.failureDiagnostics(): String = buildString {
        append("pidof: ")
        append(device.executeShellCommand("pidof $PACKAGE_NAME").trim().ifEmpty { "<not-running>" })
        append('\n')
        append("exit-info:\n")
        append(device.executeShellCommand("dumpsys activity exit-info $PACKAGE_NAME").takeLast(12_000))
    }

    private companion object {
        const val PACKAGE_NAME = "com.junchen.jingdu"
        const val FIXTURE_MIB = 10
        const val REQUIRED_SAMPLES = 10
        const val POLL_MS = 25L
        const val LIBRARY_TIMEOUT_MS = 8_000L
        const val READY_TIMEOUT_NS = 12_000_000_000L
        const val SAMPLE_STATUS_KEY = "jingdu.firstReadableSample"
        const val NEW_20_IMPORT_MIB = 20
        const val NEW_100_IMPORT_MIB = 100
        const val NEW_IMPORT_SAMPLES = 5
        const val NEW_IMPORT_STATUS_KEY = "jingdu.newImportSample"

        val PHYSICAL_COMPILATION_MODE = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
            warmupIterations = 0,
        )
    }
}
