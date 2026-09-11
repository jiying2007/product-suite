#!/usr/bin/env python3
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
generator = (ROOT / "apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt").read_text(encoding="utf-8")
journey = (ROOT / "apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt").read_text(encoding="utf-8")
runner = (ROOT / "scripts/run-android-macrobenchmark-ci.sh").read_text(encoding="utf-8")
physical_runner_path = ROOT / "scripts/run-android-physical-release-performance.sh"
physical_workflow_path = ROOT / ".github/workflows/android-physical-release-performance.yml"
hosted_baseline_path = ROOT / "scripts/reader-hosted-emulator-baseline.json"
checker = (ROOT / "scripts/check-android-performance-slo.py").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
app_gradle = (ROOT / "apps/jingdu/android/app/build.gradle").read_text(encoding="utf-8")
macro_gradle = (ROOT / "apps/jingdu/android/macrobenchmark/build.gradle").read_text(encoding="utf-8")
root_gradle = (ROOT / "apps/jingdu/android/build.gradle").read_text(encoding="utf-8")
benchmark_provider = (ROOT / "apps/jingdu/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt").read_text(encoding="utf-8")
proguard = (ROOT / "apps/jingdu/android/app/proguard-rules.pro").read_text(encoding="utf-8")
product_baseline_path = ROOT / "apps/jingdu/android/app/src/main/baseline-prof.txt"
product_startup_path = ROOT / "apps/jingdu/android/app/src/main/startup-prof.txt"
provenance_path = ROOT / "docs/READER_PROFILE_PROVENANCE.md"

startup_marker = "@Test fun readerStartup()"
runtime_marker = "@Test fun readerCriticalJourneys()"
assert startup_marker in generator, "Reader startup profile CUJ missing"
assert runtime_marker in generator, "Reader runtime profile CUJ missing"
assert generator.count("includeInStartupProfile = true") == 1, "exactly one startup-profile CUJ is required"
assert generator.count("includeInStartupProfile = false") == 1, "runtime CUJs must be Baseline-only"
assert 'outputFilePrefix = "jingdu-reader-startup"' in generator
assert 'outputFilePrefix = "jingdu-reader-critical"' in generator

startup_at = generator.index(startup_marker)
runtime_at = generator.index(runtime_marker)
assert startup_at < runtime_at, "startup CUJ must remain separate from runtime CUJs"
startup_block = generator[startup_at:runtime_at]
runtime_block = generator[runtime_at:]
assert "includeInStartupProfile = true" in startup_block
assert "PAGE_FORWARD_TAP_X" not in startup_block, "page turns must not inflate Startup Profile"
assert "requireChaptersClick" not in startup_block
assert "includeInStartupProfile = false" in runtime_block
assert "PAGE_FORWARD_TAP_X" in runtime_block, "hosted runtime profile must exercise the real reader tap zone"
assert "KEYCODE_VOLUME_DOWN" not in generator, "hosted profile must not depend on emulator hardware-volume delivery"
assert "requireChaptersClick" in runtime_block
assert runtime_block.index("requireChaptersClick") < runtime_block.index('setProfileMode("continuous")'), "Quick/Chapters must remain paged profile CUJs before continuous mode"
assert 'setProfileMode("continuous")' in runtime_block, "runtime profile must switch mode through deterministic provider protocol"
assert 'requireContinuousModeClick' not in generator, "localized/UI-text profile mode switch retained"
assert 'content call --uri content://com.junchen.jingdu.benchmarkfixture --method mode --arg $mode' in generator

