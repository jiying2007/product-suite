#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/jingdu/android"
TARGET_PACKAGE="com.junchen.jingdu"
TEST_PACKAGE="com.junchen.jingdu.macrobenchmark"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
ADB="${ADB:-$SDK_ROOT/platform-tools/adb}"
REMOTE_ROOT="/sdcard/Download/jingdu-reader-physical-release"
RESULT_ROOT="$ANDROID_DIR/macrobenchmark/build/outputs/physical-release"
INSTRUMENTATION=""
ORIGINAL_WINDOW_SCALE=""
ORIGINAL_TRANSITION_SCALE=""
ORIGINAL_ANIMATOR_SCALE=""

restore_animation_scales() {
  [[ -x "$ADB" ]] || return 0
  [[ -n "$ORIGINAL_WINDOW_SCALE" ]] && "$ADB" shell settings put global window_animation_scale "$ORIGINAL_WINDOW_SCALE" >/dev/null 2>&1 || true
  [[ -n "$ORIGINAL_TRANSITION_SCALE" ]] && "$ADB" shell settings put global transition_animation_scale "$ORIGINAL_TRANSITION_SCALE" >/dev/null 2>&1 || true
  [[ -n "$ORIGINAL_ANIMATOR_SCALE" ]] && "$ADB" shell settings put global animator_duration_scale "$ORIGINAL_ANIMATOR_SCALE" >/dev/null 2>&1 || true
}
trap restore_animation_scales EXIT

[[ -x "$ADB" ]] || { echo "Missing adb: $ADB" >&2; exit 1; }

ACTUAL_SOURCE_SHA="$(git -C "$ROOT" rev-parse HEAD)"
EXPECTED_SOURCE_SHA="${JINGDU_QUALIFIED_SOURCE_SHA:-}"
SOURCE_REF="${JINGDU_QUALIFIED_SOURCE_REF:-$ACTUAL_SOURCE_SHA}"
if [[ -n "$EXPECTED_SOURCE_SHA" && "$ACTUAL_SOURCE_SHA" != "$EXPECTED_SOURCE_SHA" ]]; then
  echo "Physical Release source mismatch: expected=$EXPECTED_SOURCE_SHA actual=$ACTUAL_SOURCE_SHA" >&2
  exit 1
fi

mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1}')
if ((${#DEVICES[@]} != 1)); then
  echo "Physical Release gate requires exactly one authorized adb device; found ${#DEVICES[@]}" >&2
  "$ADB" devices -l >&2
  exit 1
fi
export ANDROID_SERIAL="${DEVICES[0]}"

QEMU="$("$ADB" shell getprop ro.kernel.qemu | tr -d '\r')"
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
MANUFACTURER="$("$ADB" shell getprop ro.product.manufacturer | tr -d '\r')"
FINGERPRINT="$("$ADB" shell getprop ro.build.fingerprint | tr -d '\r')"
SDK="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
MODEL_LOWER="${MODEL,,}"
FINGERPRINT_LOWER="${FINGERPRINT,,}"
if [[ "$QEMU" == "1" || "$MODEL_LOWER" == *emulator* || "$MODEL_LOWER" == *sdk_gphone* || "$FINGERPRINT_LOWER" == *generic* ]]; then
  echo "Physical Release gate refuses emulator/generic devices: model=$MODEL fingerprint=$FINGERPRINT qemu=$QEMU" >&2
  exit 1
fi

echo "Physical Release source: ref=$SOURCE_REF sha=$ACTUAL_SOURCE_SHA"
echo "Physical Release device: serial=$ANDROID_SERIAL manufacturer=$MANUFACTURER model=$MODEL sdk=$SDK"
echo "Physical Release fingerprint: $FINGERPRINT"

ORIGINAL_WINDOW_SCALE="$("$ADB" shell settings get global window_animation_scale | tr -d '\r')"
ORIGINAL_TRANSITION_SCALE="$("$ADB" shell settings get global transition_animation_scale | tr -d '\r')"
ORIGINAL_ANIMATOR_SCALE="$("$ADB" shell settings get global animator_duration_scale | tr -d '\r')"
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0

cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all :app:assembleBenchmark :macrobenchmark:assembleBenchmark
TARGET_APK="$(find "$ANDROID_DIR/app/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
TEST_APK="$(find "$ANDROID_DIR/macrobenchmark/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
[[ -n "$TARGET_APK" && -f "$TARGET_APK" ]] || { echo "Benchmark target APK missing" >&2; exit 1; }
[[ -n "$TEST_APK" && -f "$TEST_APK" ]] || { echo "Macrobenchmark APK missing" >&2; exit 1; }

"$ADB" uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
"$ADB" uninstall "$TARGET_PACKAGE" >/dev/null 2>&1 || true
"$ADB" install "$TARGET_APK"
"$ADB" install "$TEST_APK"
INSTRUMENTATION="$("$ADB" shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\([^ ]*\).*$/\1/p' | grep 'com.junchen.jingdu.macrobenchmark' | head -n 1)"
[[ -n "$INSTRUMENTATION" ]] || { echo "Macrobenchmark instrumentation not registered" >&2; exit 1; }

rm -rf "$RESULT_ROOT"
mkdir -p "$RESULT_ROOT"
cat > "$RESULT_ROOT/provenance.txt" <<EOF
source_ref=$SOURCE_REF
source_sha=$ACTUAL_SOURCE_SHA
manufacturer=$MANUFACTURER
model=$MODEL
sdk=$SDK
fingerprint=$FINGERPRINT
page_turn_input=physical-volume
release_slo_p95_ms=40
release_slo_p99_ms=80
EOF
"$ADB" shell rm -rf "$REMOTE_ROOT"
"$ADB" shell mkdir -p "$REMOTE_ROOT"

LOG="$RESULT_ROOT/instrumentation.log"
set +e
"$ADB" shell am instrument -w -r \
  -e no-isolated-storage true \
  -e additionalTestOutputDir "$REMOTE_ROOT" \
  -e listener androidx.benchmark.macro.junit4.SideEffectRunListener \
  -e androidx.benchmark.enabledRules Macrobenchmark \
  -e class com.junchen.jingdu.macrobenchmark.ReaderJourneyBenchmark \
  -e jingdu.pageTurnInput physical-volume \
  "$INSTRUMENTATION" | tee "$LOG"
STATUS=${PIPESTATUS[0]}
set -e

if (( STATUS != 0 )) || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed|System has crashed' "$LOG" || ! grep -q 'INSTRUMENTATION_CODE: -1' "$LOG"; then
  echo "Physical Release Macrobenchmark instrumentation failed" >&2
  "$ADB" pull "$REMOTE_ROOT" "$RESULT_ROOT/evidence" >/dev/null 2>&1 || true
  exit 1
fi

"$ADB" pull "$REMOTE_ROOT" "$RESULT_ROOT/evidence"
JSON="$(find "$RESULT_ROOT/evidence" -type f -name '*-benchmarkData.json' -print -quit)"
[[ -n "$JSON" && -f "$JSON" ]] || { echo "Physical Release benchmarkData.json missing" >&2; exit 1; }

cd "$ROOT"
# This is the product frame SLO. Never substitute hosted-regression thresholds here.
python3 scripts/check-android-performance-slo.py "$JSON" --mode release

echo "Physical Release Reader frame gate PASS: P95<=40ms P99<=80ms with real VOLUME_DOWN page turns"
