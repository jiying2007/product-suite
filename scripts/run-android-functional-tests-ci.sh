#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/jingdu/android"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$SDK_ROOT/emulator/emulator"
IMAGE="system-images;android-36;google_apis_ps16k;x86_64"
AVD_HOME="${RUNNER_TEMP:-/tmp}/jingdu-functional-avd-home"
AVD_NAME="jingdu-functional-16k-ci"
LOG="${RUNNER_TEMP:-/tmp}/jingdu-functional-emulator.log"
FONT_SCALE_LOG="${RUNNER_TEMP:-/tmp}/jingdu-functional-font-scale-200.log"
BOOT_TIMEOUT_SECONDS="${JINGDU_FUNCTIONAL_BOOT_TIMEOUT_SECONDS:-300}"
FRAMEWORK_TIMEOUT_SECONDS="${JINGDU_FUNCTIONAL_FRAMEWORK_TIMEOUT_SECONDS:-90}"
EMULATOR_MEMORY_MB="${JINGDU_FUNCTIONAL_EMULATOR_MEMORY_MB:-6144}"
MIN_GUEST_MEMORY_KB="${JINGDU_FUNCTIONAL_MIN_GUEST_MEMORY_KB:-5500000}"
EMULATOR_PID=""
CURRENT_PHASE="functional"

stop_emulator() {
  "$ADB" shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  "$ADB" emu kill >/dev/null 2>&1 || true
  if [[ -n "$EMULATOR_PID" ]]; then
    for _ in $(seq 1 30); do
      if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    if kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
      kill "$EMULATOR_PID" >/dev/null 2>&1 || true
    fi
    wait "$EMULATOR_PID" >/dev/null 2>&1 || true
  fi
  EMULATOR_PID=""
  "$ADB" kill-server >/dev/null 2>&1 || true
}

cleanup() {
  stop_emulator
}
trap cleanup EXIT

fail_emulator() {
  echo "$1" >&2
  if "$ADB" get-state >/dev/null 2>&1; then
    {
      echo
      echo "===== Android ${CURRENT_PHASE} guest logcat at failure ====="
      "$ADB" shell logcat -d -v threadtime 2>/dev/null | tail -n 800 || true
    } >>"$LOG"
  fi
  echo "===== Android functional emulator lifecycle log =====" >&2
  tail -n 360 "$LOG" >&2 || true
  if [[ -s "$FONT_SCALE_LOG" ]]; then
    echo "===== Android 200% font-scale instrumentation log =====" >&2
    cat "$FONT_SCALE_LOG" >&2 || true
  fi
  exit 1
}

prepare_pixel_launcher_guard() {
  local phase="$1"
  local result

  # PackageManager is the readiness boundary. Android 15's experimental 16 KiB image exposed a
  # Smartspace crash through NexusLauncher; keep the client isolated when present, but do not require
  # a Pixel launcher package on newer Google APIs images.
  "$ADB" shell pm path android >/dev/null 2>&1 || return 1
  if "$ADB" shell pm path com.google.android.apps.nexuslauncher >/dev/null 2>&1; then
    if ! result="$("$ADB" shell pm disable-user --user 0 com.google.android.apps.nexuslauncher 2>/dev/null | tr -d '\r')"; then
      return 1
    fi
    echo "Android ${phase} NexusLauncher isolated before boot acceptance: ${result:-disabled-user}"
  else
    echo "Android ${phase} NexusLauncher not present; no Smartspace launcher client to isolate"
  fi
  return 0
}