# Stage 1 measures the production R8 APK in the install state Android defines for a fresh Play-style
# install: the curated Baseline Profile already packaged in the APK is required and precompiled. The
# profile generated later in this job is independent freshness evidence and can never self-feed SLO.
assert "BaselineProfileMode.Require" in journey
assert "BaselineProfileMode.Disable" not in journey
assert "warmupIterations = 0" in journey
assert journey.count('repeat(6) {') >= 2, "page and continuous journeys must both retain six interactions"
assert "PAGE_FORWARD_TAP_X" in journey, "hosted page-turn journey must use a real reader tap zone"
assert "PAGE_TURN_INPUT_ARG" in journey and 'PHYSICAL_VOLUME_INPUT = "physical-volume"' in journey, "physical volume input selector missing"
assert "KeyEvent.KEYCODE_VOLUME_DOWN" in journey, "physical Release volume page-turn path missing"
assert 'sampled.get("frameDurationCpuMs")' in checker
assert "RELEASE_P95_MS = 40.0" in checker and "RELEASE_P99_MS = 80.0" in checker, "product Release SLO must remain 40/80"
assert "HOSTED_P95_MS = 160.0" in checker and "HOSTED_P99_MS = 220.0" in checker, "evidence-derived hosted absolute regression ceiling missing"
assert "HOSTED_MAX_REGRESSION_RATIO = 0.15" in checker, "hosted relative regression budget missing"
assert 'choices=("release", "hosted-regression")' in checker, "two-level performance gate modes missing"
assert "load_hosted_baseline" in checker, "checked-in hosted baseline contract missing"

assert hosted_baseline_path.is_file(), "checked-in hosted emulator baseline missing"
hosted_baseline = json.loads(hosted_baseline_path.read_text(encoding="utf-8"))
assert hosted_baseline.get("schemaVersion") == 1
assert hosted_baseline.get("kind") == "reader-hosted-emulator-regression-baseline"
assert hosted_baseline.get("maxRegressionRatio") == 0.15
assert hosted_baseline.get("absoluteCeilingMs") == {"p95": 160.0, "p99": 220.0}
source = hosted_baseline.get("source", {})
assert source.get("headSha") == "fa22d088df7456330244ac4dc2c00a82da888656", "hosted baseline source head drifted"
assert source.get("workflowRunId") == 33294378785 and source.get("jobId") == 99212107479
assert source.get("artifactId") == 9727262417
assert source.get("artifactSha256") == "c48fbfe3e4daba9c48cba836e67478eb44043abcefb9b1f7cb479684cd1039c6"
expected_hosted = {
    "pageTurn10MiB": (59, 64.348030, 75.029256),
    "continuousScroll10MiB": (687, 128.868375, 155.338134),
    "chaptersAndSettings10MiB": (167, 135.095162, 187.723889),
}
for name, (samples, p95, p99) in expected_hosted.items():
    record = hosted_baseline["benchmarks"][name]
    assert record["samples"] == samples
    assert abs(float(record["p95Ms"]) - p95) < 1e-6
    assert abs(float(record["p99Ms"]) - p99) < 1e-6

# Real frame gate and profile collection intentionally use different target variants. Macrobenchmark
# must see production-like R8 code; HRF collection must see a non-obfuscated profileable target.
benchmark_block = app_gradle[app_gradle.index("        benchmark {"):app_gradle.index("        profile {")]
profile_block = app_gradle[app_gradle.index("        profile {"):app_gradle.index("    sourceSets {")]
assert "initWith release" in benchmark_block
assert "minifyEnabled = true" in benchmark_block and "shrinkResources = true" in benchmark_block
assert "debuggable = false" in benchmark_block
assert "initWith release" in profile_block
assert "minifyEnabled = false" in profile_block and "shrinkResources = false" in profile_block
assert "debuggable = false" in profile_block
assert 'java.srcDir "src/benchmark/java"' in app_gradle
assert 'kotlin.srcDir "src/benchmark/java"' in app_gradle, "profile variant must compile the benchmark Kotlin fixture provider"
assert 'manifest.srcFile "src/benchmark/AndroidManifest.xml"' in app_gradle
assert "profile {" in macro_gradle and 'matchingFallbacks = ["profile"]' in macro_gradle
for task in (":app:assembleBenchmark", ":app:assembleProfile", ":macrobenchmark:assembleBenchmark", ":macrobenchmark:assembleProfile"):
    assert task in root_gradle, f"androidCheck must compile hosted variant: {task}"

