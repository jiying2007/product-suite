#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/apps/jingdu/android"
TARGET_PACKAGE="com.junchen.jingdu"
TEST_PACKAGE="com.junchen.jingdu.macrobenchmark"
TEST_CLASSES="com.junchen.jingdu.macrobenchmark.ReaderJourneyBenchmark,com.junchen.jingdu.macrobenchmark.StartupBenchmark,com.junchen.jingdu.macrobenchmark.PhysicalReleaseSloBenchmark"
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
cold_start_metric=timeToInitialDisplayMs
cold_start_p95_target_ms=1000
unchanged_imported_first_readable_metric=click_to_authoritative_paged_ready
unchanged_imported_first_readable_p95_target_ms=500
unchanged_imported_first_readable_min_samples=10
new_20_mib_first_readable_metric=action_view_import_to_authoritative_paged_ready
new_20_mib_first_readable_limit_ms=1000
new_20_mib_first_readable_min_samples=5
new_100_mib_first_readable_metric=action_view_import_to_authoritative_paged_ready
new_100_mib_first_readable_limit_ms=2000
new_100_mib_first_readable_min_samples=5
chapter_jump_metric=active_index_chapters_lookup_and_jump
chapter_jump_p95_target_ms=100
chapter_jump_min_samples=10
indexed_exact_search_metric=active_index_exact_search
indexed_exact_search_p95_target_ms=100
indexed_exact_search_min_samples=10
smart_clean_20_mib_metric=noise_candidates_streaming_scan
smart_clean_20_mib_limit_ms=1000
smart_clean_20_mib_min_samples=5
smart_clean_100_mib_metric=noise_candidates_streaming_scan
smart_clean_100_mib_limit_ms=3000
smart_clean_100_mib_min_samples=5
tts_next_chunk_metric=speak_next_generation_projection_real_tts_queue
tts_next_chunk_p95_target_ms=150
tts_next_chunk_min_samples=10
stability_200_mib_metric=import_open_search_clean_no_oom_anr
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
  -e class "$TEST_CLASSES" \
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
mapfile -t BENCHMARK_JSON < <(find "$RESULT_ROOT/evidence" -type f -name '*-benchmarkData.json' -print)
((${#BENCHMARK_JSON[@]} > 0)) || { echo "Physical Release benchmarkData.json missing" >&2; exit 1; }

CHAPTER_JUMP_LOG="$RESULT_ROOT/chapter-jump.log"
"$ADB" shell content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 10 \
  > "$RESULT_ROOT/chapter-jump-setup.log"
: > "$CHAPTER_JUMP_LOG"
for iteration in $(seq 1 10); do
  echo "iteration=$iteration" >> "$CHAPTER_JUMP_LOG"
  "$ADB" shell content call \
    --uri content://com.junchen.jingdu.benchmarkfixture \
    --method chapterJumpMetric \
    --arg 10 | tee -a "$CHAPTER_JUMP_LOG"
done

INDEXED_SEARCH_LOG="$RESULT_ROOT/indexed-search.log"
: > "$INDEXED_SEARCH_LOG"
for iteration in $(seq 1 10); do
  echo "iteration=$iteration" >> "$INDEXED_SEARCH_LOG"
  "$ADB" shell content call \
    --uri content://com.junchen.jingdu.benchmarkfixture \
    --method indexedSearchMetric \
    --arg 10 | tee -a "$INDEXED_SEARCH_LOG"
done

SMART_CLEAN_20_LOG="$RESULT_ROOT/smart-clean-20mib.log"
"$ADB" shell content call --uri content://com.junchen.jingdu.benchmarkfixture --method seedSmartClean --arg 20 \
  > "$RESULT_ROOT/smart-clean-20mib-setup.log"
: > "$SMART_CLEAN_20_LOG"
for iteration in $(seq 1 5); do
  echo "iteration=$iteration" >> "$SMART_CLEAN_20_LOG"
  "$ADB" shell content call \
    --uri content://com.junchen.jingdu.benchmarkfixture \
    --method smartCleanMetric \
    --arg 20 | tee -a "$SMART_CLEAN_20_LOG"
done

SMART_CLEAN_100_LOG="$RESULT_ROOT/smart-clean-100mib.log"
"$ADB" shell content call --uri content://com.junchen.jingdu.benchmarkfixture --method seedSmartClean --arg 100 \
  > "$RESULT_ROOT/smart-clean-100mib-setup.log"
: > "$SMART_CLEAN_100_LOG"
for iteration in $(seq 1 5); do
  echo "iteration=$iteration" >> "$SMART_CLEAN_100_LOG"
  "$ADB" shell content call \
    --uri content://com.junchen.jingdu.benchmarkfixture \
    --method smartCleanMetric \
    --arg 100 | tee -a "$SMART_CLEAN_100_LOG"
done

TTS_NEXT_CHUNK_LOG="$RESULT_ROOT/tts-next-chunk.log"
"$ADB" shell content call --uri content://com.junchen.jingdu.benchmarkfixture --method seed --arg 10 \
  > "$RESULT_ROOT/tts-next-chunk-setup.log"
: > "$TTS_NEXT_CHUNK_LOG"
for iteration in $(seq 1 10); do
  echo "iteration=$iteration" >> "$TTS_NEXT_CHUNK_LOG"
  "$ADB" shell content call \
    --uri content://com.junchen.jingdu.benchmarkfixture \
    --method ttsNextChunkMetric \
    --arg 10 | tee -a "$TTS_NEXT_CHUNK_LOG"
done

STABILITY_LOG="$RESULT_ROOT/stability-200mib.log"
STABILITY_LOGCAT="$RESULT_ROOT/stability-200mib-logcat.txt"
STABILITY_MEMINFO="$RESULT_ROOT/stability-200mib-meminfo.txt"
STABILITY_EXIT_INFO="$RESULT_ROOT/stability-200mib-exit-info.txt"

set +e
"$ADB" shell am instrument -w -r \
  -e class com.junchen.jingdu.macrobenchmark.PhysicalReleaseStabilityTest \
  "$INSTRUMENTATION" | tee "$STABILITY_LOG"
STABILITY_STATUS=${PIPESTATUS[0]}
set -e

"$ADB" shell logcat -d -v threadtime > "$STABILITY_LOGCAT" || true
"$ADB" shell dumpsys meminfo "$TARGET_PACKAGE" > "$STABILITY_MEMINFO" || true
"$ADB" shell dumpsys activity exit-info "$TARGET_PACKAGE" > "$STABILITY_EXIT_INFO" || true

echo "stability_instrumentation_status=$STABILITY_STATUS" >> "$STABILITY_LOG"

cd "$ROOT"
# These are separate product SLO authorities over the same retained physical evidence directory.
# Never substitute hosted-regression thresholds for either physical gate.
python3 scripts/check-android-performance-slo.py "$RESULT_ROOT/evidence" --mode release
python3 scripts/check-android-startup-slo.py "$RESULT_ROOT/evidence"
python3 scripts/check-android-first-readable-slo.py "$LOG" \
  --summary-json "$RESULT_ROOT/first-readable-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-new-import-slo.py "$LOG" \
  --summary-json "$RESULT_ROOT/new-20mib-first-readable-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-new-import-slo.py "$LOG" \
  --fixture-mib 100 \
  --limit-ms 2000 \
  --summary-json "$RESULT_ROOT/new-100mib-first-readable-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-chapter-jump-slo.py "$CHAPTER_JUMP_LOG" \
  --summary-json "$RESULT_ROOT/chapter-jump-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-indexed-search-slo.py "$INDEXED_SEARCH_LOG" \
  --summary-json "$RESULT_ROOT/indexed-search-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-smart-clean-slo.py "$SMART_CLEAN_20_LOG" \
  --fixture-mib 20 \
  --limit-ms 1000 \
  --summary-json "$RESULT_ROOT/smart-clean-20mib-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-smart-clean-slo.py "$SMART_CLEAN_100_LOG" \
  --fixture-mib 100 \
  --limit-ms 3000 \
  --summary-json "$RESULT_ROOT/smart-clean-100mib-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-tts-next-chunk-slo.py "$TTS_NEXT_CHUNK_LOG" \
  --summary-json "$RESULT_ROOT/tts-next-chunk-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"
python3 scripts/check-android-200mib-stability-slo.py \
  "$STABILITY_LOG" \
  "$STABILITY_LOGCAT" \
  "$STABILITY_MEMINFO" \
  "$STABILITY_EXIT_INFO" \
  --summary-json "$RESULT_ROOT/stability-200mib-slo.json" \
  --source-ref "$SOURCE_REF" \
  --source-sha "$ACTUAL_SOURCE_SHA" \
  --manufacturer "$MANUFACTURER" \
  --model "$MODEL" \
  --sdk "$SDK" \
  --fingerprint "$FINGERPRINT"

echo "Physical Release Reader frame gate PASS: P95<=40ms P99<=80ms with real VOLUME_DOWN page turns"
echo "Physical Release cold-start gate PASS: StartupBenchmark.coldStartup P95<1000ms"
echo "Physical Release unchanged-book first-readable gate PASS: P95<500ms over >=10 retained samples"
echo "Physical Release new 20 MiB first-readable gate PASS: every retained sample <1000ms"
echo "Physical Release new 100 MiB first-readable gate PASS: every retained sample <2000ms"
echo "Physical Release chapter-jump gate PASS: active-index P95<100ms over >=10 retained samples"
echo "Physical Release indexed exact-search gate PASS: P95<100ms over >=10 retained samples"
echo "Physical Release Smart Clean 20 MiB gate PASS: every retained noisy-fixture scan <1000ms"
echo "Physical Release Smart Clean 100 MiB gate PASS: every retained noisy-fixture scan <3000ms"
echo "Physical Release TTS next-chunk gate PASS: real-engine queue P95<150ms over >=10 retained samples"
echo "Physical Release 200 MiB stability gate PASS: import/open/search/Clean/Reader-ready with no OOM/ANR/crash"
