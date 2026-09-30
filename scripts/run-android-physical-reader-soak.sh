#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/jingdu/android"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
ADB="${ADB:-$SDK_ROOT/platform-tools/adb}"
TARGET_PACKAGE="com.junchen.jingdu"
TEST_PACKAGE="com.junchen.jingdu.macrobenchmark"
DURATION_MINUTES="${JINGDU_SOAK_MINUTES:-60}"
SOURCE_REF="${JINGDU_QUALIFIED_SOURCE_REF:-$(git -C "$ROOT" rev-parse HEAD)}"
EXPECTED_SOURCE_SHA="${JINGDU_QUALIFIED_SOURCE_SHA:-}"
ACTUAL_SOURCE_SHA="$(git -C "$ROOT" rev-parse HEAD)"
RESULT_ROOT="$ANDROID_DIR/macrobenchmark/build/outputs/physical-soak"

[[ "$DURATION_MINUTES" == "60" || "$DURATION_MINUTES" == "180" ]] || {
  echo "JINGDU_SOAK_MINUTES must be 60 or 180" >&2
  exit 2
}
[[ -x "$ADB" ]] || { echo "Missing adb: $ADB" >&2; exit 1; }
if [[ -n "$EXPECTED_SOURCE_SHA" && "$EXPECTED_SOURCE_SHA" != "$ACTUAL_SOURCE_SHA" ]]; then
  echo "Physical soak source mismatch: expected=$EXPECTED_SOURCE_SHA actual=$ACTUAL_SOURCE_SHA" >&2
  exit 1
fi

mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1}')
(("${#DEVICES[@]}" == 1)) || { echo "Physical soak requires exactly one authorized device" >&2; "$ADB" devices -l >&2; exit 1; }
export ANDROID_SERIAL="${DEVICES[0]}"
QEMU="$("$ADB" shell getprop ro.kernel.qemu | tr -d '\r')"
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
MANUFACTURER="$("$ADB" shell getprop ro.product.manufacturer | tr -d '\r')"
FINGERPRINT="$("$ADB" shell getprop ro.build.fingerprint | tr -d '\r')"
SDK="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$QEMU" == "1" || "${MODEL,,}" == *emulator* || "${FINGERPRINT,,}" == *generic* ]]; then
  echo "Physical soak refuses emulator/generic device: $MODEL / $FINGERPRINT" >&2
  exit 1
fi

rm -rf "$RESULT_ROOT"
mkdir -p "$RESULT_ROOT"
cat > "$RESULT_ROOT/provenance.txt" <<EOF
source_ref=$SOURCE_REF
source_sha=$ACTUAL_SOURCE_SHA
duration_minutes=$DURATION_MINUTES
manufacturer=$MANUFACTURER
model=$MODEL
sdk=$SDK
fingerprint=$FINGERPRINT
fixture_mib=100
page_input=physical-volume
peak_pss_limit_kb=524288
battery_and_thermal=evidence-only-until-device-normalized-baseline
EOF

"$ADB" shell dumpsys battery > "$RESULT_ROOT/battery-before.txt" || true
"$ADB" shell dumpsys thermalservice > "$RESULT_ROOT/thermal-before.txt" || true
"$ADB" shell logcat -c || true

cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all :app:assembleBenchmark :macrobenchmark:assembleBenchmark
TARGET_APK="$(find "$ANDROID_DIR/app/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
TEST_APK="$(find "$ANDROID_DIR/macrobenchmark/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
[[ -s "$TARGET_APK" && -s "$TEST_APK" ]] || { echo "physical soak APKs missing" >&2; exit 1; }

"$ADB" uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
"$ADB" uninstall "$TARGET_PACKAGE" >/dev/null 2>&1 || true
"$ADB" install "$TARGET_APK"
"$ADB" install "$TEST_APK"
INSTRUMENTATION="$("$ADB" shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\([^ ]*\).*$/\1/p' | grep "$TEST_PACKAGE" | head -n1)"
[[ -n "$INSTRUMENTATION" ]] || { echo "Macrobenchmark instrumentation missing" >&2; exit 1; }

STAY_ON_BEFORE="$("$ADB" shell settings get global stay_on_while_plugged_in | tr -d '\r')"
restore_stay_on() {
  "$ADB" shell settings put global stay_on_while_plugged_in "$STAY_ON_BEFORE" >/dev/null 2>&1 || true
}
trap restore_stay_on EXIT
"$ADB" shell svc power stayon true
"$ADB" shell input keyevent KEYCODE_WAKEUP || true

LOG="$RESULT_ROOT/instrumentation.log"
set +e
"$ADB" shell am instrument -w -r   -e class com.junchen.jingdu.macrobenchmark.PhysicalLongSessionSoakTest   -e jingdu.soakMinutes "$DURATION_MINUTES"   "$INSTRUMENTATION" | tee "$LOG"
STATUS=${PIPESTATUS[0]}
set -e

"$ADB" shell dumpsys battery > "$RESULT_ROOT/battery-after.txt" || true
"$ADB" shell dumpsys thermalservice > "$RESULT_ROOT/thermal-after.txt" || true
FINAL_PID="$("$ADB" shell pidof "$TARGET_PACKAGE" | tr -d '\r' | awk '{print $1}')"
if [[ -n "$FINAL_PID" ]]; then
  "$ADB" shell logcat -d -v threadtime --pid="$FINAL_PID" > "$RESULT_ROOT/reader-logcat.txt" || true
  "$ADB" shell dumpsys meminfo "$TARGET_PACKAGE" > "$RESULT_ROOT/meminfo-after.txt" || true
else
  "$ADB" shell logcat -d -v threadtime > "$RESULT_ROOT/reader-logcat.txt" || true
fi

if (( STATUS != 0 )) || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed' "$LOG"; then
  echo "Physical Reader soak instrumentation failed" >&2
  exit 1
fi

cd "$ROOT"
python3 scripts/check-android-reader-soak.py   "$LOG"   "$RESULT_ROOT/reader-logcat.txt"   --duration-minutes "$DURATION_MINUTES"   --summary-json "$RESULT_ROOT/reader-soak-slo.json"

echo "Physical Reader long-session soak PASS"