# The hosted fixture is a stable R8-safe protocol and must match the real reader environment.
assert "controlsAutoHideMs = 3500L" in benchmark_provider, "benchmark must use production controls auto-hide"
assert "controlsAutoHideMs = 60_000L" not in benchmark_provider, "benchmark-only persistent controls bias retained"
assert "DataStore flush is synchronous" in benchmark_provider, "benchmark mode ACK rationale missing"
assert 'putLong("modeApplied", 1L)' not in benchmark_provider, "unstable R8 Bundle payload ACK retained"
assert 'result.contains("Result: Bundle[{}]")' in journey, "deterministic empty-Bundle mode ACK contract missing"
assert "-keep class com.junchen.jingdu.ReaderBenchmarkFixtureProvider { *; }" in proguard, "hosted fixture provider R8 keep missing"

slo_call = 'python3 scripts/check-android-performance-slo.py'
profile_swap = 'install_pair "Profile collection" "$PROFILE_TARGET_APK" "$PROFILE_TEST_APK"'
profile_call = 'run_instrumentation BaselineProfile "$PROFILE_REMOTE" "$RESULT_ROOT/profile-instrumentation.log" "$PROFILE_CLASS"'
emulator_start = '"$EMULATOR" -avd "$AVD_NAME"'
host_build = './gradlew --no-daemon --warning-mode all'
settle_call = '\nwait_for_performance_settle\n'
r8_install = 'install_pair "R8 Macrobenchmark" "$BENCHMARK_TARGET_APK" "$BENCHMARK_TEST_APK"'
macro_call = 'run_instrumentation Macrobenchmark "$MACRO_REMOTE" "$RESULT_ROOT/macro-instrumentation.log" "$MACRO_CLASS"'
assert slo_call in runner and profile_call in runner and profile_swap in runner
assert '--mode hosted-regression' in runner, "hosted CI must use regression mode"
assert '--baseline "$HOSTED_BASELINE"' in runner and 'reader-hosted-emulator-baseline.json' in runner, "hosted CI baseline wiring missing"
assert ':app:assembleBenchmark :macrobenchmark:assembleBenchmark' in runner
assert ':app:assembleProfile :macrobenchmark:assembleProfile' in runner
assert 'BENCHMARK_TARGET_APK=' in runner and 'PROFILE_TARGET_APK=' in runner
assert host_build in runner and emulator_start in runner
assert runner.index(host_build) < runner.index(emulator_start), "hosted performance APKs must build before the measurement emulator starts"
assert 'Performance APKs built before emulator start; launching fresh measurement guest' in runner
assert 'PERFORMANCE_SETTLE_SECONDS="${JINGDU_PERFORMANCE_SETTLE_SECONDS:-360}"' in runner, "fresh hosted performance guest must retain deterministic six-minute post-boot settle"
assert 'wait_for_performance_settle()' in runner, "fresh hosted performance settle function missing"
assert settle_call in runner and r8_install in runner
assert runner.index(emulator_start) < runner.index(settle_call) < runner.index(r8_install), "performance measurement must begin only after fresh guest settle"
assert 'second % 15 == 0' in runner, "performance settle must repeatedly verify guest health"
assert 'read_guest_uptime_seconds()' in runner and '"$ADB" shell cat /proc/uptime' in runner, "guest uptime must be read without lossy adb argument quoting"
assert "shell cut -d' ' -f1 /proc/uptime" not in runner, "remote cut-based uptime parsing must not return"
assert 'read_guest_boot_id()' in runner and '/proc/sys/kernel/random/boot_id' in runner, "guest boot identity tracking missing"
assert 'record_guest_identity' in runner and 'assert_guest_identity()' in runner, "guest identity fail-closed contract missing"
assert 'assert_guest_identity "during performance settle at ${second}s/${PERFORMANCE_SETTLE_SECONDS}s"' in runner
assert 'assert_guest_identity "before ${label} target installation"' in runner
assert 'assert_guest_identity "after ${label} APK installation"' in runner
assert 'assert_guest_identity "before ${rule} instrumentation"' in runner
assert 'assert_guest_identity "after ${rule} instrumentation"' in runner
assert 'guest_uptime=' in runner, "performance guest age must remain visible in evidence"
assert runner.index(slo_call) < runner.index(profile_swap) < runner.index(profile_call), "R8 performance result must freeze before non-minified profile target is installed"
assert "SLO_STATUS=$?" in runner, "performance result must be retained across profile generation"
assert 'preserve_failed_macro_evidence "$MACRO_REMOTE"' in runner
assert runner.index('if (( SLO_STATUS != 0 )); then\n  preserve_failed_macro_evidence "$MACRO_REMOTE"') < runner.index(profile_swap), "red performance Perfetto must be retained before a later Profile failure can exit"
assert 'PROFILE_RAW="$RESULT_ROOT/profile/raw"' in runner
assert 'baseline-prof.txt' in runner and 'startup-prof.txt' in runner
assert 'sort -u > "$RESULT_ROOT/profile/baseline-prof.txt"' in runner
assert 'sort -u > "$RESULT_ROOT/profile/startup-prof.txt"' in runner
last_slo_exit = runner.rfind('exit "$SLO_STATUS"')
assert last_slo_exit > runner.index(profile_call), "red performance gate must still fail after profiles are emitted"
assert 'GPU_MODE="${JINGDU_EMULATOR_GPU_MODE:-auto}"' in runner, "hosted emulator must use the recommended auto graphics mode by default"
assert 'settings put secure immersive_mode_confirmations confirmed' in runner, "hosted emulator must suppress system immersive onboarding before Reader input"
assert 'settings get secure immersive_mode_confirmations' in runner and 'immersive_confirmation' in runner, "immersive onboarding suppression must be verified by read-back"

