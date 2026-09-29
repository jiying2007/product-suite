# Performance SLO — Long-form TXT

Jingdu treats large local TXT as a first-class product workload. The performance system intentionally separates deterministic Hosted-CI regression guardrails from physical Release qualification so emulator numbers are never presented as phone performance.

## Qualification corpus

Every release is qualified with deterministic or representative files at four tiers:

| Size | Purpose |
| --- | --- |
| 1–5 MiB | ordinary novel / cold-start path |
| 10 MiB | Android Reader interaction journeys |
| 100 MiB | very-long-book Android soak journey |
| 960 MiB | near-1GiB native bounded-memory/RSS qualification |

The release-device corpus additionally includes UTF-8, GB18030/GBK and Big5 source imports where device evidence is required. Source files are never rewritten in place.

## Android physical Release SLOs

Measure the release-derived minified Benchmark target on the physical device classes listed in `DEVICE_MATRIX.md`. Report median and P95 where the tool supports repeated measurements.

| Metric | Target |
| --- | ---: |
| cold app startup, time to initial display | P95 < 1.0 s |
| unchanged imported book, time to first readable page | P95 < 500 ms |
| new 20 MiB TXT, time to first readable page | < 1.0 s |
| new 100 MiB TXT, time to first readable page | < 2.0 s |
| chapter jump with active index | P95 < 100 ms |
| indexed exact search, ordinary query | P95 < 100 ms |
| Smart Clean scan, 20 MiB | < 1.0 s target |
| Smart Clean scan, 100 MiB | < 3.0 s target |
| TTS next-chunk scheduling | P95 < 150 ms |
| 200 MiB open/search/Clean qualification | no OOM / ANR |

The Reader frame-tail product SLO is independently enforced for every frame-producing journey:

| Physical Release frame metric | Product SLO |
| --- | ---: |
| `frameDurationCpuMs` P95 | **<= 40 ms** |
| `frameDurationCpuMs` P99 | **<= 80 ms** |

Required evidence floors are unchanged: `pageTurn10MiB >= 20`, `continuousScroll10MiB >= 500`, and `chaptersAndSettings10MiB >= 50` CPU-frame samples.

Physical qualification is executed by `scripts/run-android-physical-release-performance.sh` through `.github/workflows/android-physical-release-performance.yml`. The workflow requires a self-hosted runner labeled `[self-hosted, android, physical]`; the script rejects QEMU/generic emulator devices and forces `jingdu.pageTurnInput=physical-volume`. Six real `KEYCODE_VOLUME_DOWN` events must advance the authoritative Reader position.

Physical Release qualification now has seven independent authorities:

- `scripts/check-android-performance-slo.py --mode release` enforces the Reader frame-tail P95/P99 SLO and sample floors from retained AndroidX frame evidence;
- `scripts/check-android-startup-slo.py` enforces `StartupBenchmark.coldStartup` `timeToInitialDisplayMs` P95 < 1.0 s over at least 10 physical cold-start iterations;
- `scripts/check-android-first-readable-slo.py` enforces unchanged imported 10 MiB book Library-click -> authoritative paged position + raster/layout readiness P95 < 500 ms over at least 10 physical samples, and writes `first-readable-slo.json` with source SHA/ref, device identity, raw samples and fixture SHA-256;
- `scripts/check-android-new-import-slo.py` enforces both new-TXT targets through the real `ACTION_VIEW text/plain` -> `MainActivity.importUri()` -> `BookRepository.importUri()` -> first authoritative paged raster-ready path. Source generation and previous-import cleanup stay outside the measured interval. The physical suite retains at least five samples per size and requires every 20 MiB sample < 1.0 s and every 100 MiB sample < 2.0 s. `new-20mib-first-readable-slo.json` and `new-100mib-first-readable-slo.json` each record median/P95/max, raw samples, source/device identity and fixture SHA-256; the checker filters by size-specific metric so one size cannot satisfy the other;
- `scripts/check-android-chapter-jump-slo.py` enforces active-index chapter jump P95 < 100 ms over at least 10 retained physical samples. Each provider call opens the already-imported fixture and warms the chapter index outside the timed interval, then times only the active-index `ReaderController.chapters()` lookup plus `jump(targetOffset)`. Evidence must prove `finalPosition == targetOffset` and retain source/normalized SHA identity in `chapter-jump-slo.json`;
- `scripts/check-android-indexed-search-slo.py` enforces indexed exact search P95 < 100 ms over at least 10 retained physical samples. Each provider call opens the already-imported fixture and prewarms the deterministic exact query outside the timed interval, then times only the second `ReaderController.search()` operation against the active index. Evidence must retain query identity, positive hit count and first-hit offset proof in `indexed-search-slo.json`;
- `scripts/check-android-smart-clean-slo.py` enforces the Smart Clean 20/100 MiB scan targets through the same production `ReaderController.noiseCandidates()` path used by `MainActivity.analyzeSmartClean()`, including native candidates plus streaming `SmartCleanRefiner.scan()`. Benchmark-only noisy fixtures contain deterministic repeated promo/watermark/URL lines so the evidence must prove positive detection work rather than a clean-file fast path. File open/import stays outside the timed interval; at least five retained samples are required per size, every 20 MiB sample must be < 1.0 s and every 100 MiB sample < 3.0 s. `smart-clean-20mib-slo.json` and `smart-clean-100mib-slo.json` retain median/P95/max, raw samples, source/device identity, source/normalized SHA, candidate count and candidate-set SHA-256 proof.

