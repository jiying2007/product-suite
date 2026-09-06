#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/jingdu/android"
AVD_NAME="jingdu-reader-ci"
TARGET_PACKAGE="com.junchen.jingdu"
TEST_PACKAGE="com.junchen.jingdu.macrobenchmark"
API_LEVEL="${JINGDU_BENCHMARK_API:-35}"
IMAGE="system-images;android-${API_LEVEL};google_apis;x86_64"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
SDKMANAGER="${SDKMANAGER:-$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager}"
AVDMANAGER="${AVDMANAGER:-$SDK_ROOT/cmdline-tools/latest/bin/avdmanager}"
EMULATOR="${EMULATOR:-$SDK_ROOT/emulator/emulator}"
ADB="${ADB:-$SDK_ROOT/platform-tools/adb}"
TEMP_DIR="${RUNNER_TEMP:-/tmp}"
AVD_HOME="${ANDROID_AVD_HOME:-$TEMP_DIR/jingdu-avd-home}"
BOOT_TIMEOUT_SECONDS="${JINGDU_EMULATOR_BOOT_TIMEOUT_SECONDS:-240}"
PERFORMANCE_SETTLE_SECONDS="${JINGDU_PERFORMANCE_SETTLE_SECONDS:-360}"
EMULATOR_LOG="$TEMP_DIR/jingdu-emulator.log"
EMULATOR_PID=""
REMOTE_RESULT_ROOT="/sdcard/Download/jingdu-reader-ci"
RESULT_ROOT="$ANDROID_DIR/macrobenchmark/build/outputs/direct-instrumentation"
HOSTED_BASELINE="$ROOT/scripts/reader-hosted-emulator-baseline.json"
INSTRUMENTATION=""
export ANDROID_AVD_HOME="$AVD_HOME"

