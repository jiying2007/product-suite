# Pose Studio performance contract

Pose Studio optimizes time-to-reference-pose, so responsiveness is a product contract rather than a cosmetic goal.

## CI regression gate

Every Pose Studio PR/main SHA runs API 36 instrumentation on a clean emulator after the build/lint/unit gate. Emulator timings are regression sentinels only; they are not substitutes for release-device measurements.

The emulator suite separates work by product path instead of combining unrelated work into one wall-clock assertion:

- steady-state render-model construction warms up first, then measures seven batches of 100 builds; median batch mean must remain below 8 ms/op and P90 batch mean below 16 ms/op;
- project JSON encode/decode is explicitly non-interactive work, warms up independently, then measures seven batches of 20 round-trips; median batch mean must remain below 50 ms/round-trip and P90 below 100 ms/round-trip;
- 720 px bitmap rendering warms up independently and samples five renders; median must remain below 1 s and P90 below 2 s.

Batch statistics deliberately reduce sensitivity to host scheduling and JIT/image changes while preserving hard regression budgets. Serialization/file IO must never be moved onto pointer paths merely because the codec has its own budget.

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
