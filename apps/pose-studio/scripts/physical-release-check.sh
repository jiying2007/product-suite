#!/usr/bin/env bash
set -euo pipefail
SOURCE_REF="${1:?source ref required}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
OUT="$ROOT/evidence/physical"
rm -rf "$OUT"
mkdir -p "$OUT"

command -v adb >/dev/null
adb wait-for-device
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]; then
  echo "Physical qualification refuses emulator evidence" >&2
  exit 1
fi

SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
EXPECTED_SHA="$(git -C "$REPO_ROOT" rev-parse "${SOURCE_REF}^{commit}")"
if [[ "$SHA" != "$EXPECTED_SHA" ]]; then
  echo "Checked-out source $SHA does not match requested candidate $SOURCE_REF ($EXPECTED_SHA)" >&2
  exit 1
fi

{
  echo "source_ref=$SOURCE_REF"
  echo "source_sha=$SHA"
  echo "manufacturer=$(adb shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "fingerprint=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "refresh_rate=$(adb shell dumpsys display | sed -n 's/.*mRefreshRate=\([0-9.]*\).*/\1/p' | head -1 | tr -d '\r')"
} | tee "$OUT/device.txt"

cd "$ROOT/android"

# The exact release candidate must be buildable with its own production/upload signing material.
./gradlew --no-daemon \
  :app:validatePoseReleaseSigning \
  :app:testDebugUnitTest \
  :app:lintRelease \
  :app:assembleRelease \
  :app:bundleRelease \
  :app:assembleBenchmark \
  :macrobenchmark:assembleBenchmark

# Keep the broad in-process regression sentinels, but do not treat them as release UI evidence.
./gradlew --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.junchen.posestudio.PoseStudioPerformanceTest

# Macrobenchmark targets a non-debuggable, minified build cloned from release and signed only with
# the standard debug key so a qualification runner can install it. Production AAB provenance is
# captured separately below from the release build signed by the product-specific key.
rm -rf macrobenchmark/build/outputs/connected_android_test_additional_output
./gradlew --no-daemon :macrobenchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark

mapfile -d '' BENCHMARK_FILES < <(find macrobenchmark/build/outputs -type f \
  \( -name '*benchmarkData.json' -o -name '*.perfetto-trace' -o -name '*.trace' \) -print0)
if [[ ${#BENCHMARK_FILES[@]} -eq 0 ]]; then
  echo "Macrobenchmark produced no JSON/trace evidence" >&2
  exit 1
fi
for file in "${BENCHMARK_FILES[@]}"; do
  cp "$file" "$OUT/$(basename "$file")"
done

python3 - "$OUT" <<'PY'
import glob
import json
import math
import os
import sys

out = sys.argv[1]
reports = glob.glob(os.path.join(out, "*benchmarkData.json"))
if not reports:
    raise SystemExit("missing Macrobenchmark benchmarkData JSON")

benchmarks = []
for path in reports:
    with open(path, encoding="utf-8") as handle:
        benchmarks.extend(json.load(handle).get("benchmarks", []))

def named(name):
    matches = [item for item in benchmarks if item.get("name") == name]
    if not matches:
        raise SystemExit(f"missing Macrobenchmark result: {name}")
    return matches[-1]

def percentile(values, q):
    values = sorted(float(v) for v in values)
    if not values:
        raise SystemExit("empty metric sample set")
    index = max(0, math.ceil(q * len(values)) - 1)
    return values[index]

startup = named("coldStartup")
startup_runs = startup.get("metrics", {}).get("timeToInitialDisplayMs", {}).get("runs", [])
startup_p95 = percentile(startup_runs, 0.95)

interaction = named("directManipulationFrames")
frame_metric = interaction.get("sampledMetrics", {}).get("frameDurationCpuMs", {})
frame_runs = [sample for iteration in frame_metric.get("runs", []) for sample in iteration]
frame_p95 = float(frame_metric.get("P95", percentile(frame_runs, 0.95)))
frame_p99 = float(frame_metric.get("P99", percentile(frame_runs, 0.99)))

summary = (
    f"cold_start_p95_ms={startup_p95:.3f}\n"
    f"direct_manipulation_frame_cpu_p95_ms={frame_p95:.3f}\n"
    f"direct_manipulation_frame_cpu_p99_ms={frame_p99:.3f}\n"
)
print(summary, end="")
with open(os.path.join(out, "macrobenchmark-summary.txt"), "w", encoding="utf-8") as handle:
    handle.write(summary)

if startup_p95 >= 1000.0:
    raise SystemExit(f"cold startup gate failed: P95 {startup_p95:.3f} ms >= 1000 ms")
if frame_p95 >= 16.7:
    raise SystemExit(f"direct manipulation frame gate failed: P95 {frame_p95:.3f} ms >= 16.7 ms")
if frame_p99 >= 33.4:
    raise SystemExit(f"direct manipulation tail-frame gate failed: P99 {frame_p99:.3f} ms >= 33.4 ms")
PY

sha256sum \
  app/build/outputs/apk/release/*.apk \
  app/build/outputs/bundle/release/*.aab \
  app/build/outputs/apk/benchmark/*.apk \
  macrobenchmark/build/outputs/apk/benchmark/*.apk \
  > "$OUT/artifacts.sha256"

APKSIGNER="$(command -v apksigner || true)"
if [[ -z "$APKSIGNER" && -n "${ANDROID_HOME:-}" ]]; then
  APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -1)"
fi
if [[ -z "$APKSIGNER" ]]; then
  echo "apksigner is required to preserve signing provenance" >&2
  exit 1
fi
"$APKSIGNER" verify --verbose --print-certs app/build/outputs/apk/release/*.apk \
  | tee "$OUT/release-signing-cert.txt"