cleanup() {
  if [[ -x "$ADB" ]]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
  fi
  if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    kill "$EMULATOR_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

require_executable() {
  local path="$1"
  local label="$2"
  if [[ ! -x "$path" ]]; then
    echo "Missing ${label}: ${path}" >&2
    exit 1
  fi
}

fail_emulator() {
  local message="$1"
  echo "$message" >&2
  if "$ADB" get-state >/dev/null 2>&1; then
    {
      echo
      echo "===== Android performance guest logcat at failure ====="
      "$ADB" shell logcat -d -v threadtime 2>/dev/null | tail -n 800 || true
    } >>"$EMULATOR_LOG"
  fi
  echo "===== Android emulator log =====" >&2
  tail -n 320 "$EMULATOR_LOG" >&2 || true
  exit 1
}

wait_for_android_ready() {
  local timeout_seconds="${1:-120}"
  local second adb_state boot_state
  for ((second = 1; second <= timeout_seconds; second++)); do
    if [[ -n "$EMULATOR_PID" ]] && ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
      return 1
    fi
    adb_state="$("$ADB" get-state 2>/dev/null || true)"
    if [[ "$adb_state" == "device" ]]; then
      boot_state="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      if [[ "$boot_state" == "1" ]] && \
         "$ADB" shell settings get global window_animation_scale >/dev/null 2>&1 && \
         "$ADB" shell pm path android >/dev/null 2>&1 && \
         "$ADB" shell am get-current-user >/dev/null 2>&1; then
        return 0
      fi
    fi
    sleep 1
  done
  return 1
}

wait_for_performance_settle() {
  local second uptime_seconds

  if [[ ! "$PERFORMANCE_SETTLE_SECONDS" =~ ^[0-9]+$ ]] || (( PERFORMANCE_SETTLE_SECONDS < 1 )); then
    fail_emulator "Android performance settle duration must be a positive integer: ${PERFORMANCE_SETTLE_SECONDS}"
  fi

  # Keep the measurement guest fresh, but do not benchmark the first seconds after sys.boot_completed.
  # The checked-in hosted baseline and the last same-code green run were measured after roughly six
  # minutes of healthy post-boot residency. A deterministic 360 s settle preserves that measurement
  # age without keeping the guest alive during host Gradle/R8 work. Core framework services are
  # checked every 15 s so a system_server failure is infrastructure evidence, never a frame result.
  echo "Waiting ${PERFORMANCE_SETTLE_SECONDS}s for fresh Android performance guest post-boot stabilization"
  for ((second = 1; second <= PERFORMANCE_SETTLE_SECONDS; second++)); do
    if [[ -n "$EMULATOR_PID" ]] && ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
      fail_emulator "Android performance emulator exited during post-boot stabilization"
    fi
    if (( second % 15 == 0 || second == PERFORMANCE_SETTLE_SECONDS )); then
      if ! wait_for_android_ready 5; then
        fail_emulator "Android framework health check failed during performance settle at ${second}s/${PERFORMANCE_SETTLE_SECONDS}s"
      fi
      uptime_seconds="$("$ADB" shell cut -d' ' -f1 /proc/uptime 2>/dev/null | tr -d '\r' || true)"
      echo "Android performance settle: ${second}s/${PERFORMANCE_SETTLE_SECONDS}s guest_uptime=${uptime_seconds:-unknown}s"
    fi
    sleep 1
  done

  if ! wait_for_android_ready 15; then
    fail_emulator "Android framework services were not healthy after performance settle"
  fi
  uptime_seconds="$("$ADB" shell cut -d' ' -f1 /proc/uptime 2>/dev/null | tr -d '\r' || true)"
  echo "Android performance guest stabilized after ${PERFORMANCE_SETTLE_SECONDS}s post-boot settle: guest_uptime=${uptime_seconds:-unknown}s"
}

resolve_instrumentation() {
  INSTRUMENTATION="$("$ADB" shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\([^ ]*\).*$/\1/p' | grep 'com.junchen.jingdu.macrobenchmark' | head -n 1)"
  if [[ -z "$INSTRUMENTATION" ]]; then
    echo "Macrobenchmark instrumentation component was not registered" >&2
    "$ADB" shell pm list instrumentation >&2 || true
    return 1
  fi
  echo "Macrobenchmark instrumentation: $INSTRUMENTATION"
}

install_pair() {
  local label="$1"
  local target_apk="$2"
  local test_apk="$3"
  if ! wait_for_android_ready 120; then
    fail_emulator "Android guest is unavailable before ${label} target installation"
  fi
  "$ADB" uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
  "$ADB" uninstall "$TARGET_PACKAGE" >/dev/null 2>&1 || true
  INSTRUMENTATION=""
  echo "Installing ${label} target: $target_apk"
  "$ADB" install "$target_apk"
  echo "Installing ${label} tests: $test_apk"
  "$ADB" install "$test_apk"
  local target_path
  target_path="$("$ADB" shell pm path "$TARGET_PACKAGE" 2>/dev/null | tr -d '\r')"
  if [[ "$target_path" != package:* ]]; then
    echo "${label} target package is not installed: $TARGET_PACKAGE" >&2
    "$ADB" shell pm list packages | grep 'com.junchen.jingdu' >&2 || true
    return 1
  fi
  echo "${label} target installed: $target_path"
  resolve_instrumentation
}

run_instrumentation() {
  local rule="$1"
  local remote_dir="$2"
  local log_file="$3"
  local test_class="${4:-}"
  local class_args=()
  if [[ -n "$test_class" ]]; then
    class_args=(-e class "$test_class")
    echo "Instrumentation class filter: $test_class"
  fi
  "$ADB" shell rm -rf "$remote_dir"
  "$ADB" shell mkdir -p "$remote_dir"

  set +e
  "$ADB" shell am instrument -w -r \
    -e no-isolated-storage true \
    -e additionalTestOutputDir "$remote_dir" \
    -e androidx.benchmark.suppressErrors EMULATOR \
    -e listener androidx.benchmark.macro.junit4.SideEffectRunListener \
    -e androidx.benchmark.enabledRules "$rule" \
    "${class_args[@]}" \
    "$INSTRUMENTATION" | tee "$log_file"
  local status=${PIPESTATUS[0]}
  set -e

  if (( status != 0 )) || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=Process crashed|Process crashed|System has crashed' "$log_file" || ! grep -q 'INSTRUMENTATION_CODE: -1' "$log_file"; then
    echo "Reader ${rule} instrumentation failed" >&2
    cat "$log_file" >&2
    return 1
  fi
}

preserve_failed_macro_evidence() {
  local remote_dir="$1"
  local local_dir="$RESULT_ROOT/macro-failure-evidence"
  mkdir -p "$local_dir"
  echo "Preserving Macrobenchmark failure traces from ${remote_dir}"
  "$ADB" shell "ls -lah $remote_dir" || true
  "$ADB" pull "$remote_dir" "$local_dir/" || true
  if "$ADB" get-state >/dev/null 2>&1; then
    "$ADB" shell logcat -d -v threadtime 2>/dev/null | tail -n 800 >"$local_dir/guest-logcat-tail.txt" || true
  fi
  find "$local_dir" -type f \( -name '*.perfetto-trace' -o -name '*-benchmarkData.json' -o -name 'guest-logcat-tail.txt' \) -print || true
}

cd "$ROOT"
python3 scripts/test-android-performance-slo.py
test -s "$HOSTED_BASELINE"
require_executable "$SDKMANAGER" sdkmanager
require_executable "$AVDMANAGER" avdmanager

echo "Android SDK root: $SDK_ROOT"
echo "Benchmark image: $IMAGE"
echo "Android AVD home: $AVD_HOME"

yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "platform-tools" "emulator" "$IMAGE"
require_executable "$ADB" adb
require_executable "$EMULATOR" emulator

if ! dpkg-query -W libpulse0 >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends libpulse0
fi

"$ADB" version
"$EMULATOR" -version

if [[ -e /dev/kvm ]]; then
  sudo chmod 666 /dev/kvm || true
  echo "KVM acceleration available"
else
  echo "KVM unavailable; using software acceleration fallback"
fi

# Build every APK before starting the benchmark guest. Keeping an Android 15 emulator alive while
# Gradle/KSP/CMake/R8 run for several minutes has produced system_server crashes before the first
# benchmark case, yielding no performance evidence. Building first preserves the exact benchmark
# image/RAM/GPU/SLO/baseline while ensuring measurement starts on a fresh guest.
cd "$ANDROID_DIR"
./gradlew --no-daemon --warning-mode all \
  :app:assembleBenchmark :macrobenchmark:assembleBenchmark \
  :app:assembleProfile :macrobenchmark:assembleProfile
BENCHMARK_TARGET_APK="$(find "$ANDROID_DIR/app/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
BENCHMARK_TEST_APK="$(find "$ANDROID_DIR/macrobenchmark/build/outputs/apk/benchmark" -type f -name '*.apk' -print -quit)"
PROFILE_TARGET_APK="$(find "$ANDROID_DIR/app/build/outputs/apk/profile" -type f -name '*.apk' -print -quit)"
PROFILE_TEST_APK="$(find "$ANDROID_DIR/macrobenchmark/build/outputs/apk/profile" -type f -name '*.apk' -print -quit)"
for pair in \
  "benchmark target:$BENCHMARK_TARGET_APK" \
  "benchmark test:$BENCHMARK_TEST_APK" \
  "profile target:$PROFILE_TARGET_APK" \
  "profile test:$PROFILE_TEST_APK"; do
  label="${pair%%:*}"
  path="${pair#*:}"
  if [[ -z "$path" || ! -f "$path" ]]; then
    echo "${label} APK was not produced" >&2
    exit 1
  fi
done
cd "$ROOT"
echo "Performance APKs built before emulator start; launching fresh measurement guest"

rm -rf "$AVD_HOME"
mkdir -p "$AVD_HOME"
echo no | "$AVDMANAGER" create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$IMAGE" \
  --device "pixel_6" \
  --path "$AVD_HOME/$AVD_NAME.avd"

echo "Available AVDs:"
"$EMULATOR" -list-avds
if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
  echo "AVD metadata after creation:" >&2
  find "$AVD_HOME" -maxdepth 2 -type f -print >&2 || true
  exit 1
fi

GPU_MODE="${JINGDU_EMULATOR_GPU_MODE:-auto}"
: >"$EMULATOR_LOG"
if [[ -e /dev/kvm ]]; then
  "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
    -camera-back none -camera-front none -gpu "$GPU_MODE" >"$EMULATOR_LOG" 2>&1 &
else
  "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
    -camera-back none -camera-front none -gpu "$GPU_MODE" -accel off >"$EMULATOR_LOG" 2>&1 &
fi
EMULATOR_PID=$!
echo "Android emulator PID: $EMULATOR_PID"
"$ADB" start-server >/dev/null

booted=0
for ((second = 1; second <= BOOT_TIMEOUT_SECONDS; second++)); do
  if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
    wait "$EMULATOR_PID" || true
    fail_emulator "Android emulator process exited before boot completed"
  fi

  adb_state="$("$ADB" get-state 2>/dev/null || true)"
  if [[ "$adb_state" == "device" ]]; then
    boot_state="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot_state" == "1" ]]; then
      booted=1
      echo "Android emulator boot completed in ${second}s"
      break
    fi
  fi

  if (( second % 15 == 0 )); then
    echo "Waiting for Android emulator boot: ${second}s/${BOOT_TIMEOUT_SECONDS}s (adb=${adb_state:-unavailable})"
  fi
  sleep 1
