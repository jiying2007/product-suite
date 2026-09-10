# Pose Studio performance contract

Pose Studio optimizes time-to-reference-pose, so responsiveness is a product contract rather than a cosmetic goal.

## CI regression gate

Every Pose Studio PR/main SHA runs API 36 instrumentation on a clean emulator after the build/lint/unit gate. The suite covers product journeys plus broad regression budgets for project encode/decode, render-model construction and bitmap rendering. Emulator timings are regression evidence only.

## Physical release gate

A commercial release candidate must run `.github/workflows/pose-studio-physical-release.yml` on a real device runner. The workflow rejects emulators, records source SHA, manufacturer/model/API/build fingerprint and preserves evidence.

Minimum release targets:

- cold start P95 < 1.0 s;
- direct manipulation targets 60 Hz with no serialization/file IO on pointer paths;
- gesture-to-visible response should remain within one rendered frame under steady state;
- local save/open P95 < 100 ms for the procedural project schema;
- 1440 px PNG render+write P95 < 1.0 s on a representative mid-range device;
- no OOM/ANR during repeated edit/save/export journeys.

Before v1.0, expand physical evidence to at least one API 26 device, one API 36 device and two OEM families. Record frame P95/P99 using Perfetto/FrameTimeline rather than substituting emulator results.