# Hosted instrumentation must run only the authority for each stage. This prevents unrelated
# Startup/Profile tests from turning the frame gate into a mixed-suite infrastructure result.
assert 'local test_class="${4:-}"' in runner, "instrumentation class filter parameter missing"
assert 'class_args=(-e class "$test_class")' in runner, "instrumentation class filter wiring missing"
assert 'MACRO_CLASS="com.junchen.jingdu.macrobenchmark.ReaderJourneyBenchmark"' in runner
assert 'PROFILE_CLASS="com.junchen.jingdu.macrobenchmark.BaselineProfileGenerator"' in runner
assert 'StartupBenchmark' not in runner, "standalone startup suite must not contaminate the frame gate"

# An invalid instrumentation run is infrastructure evidence, not a performance result. The hosted
# gate must fail closed on the fresh measurement guest rather than retrying a system_server-dead AVD.
assert "return 1" in runner[runner.index("run_instrumentation()") : runner.index("preserve_failed_macro_evidence()")]
assert "wait_for_android_ready 120" in runner
assert "INSTRUMENTATION_ABORTED" in runner and "System has crashed" in runner
assert runner.count(macro_call) == 1, "Macrobenchmark must not retry on the same failed guest"
assert "attempting one bounded guest recovery" not in runner
assert "macro-instrumentation-retry.log" not in runner
assert 'fail_emulator "Reader Macrobenchmark instrumentation failed on fresh measurement guest"' in runner
assert 'guest-logcat-tail.txt' in runner, "system-level performance failures must preserve guest logcat evidence"