done

if (( booted == 0 )); then
  fail_emulator "Android emulator did not finish booting within ${BOOT_TIMEOUT_SECONDS}s"
fi
if ! wait_for_android_ready 120; then
  fail_emulator "Android framework services were not ready after benchmark guest boot"
fi

"$ADB" shell input keyevent 82 || true
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
immersive_confirmation="$("$ADB" shell settings get secure immersive_mode_confirmations | tr -d '\r')"
if [[ "$immersive_confirmation" != "confirmed" ]]; then
  echo "Android immersive-mode confirmation was not suppressed: ${immersive_confirmation:-<empty>}" >&2
  exit 1
fi
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell getprop ro.build.version.release
"$ADB" shell getprop ro.product.cpu.abi
wait_for_performance_settle

# Stage 1: production-like R8 target. The hosted result is frozen before any generated profile exists.
install_pair "R8 Macrobenchmark" "$BENCHMARK_TARGET_APK" "$BENCHMARK_TEST_APK"
rm -rf "$RESULT_ROOT"
mkdir -p "$RESULT_ROOT/macro" "$RESULT_ROOT/profile"
"$ADB" shell rm -rf "$REMOTE_RESULT_ROOT"
"$ADB" shell mkdir -p "$REMOTE_RESULT_ROOT"