The first-readable journeys and operation-level chapter/search/Smart Clean evidence are physical-only and excluded from Hosted Reader performance CI so they cannot widen or contaminate emulator frame regression authority. Hosted thresholds cannot satisfy any physical gate. The remaining TTS/200 MiB physical Release SLO automation is tracked separately in #134 and must stay fail-closed until dedicated retained physical evidence exists.

Targets are qualification goals for the release matrix, not guarantees across every Android device. A regression must be investigated and recorded rather than hidden by widening the target without evidence.

## Host Core regression gates

`jingdu_core_performance_gate_test` is the fast deterministic hosted-CI guardrail. It checks bounded first open/index, validated `.jdx` reopen, end-of-file full-text search, random bounded reads, coarse RSS and accidental whole-document materialization.

`jingdu_core_near_1gib_rss_gate_test` runs the bounded-access contract with `JINGDU_PERF_FIXTURE_MIB=960`. Its peak RSS ceiling remains below 640 MiB, independent of the 960 MiB source size. This specifically catches accidental whole-document materialization.

Hosted wall-clock ceilings are intentionally loose. Real experience is measured on Android release devices.

## Hosted Android regression gate

Pull requests run the Reader Macrobenchmark suite on Android 35, a Pixel 6 AVD and pinned Ubuntu 22.04 after the normal Android build/test/lint/R8 gate passes. The emulator uses the repository contract `JINGDU_EMULATOR_GPU_MODE:-auto`. It is a stable regression environment, not Release-device performance evidence.

The required journeys are:

1. open a 10 MiB TXT;
2. open a 100 MiB TXT soak fixture;
3. perform six real right-side Reader tap-zone page turns on the 10 MiB fixture;
4. switch to continuous mode and perform six real swipes;
5. execute two Chapters → Back → Aa → Back cycles;
6. independently execute the Reader Baseline/Startup Profile collection contract.

Hosted page-turn intentionally uses the Reader tap zone because API-35 hosted emulator input policy can consume injected hardware volume keys before the foreground Activity. Hardware volume-key behavior remains a product feature and is explicitly covered by the physical Release gate.

### Frozen Hosted baseline

`scripts/reader-hosted-emulator-baseline.json` is frozen from exact-head `fa22d088df7456330244ac4dc2c00a82da888656`, workflow run `33294378785`, performance job `99212107479`, artifact `9727262417` (artifact SHA-256 `c48fbfe3e4daba9c48cba836e67478eb44043abcefb9b1f7cb479684cd1039c6`). This was the first run where all three interaction journeys completed with the authoritative sample floors.

| Journey | Samples | Frozen P95 | Frozen P99 |
| --- | ---: | ---: | ---: |
| `pageTurn10MiB` | 59 | 64.348 ms | 75.029 ms |
| `continuousScroll10MiB` | 687 | 128.868 ms | 155.338 ms |
| `chaptersAndSettings10MiB` | 167 | 135.095 ms | 187.724 ms |

For each Hosted benchmark/percentile, the effective gate is:

`min(frozen baseline × 1.15, absolute Hosted ceiling)`

The absolute Hosted ceilings are:

| Hosted anti-drift metric | Absolute ceiling |
| --- | ---: |
| `frameDurationCpuMs` P95 | **160 ms** |
| `frameDurationCpuMs` P99 | **220 ms** |

These are not product SLOs. With the frozen baseline, the 15% relative limit is tighter than 160/220 for every current journey, so the checked-in baseline remains the effective regression authority. Baseline changes require new exact-head complete evidence and explicit provenance; changing it merely to make a regression green is forbidden.

The Macrobenchmark JSON and Perfetto traces are retained as CI artifacts. Missing/truncated percentile evidence fails the build before threshold comparison.

## Baseline Profile contract

`BaselineProfileGenerator.readerV3CriticalJourneys` covers the Reader runtime hot paths without contaminating Startup Profile:

- real Reader tap-zone page turns;
- Quick Settings and Chapters while the Reader remains in paged mode;
- continuous mode restart followed by real continuous swipes.

Quick/Chapters are intentionally profiled before switching to continuous mode so profile collection does not depend on a cross-mode controls-visibility transition. Hosted profile collection does not use emulator hardware volume keys.

Macrobenchmark frame measurement runs first against the release-derived R8 target using `CompilationMode.Partial(BaselineProfileMode.Require, warmupIterations = 0)`. Only after the Hosted result is frozen does CI swap to the non-minified profile target to collect readable Baseline/Startup HRF evidence. Newly generated rules never feed the same frame measurement.

## Architectural performance invariants

1. The managed UI never owns the whole document as a single `String`.
2. Reader rendering uses bounded windows from the shared Core.
3. Immutable normalized/Clean revisions are content addressed; unchanged content reuses index/cache work.
4. Import, normalization, search, chapters, Clean, re-decode and export never perform file-size-proportional work on the UI thread.
5. First readable page and background indexing are separate product milestones; full-book secondary work must not unnecessarily block reading.
6. Library rendering uses metadata only and never opens every book to draw the grid.
7. A performance cache can always be discarded/rebuilt without changing product identity or user data.
8. Hosted emulator thresholds and physical Release SLOs are separate authorities and cannot substitute for each other.