# Physical Release qualification is a separate, manually dispatched self-hosted physical-device gate.
assert physical_runner_path.is_file() and physical_workflow_path.is_file(), "physical Release performance gate assets missing"
physical_runner = physical_runner_path.read_text(encoding="utf-8")
physical_workflow = physical_workflow_path.read_text(encoding="utf-8")
assert 'ro.kernel.qemu' in physical_runner and 'refuses emulator/generic devices' in physical_runner
assert '-e jingdu.pageTurnInput physical-volume' in physical_runner, "physical Release must force real volume-key CUJ"
assert 'scripts/check-android-performance-slo.py "$JSON" --mode release' in physical_runner, "physical Release must use 40/80 Release mode"
assert 'androidx.benchmark.suppressErrors EMULATOR' not in physical_runner, "physical Release gate must never suppress emulator errors"
assert 'runs-on: [self-hosted, android, physical]' in physical_workflow
assert 'workflow_dispatch:' in physical_workflow
assert 'run-android-physical-release-performance.sh' in physical_workflow

# The generated evidence is curated into compact product assets. Startup stays intentionally narrow.
assert product_baseline_path.is_file() and product_startup_path.is_file(), "product Baseline/Startup Profile assets missing"
baseline = product_baseline_path.read_text(encoding="utf-8")
startup = product_startup_path.read_text(encoding="utf-8")
assert baseline.strip() and startup.strip()
for marker in (
    "Lcom/junchen/jingdu/ReaderScreenKt;",
    "Lcom/junchen/jingdu/ReaderQuickPanelsKt;",
    "Lcom/junchen/jingdu/ReaderSmartChaptersPanelKt;",
    "Lcom/junchen/jingdu/ReaderFastTextKt;",
    "Lcom/junchen/jingdu/ReaderHotControlsKt;",
    "Lcom/junchen/jingdu/ReaderHotPanelCanvasKt;",
    "Landroidx/compose/foundation/text/**",
    "Landroidx/compose/ui/text/**",
    "Landroidx/compose/ui/layout/**",
    "Landroidx/compose/foundation/CanvasKt;",
    "Landroidx/compose/foundation/gestures/**",
    "Landroidx/compose/foundation/layout/**",
    "Landroidx/compose/material3/ButtonKt;",
    "Landroidx/compose/material3/IconButtonKt;",
    "Landroidx/compose/material3/IconKt;",
):
    assert marker in baseline, f"baseline hot path missing: {marker}"
for marker in (
    "Lcom/junchen/jingdu/MainActivity;",
    "Lcom/junchen/jingdu/JingduAppKt;",
    "Lcom/junchen/jingdu/LibraryScreenKt;",
    "Lcom/junchen/jingdu/ReaderScreenKt;",
):
    assert marker in startup, f"startup funnel missing: {marker}"
for forbidden in ("ReaderQuickPanelsKt", "ReaderSmartChaptersPanelKt", "foundation/lazy", "continuous"):
    assert forbidden not in startup, f"runtime-only Startup Profile rule retained: {forbidden}"
assert len(startup.splitlines()) < len(baseline.splitlines()), "Startup Profile must remain a strict compact subset"

# Provenance is evidence for the exact revision family, not a verifier hard-coded to one historical run.
assert provenance_path.is_file(), "profile provenance missing"
provenance = provenance_path.read_text(encoding="utf-8")
assert re.search(r"source head: `?[0-9a-f]{40}`?", provenance), "profile provenance source head missing"
assert re.search(r"run `?[0-9]{8,}`?", provenance), "profile provenance CI run missing"
profile_evidence = re.findall(r"generated (?:baseline|startup) source: ([0-9,]+) rules, ([0-9,]+) bytes, SHA-256 `([0-9a-f]{64})`", provenance)
assert len(profile_evidence) == 2, "baseline/startup profile provenance evidence must include rules, bytes and SHA-256"

perf_job = workflow[workflow.index("  android-performance:"):workflow.index("  harmony-contract:")]
assert "runs-on: ubuntu-22.04" in perf_job, "hosted performance image must be pinned"
assert "Preserve Macrobenchmark evidence and failure Perfetto traces" in perf_job

print("Reader Baseline/Startup Profile contract OK")
