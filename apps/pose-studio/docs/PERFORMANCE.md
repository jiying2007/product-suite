# Pose Studio performance contract

Pose Studio optimizes time-to-reference-pose, so responsiveness is a product contract rather than a cosmetic goal.

## CI regression gate

Every Pose Studio PR/main SHA runs API 36 instrumentation on a clean emulator after the build/lint/unit gate. Emulator timings are regression sentinels only; they are not substitutes for release-device measurements.

The emulator suite separates work by product path instead of combining unrelated work into one wall-clock assertion:

- steady-state render-model construction warms up first, then measures seven batches of 100 builds; median batch mean must remain below 8 ms/op and P90 batch mean below 16 ms/op;
- project JSON encode/decode is explicitly non-interactive work, warms up independently, then measures seven batches of 20 round-trips; median batch mean must remain below 50 ms/round-trip and P90 below 100 ms/round-trip;
- 720 px bitmap rendering warms up independently and samples five renders; median must remain below 1 s and P90 below 2 s.

Batch statistics deliberately reduce sensitivity to host scheduling and JIT/image changes while preserving hard regression budgets. Serialization/file IO must never be moved onto pointer paths merely because the codec has its own budget.

Normal `poseStudioCheck` also compiles the app's `benchmark` variant and the separate `:macrobenchmark` test APK. It deliberately does not execute Macrobenchmark on a hosted emulator because emulator performance is not release evidence. The repository check also syntax-checks the physical qualification script and requires that it invoke the benchmark variant explicitly rather than a broad connected-test aggregate.

## Release-like benchmark variant

The app has a `benchmark` build type that inherits `release`: R8/minification and resource shrinking remain enabled and the app is non-debuggable. The only intentional installability difference is local debug signing so a qualification runner can install the exact code under test. A benchmark-only manifest makes that variant profileable by shell; the production `release` manifest remains unaffected.

The separate `:macrobenchmark` module uses AndroidX Macrobenchmark and UI Automator to measure:

- ten cold starts with `StartupTimingMetric`;
- repeated direct wrist manipulation with `FrameTimingMetric`, producing raw frame distributions and Perfetto traces.

Both journeys use AndroidX `CompilationMode.DEFAULT`. On supported Android versions this represents the default fresh-install compilation policy: a Baseline Profile is used only if one is actually packaged in a future candidate. The current presence of ProfileInstaller is a Macrobenchmark/runtime prerequisite and is not itself evidence that Pose Studio ships a Baseline Profile.

Macrobenchmark JSON and trace files are immutable release evidence, not substitutes for the production AAB/signing provenance built in the same qualification run.

## Physical release gate

A commercial release candidate must run `.github/workflows/pose-studio-physical-release.yml` on a real device runner. The workflow rejects emulators, verifies that the requested immutable ref resolves to the checked-out SHA, records manufacturer/model/API/build fingerprint, and preserves evidence.

The physical script is fail-closed for product-specific release signing: it runs `validatePoseReleaseSigning`, builds the signed release APK/AAB, records SHA-256 checksums and the release signing certificate, then runs exactly `:macrobenchmark:connectedBenchmarkAndroidTest` against the release-like benchmark variant.

Current automated physical thresholds are:

- cold start `timeToInitialDisplayMs` P95 < 1.0 s, with at least 10 startup samples;
- direct-manipulation `frameDurationCpuMs` P95 < 16.7 ms;
- direct-manipulation `frameDurationCpuMs` P99 < 33.4 ms, with at least 100 frame samples.

P95/P99 are recomputed from raw AndroidX `runs` samples using the same linear interpolation semantics as AndroidX MetricResult rather than trusting a separately rendered summary. These thresholds express the 60 Hz interaction target while still making tail-frame regressions visible. AndroidX `FrameTimingMetric` also preserves `frameOverrunMs` where the platform supports it; inspect the trace rather than hiding a passing percentile behind severe isolated jank.

Additional v1 release targets remain mandatory and need representative physical journey evidence before the corresponding issue rows can close:

- gesture-to-visible response should remain within one rendered frame under steady state;
- local save/open P95 < 100 ms for the procedural project schema;
- 1440 px PNG render+write P95 < 1.0 s on a representative mid-range device;
- no OOM/ANR during repeated edit/save/export journeys.

Before v1.0, qualification must cover at least one API 26 device, one API 36 device and two OEM families. Do not replace that matrix with hosted-emulator results or a single flagship phone.