MACRO_REMOTE="$REMOTE_RESULT_ROOT/macro"
MACRO_CLASS="com.junchen.jingdu.macrobenchmark.ReaderJourneyBenchmark"
if ! run_instrumentation Macrobenchmark "$MACRO_REMOTE" "$RESULT_ROOT/macro-instrumentation.log" "$MACRO_CLASS"; then
  preserve_failed_macro_evidence "$MACRO_REMOTE"
  fail_emulator "Reader Macrobenchmark instrumentation failed on fresh measurement guest"
fi
REMOTE_JSON="$("$ADB" shell "ls -1 $MACRO_REMOTE/*-benchmarkData.json 2>/dev/null | head -n 1" | tr -d '\r')"
if [[ -z "$REMOTE_JSON" ]]; then
  echo "Macrobenchmark completed without benchmarkData.json" >&2
  "$ADB" shell "ls -lah $MACRO_REMOTE" >&2 || true
  preserve_failed_macro_evidence "$MACRO_REMOTE"
  exit 1
fi
"$ADB" pull "$REMOTE_JSON" "$RESULT_ROOT/macro/"

cd "$ROOT"
set +e
python3 scripts/check-android-performance-slo.py \
  "$RESULT_ROOT/macro" \
  --mode hosted-regression \
  --baseline "$HOSTED_BASELINE"
SLO_STATUS=$?
set -e
find "$RESULT_ROOT/macro" -type f -name '*-benchmarkData.json' -print -quit | grep -q .

# A red hosted regression is a product result. Preserve its traces before any later Profile failure
# can terminate the shell; install_pair below re-validates the still-live guest before swapping APKs.
if (( SLO_STATUS != 0 )); then
  preserve_failed_macro_evidence "$MACRO_REMOTE"
fi

# Stage 2: only after the shipped-profile R8 result is frozen, swap to a separate non-minified target
# so Baseline/Startup HRF method names remain readable. Generated profile data never feeds Stage 1.
echo "Switching from R8 Macrobenchmark target to non-minified Profile target"
install_pair "Profile collection" "$PROFILE_TARGET_APK" "$PROFILE_TEST_APK"
PROFILE_REMOTE="$REMOTE_RESULT_ROOT/profile"
PROFILE_CLASS="com.junchen.jingdu.macrobenchmark.BaselineProfileGenerator"
run_instrumentation BaselineProfile "$PROFILE_REMOTE" "$RESULT_ROOT/profile-instrumentation.log" "$PROFILE_CLASS"
PROFILE_RAW="$RESULT_ROOT/profile/raw"
rm -rf "$PROFILE_RAW"
mkdir -p "$PROFILE_RAW"
"$ADB" pull "$PROFILE_REMOTE" "$PROFILE_RAW/"
mapfile -d '' BASELINE_FILES < <(find "$PROFILE_RAW" -type f -name '*baseline-prof.txt' -print0 | sort -z)
mapfile -d '' STARTUP_FILES < <(find "$PROFILE_RAW" -type f -name '*startup-prof.txt' -print0 | sort -z)
if ((${#BASELINE_FILES[@]} == 0 || ${#STARTUP_FILES[@]} == 0)); then
  echo "Baseline Profile journey did not produce baseline and startup profile sources" >&2
  find "$PROFILE_RAW" -type f -maxdepth 4 -print >&2 || true
  exit 1
fi
cat "${BASELINE_FILES[@]}" | sed '/^[[:space:]]*$/d' | sort -u > "$RESULT_ROOT/profile/baseline-prof.txt"
cat "${STARTUP_FILES[@]}" | sed '/^[[:space:]]*$/d' | sort -u > "$RESULT_ROOT/profile/startup-prof.txt"
test -s "$RESULT_ROOT/profile/baseline-prof.txt"
test -s "$RESULT_ROOT/profile/startup-prof.txt"
echo "Canonical Reader baseline rules: $(wc -l < "$RESULT_ROOT/profile/baseline-prof.txt")"
echo "Canonical Reader startup rules: $(wc -l < "$RESULT_ROOT/profile/startup-prof.txt")"

if (( SLO_STATUS != 0 )); then
  exit "$SLO_STATUS"
fi