wait_for_framework_services() {
  local phase="$1"
  local context="$2"
  local second

  for ((second = 1; second <= FRAMEWORK_TIMEOUT_SECONDS; second++)); do
    if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
      wait "$EMULATOR_PID" || true
      fail_emulator "Android ${phase} emulator exited while waiting for framework services ${context}"
    fi
    if "$ADB" shell settings get global window_animation_scale >/dev/null 2>&1 && \
       "$ADB" shell pm path android >/dev/null 2>&1 && \
       "$ADB" shell am get-current-user >/dev/null 2>&1; then
      echo "Android ${phase} framework services ready in ${second}s ${context}"
      return 0
    fi
    if (( second % 10 == 0 )); then
      echo "Waiting for Android ${phase} framework services ${context}: ${second}s/${FRAMEWORK_TIMEOUT_SECONDS}s"
    fi
    sleep 1
  done
  fail_emulator "Android ${phase} framework services were not ready within ${FRAMEWORK_TIMEOUT_SECONDS}s ${context}"
}

finalize_test_system_ui() {
  local phase="$1"

  # These settings are emulator-only stability guards. They do not change Jingdu or its tests.
  "$ADB" shell settings put secure smartspace 0 >/dev/null 2>&1 \
    || fail_emulator "Android ${phase} SettingsProvider unavailable while disabling Smartspace"
  "$ADB" shell settings put secure smartspace_show_on_home_screen 0 >/dev/null 2>&1 \
    || fail_emulator "Android ${phase} SettingsProvider unavailable while disabling home Smartspace"
  sleep 3
  "$ADB" shell pm path android >/dev/null 2>&1 \
    || fail_emulator "Android ${phase} PackageManager became unavailable after Smartspace isolation"
  "$ADB" shell am get-current-user >/dev/null 2>&1 \
    || fail_emulator "Android ${phase} ActivityManager became unavailable after Smartspace isolation"
  echo "Android ${phase} hosted system UI stability guards applied"
}

