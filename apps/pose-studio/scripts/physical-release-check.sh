#!/usr/bin/env bash
set -euo pipefail
SOURCE_REF="${1:?source ref required}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/evidence/physical"
mkdir -p "$OUT"
command -v adb >/dev/null
adb wait-for-device
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]; then
  echo "Physical qualification refuses emulator evidence" >&2
  exit 1
fi
SHA="$(git -C "$ROOT/../.." rev-parse HEAD)"
{
  echo "source_ref=$SOURCE_REF"
  echo "source_sha=$SHA"
  echo "manufacturer=$(adb shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "fingerprint=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
} | tee "$OUT/device.txt"
cd "$ROOT/android"
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease bundleRelease assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.junchen.posestudio.PoseStudioPerformanceTest
: > "$OUT/startup-ms.txt"
for _ in 1 2 3 4 5; do
  adb shell am force-stop com.junchen.posestudio
  adb shell am start -W -n com.junchen.posestudio/.MainActivity | awk -F: '/TotalTime/ {gsub(/ /, "", $2); print $2}' | tee -a "$OUT/startup-ms.txt"
done
python3 - "$OUT/startup-ms.txt" <<'PY'
import sys
values=sorted(float(x.strip()) for x in open(sys.argv[1]) if x.strip())
if len(values) < 5: raise SystemExit("missing startup samples")
p95=values[-1]
print(f"startup_p95_ms={p95:.1f}")
if p95 >= 1000: raise SystemExit(f"cold startup gate failed: {p95:.1f}ms")
PY
sha256sum app/build/outputs/apk/release/* app/build/outputs/bundle/release/* > "$OUT/artifacts.sha256"