start_emulator() {
  local phase="$1"
  CURRENT_PHASE="$phase"
  {
    echo
    echo "===== Android ${phase} emulator lifecycle ====="
    echo "Android ${phase} emulator requested RAM: ${EMULATOR_MEMORY_MB}MB"
    echo "Android ${phase} emulator image: ${IMAGE}"
  } >>"$LOG"

  "$EMULATOR" -avd "$AVD_NAME" \
    -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -no-metrics \
    -gpu swiftshader_indirect -accel on -camera-back none -camera-front none \
    -memory "$EMULATOR_MEMORY_MB" \
    >>"$LOG" 2>&1 &
  EMULATOR_PID=$!
  echo "Android ${phase} emulator PID: $EMULATOR_PID"
  "$ADB" start-server >/dev/null

  local booted=0 launcher_guard_ready=0
  local second adb_state boot
  for ((second = 1; second <= BOOT_TIMEOUT_SECONDS; second++)); do
    if ! kill -0 "$EMULATOR_PID" >/dev/null 2>&1; then
      wait "$EMULATOR_PID" || true
      fail_emulator "Android ${phase} 16 KiB emulator exited before boot completed"
    fi
    adb_state="$("$ADB" get-state 2>/dev/null || true)"
    if [[ "$adb_state" == "device" ]]; then
      if (( launcher_guard_ready == 0 )) && prepare_pixel_launcher_guard "$phase"; then
        launcher_guard_ready=1
      fi
      boot="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      if [[ "$boot" == "1" && "$launcher_guard_ready" == "1" ]]; then
        booted=1
        echo "Android ${phase} emulator boot accepted in ${second}s"
        break
      fi
    fi
    if (( second % 15 == 0 )); then
      echo "Waiting for Android ${phase} emulator: ${second}s/${BOOT_TIMEOUT_SECONDS}s (adb=${adb_state:-unavailable} launcher_guard_ready=$launcher_guard_ready)"
    fi
    sleep 1
  done
  if (( booted == 0 )); then
    fail_emulator "Android ${phase} 16 KiB emulator failed to boot with hosted UI guard ready within ${BOOT_TIMEOUT_SECONDS}s"
  fi

  wait_for_framework_services "$phase" "after boot acceptance"

  local page_size mem_total_kb runtime_api fingerprint
  page_size="$("$ADB" shell getconf PAGE_SIZE | tr -d '\r[:space:]')"
  if [[ "$page_size" != "16384" ]]; then
    fail_emulator "Android ${phase} emulator is not a 16 KiB runtime: PAGE_SIZE=$page_size"
  fi

  runtime_api="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r[:space:]')"
  if [[ ! "$runtime_api" =~ ^[0-9]+$ ]] || (( runtime_api < 36 )); then
    fail_emulator "Android ${phase} functional runtime is older than API 36: api=${runtime_api:-unknown}"
  fi

  mem_total_kb="$("$ADB" shell cat /proc/meminfo | tr -d '\r' | sed -n 's/^MemTotal:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n1)"
  if [[ ! "$mem_total_kb" =~ ^[0-9]+$ ]] || (( mem_total_kb < MIN_GUEST_MEMORY_KB )); then
    fail_emulator "Android ${phase} guest RAM is below the stability floor: MemTotal=${mem_total_kb:-unknown}kB minimum=${MIN_GUEST_MEMORY_KB}kB"
  fi

  fingerprint="$("$ADB" shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "Android ${phase} runtime confirmed: API=$runtime_api PAGE_SIZE=$page_size image=$IMAGE MemTotal=${mem_total_kb}kB"
  echo "Android ${phase} build fingerprint: $fingerprint"
  finalize_test_system_ui "$phase"
  "$ADB" shell input keyevent 82 >/dev/null 2>&1 || true
  "$ADB" shell settings put global window_animation_scale 0
  "$ADB" shell settings put global transition_animation_scale 0
  "$ADB" shell settings put global animator_duration_scale 0
  "$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

[[ -x "$SDKMANAGER" ]] || { echo "sdkmanager missing: $SDKMANAGER" >&2; exit 1; }
if command -v apt-get >/dev/null 2>&1 && ! dpkg-query -W libpulse0 >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends libpulse0 >/dev/null
fi
yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "platform-tools" "emulator" "$IMAGE" >/dev/null

[[ -x "$ADB" ]] || { echo "adb missing: $ADB" >&2; exit 1; }
[[ -x "$EMULATOR" ]] || { echo "emulator missing: $EMULATOR" >&2; exit 1; }
if [[ ! -e /dev/kvm ]]; then
  echo "16 KiB x86_64 emulator requires KVM on hosted CI" >&2
  exit 1
fi
sudo chmod 666 /dev/kvm
[[ -r /dev/kvm && -w /dev/kvm ]] || { echo "hosted runner cannot access /dev/kvm" >&2; exit 1; }
echo "KVM acceleration enabled for Android functional CI"

rm -rf "$AVD_HOME"
mkdir -p "$AVD_HOME"
export ANDROID_AVD_HOME="$AVD_HOME"
echo no | "$AVDMANAGER" create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$IMAGE" \
  --device "pixel_6" \
  --path "$AVD_HOME/$AVD_NAME.avd" >/dev/null

: >"$LOG"
: >"$FONT_SCALE_LOG"
start_emulator "normal functional"

cd "$ANDROID_DIR"
TEST_SOURCE="app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt"
[[ -f "$TEST_SOURCE" ]] || { echo "functional test source missing: $TEST_SOURCE" >&2; exit 1; }
echo "Functional checkout SHA: $(git rev-parse HEAD)"
echo "JingduUiTest source SHA256: $(sha256sum "$TEST_SOURCE" | awk '{print $1}')"

# The first lifecycle owns the complete source-bound app instrumentation suite. Macrobenchmark and
# Baseline Profile instrumentation remain exclusively in android-performance.
./gradlew --no-daemon --warning-mode all --no-build-cache clean :app:connectedDebugAndroidTest

echo "Android normal functional source-bound suite PASS"

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -s "$APP_APK" ]] || fail_emulator "Source-bound debug APK missing after normal functional suite: $APP_APK"
[[ -s "$TEST_APK" ]] || fail_emulator "Source-bound androidTest APK missing after normal functional suite: $TEST_APK"
APP_APK_SHA="$(sha256sum "$APP_APK" | awk '{print $1}')"
TEST_APK_SHA="$(sha256sum "$TEST_APK" | awk '{print $1}')"
echo "Functional app APK SHA256: $APP_APK_SHA"
echo "Functional androidTest APK SHA256: $TEST_APK_SHA"

# Do not carry UTP/framework state from the normal suite into accessibility evidence. Start a fresh
# -wipe-data 16 KiB lifecycle and install the exact APK bytes produced by the first lifecycle.
stop_emulator
start_emulator "200% font-scale"

[[ "$(sha256sum "$APP_APK" | awk '{print $1}')" == "$APP_APK_SHA" ]] || fail_emulator "Source-bound debug APK changed between functional lifecycles"
[[ "$(sha256sum "$TEST_APK" | awk '{print $1}')" == "$TEST_APK_SHA" ]] || fail_emulator "Source-bound androidTest APK changed between functional lifecycles"
"$ADB" install -r -t "$APP_APK" >/dev/null || fail_emulator "Failed to install source-bound debug APK on fresh 200% emulator"
"$ADB" install -r -t "$TEST_APK" >/dev/null || fail_emulator "Failed to install source-bound androidTest APK on fresh 200% emulator"
echo "200% functional app APK SHA256: $APP_APK_SHA"
echo "200% functional androidTest APK SHA256: $TEST_APK_SHA"

# This hosted pass is a layout/discoverability regression gate. It does not replace physical
# TalkBack/two-OEM qualification. Run the existing JingduUiTest class unchanged at Android 200%.
echo "Running JingduUiTest at Android font_scale=2.0"
"$ADB" shell settings put system font_scale 2.0
FONT_SCALE="$("$ADB" shell settings get system font_scale | tr -d '\r[:space:]')"
if [[ "$FONT_SCALE" != "2.0" && "$FONT_SCALE" != "2" ]]; then
  fail_emulator "Failed to configure Android 200% font scale: font_scale=$FONT_SCALE"
fi

TARGET_PACKAGE="com.junchen.jingdu.debug"
INSTRUMENTATION="$({ "$ADB" shell pm list instrumentation || true; } | tr -d '\r' | sed -n "s/^instrumentation:\([^ ]*\) (target=${TARGET_PACKAGE})$/\1/p" | head -n1)"
if [[ -z "$INSTRUMENTATION" ]]; then
  fail_emulator "Source-bound Android test instrumentation is not installed for the 200% font-scale run"
fi
"$ADB" shell am force-stop "$TARGET_PACKAGE" >/dev/null 2>&1 || true
set +e
"$ADB" shell am instrument -w -r \
  -e class com.junchen.jingdu.JingduUiTest \
  "$INSTRUMENTATION" | tr -d '\r' | tee "$FONT_SCALE_LOG"
FONT_SCALE_STATUS=${PIPESTATUS[0]}
set -e
if (( FONT_SCALE_STATUS != 0 )); then
  fail_emulator "Android 200% font-scale instrumentation command failed: status=$FONT_SCALE_STATUS"
fi
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_ABORTED|INSTRUMENTATION_FAILED|shortMsg=Process crashed|DeadSystemException' "$FONT_SCALE_LOG"; then
  fail_emulator "Android 200% font-scale JingduUiTest reported a failure or system abort"
fi
if ! grep -Eq '^OK \(15 tests\)$' "$FONT_SCALE_LOG"; then
  fail_emulator "Android 200% font-scale JingduUiTest did not report OK (15 tests)"
fi
"$ADB" shell settings put system font_scale 1.0

echo "Android 200% font-scale JingduUiTest PASS (15/15)"
echo "Android functional instrumentation suite PASS on independent 16 KiB runtimes"
