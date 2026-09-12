#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
APP_GRADLE="$ROOT/android/app/build.gradle"
APP_UI="$ROOT/android/app/src/main/java/com/junchen/posestudio/ui/PoseStudioApp.kt"
PHYSICAL_CHECK="$ROOT/scripts/physical-release-check.sh"
PHYSICAL_WORKFLOW="$REPO_ROOT/.github/workflows/pose-studio-physical-release.yml"
CANDIDATE_WORKFLOW="$REPO_ROOT/.github/workflows/pose-studio-candidate-release.yml"
SOURCE_RELEASE_SCRIPT="$REPO_ROOT/scripts/publish-source-release.py"
BENCHMARK_ANALYZER="$ROOT/scripts/analyze-artist-benchmark.py"
BENCHMARK_TEMPLATE="$ROOT/docs/BENCHMARK_RESULTS_TEMPLATE.csv"

if grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  echo "Pose Studio must remain offline-first: INTERNET permission is forbidden" >&2
  exit 1
fi

# Keep the physical-release qualification script syntactically valid and scoped to the exact
# release-like Macrobenchmark variant. Hosted CI does not execute physical performance claims.
bash -n "$PHYSICAL_CHECK"
grep -Fq ':macrobenchmark:connectedBenchmarkAndroidTest' "$PHYSICAL_CHECK"
if grep -Fq ':macrobenchmark:connectedCheck' "$PHYSICAL_CHECK"; then
  echo "Pose Studio physical qualification must target the benchmark variant explicitly" >&2
  exit 1
fi
grep -Fq 'insufficient cold-start evidence' "$PHYSICAL_CHECK"
grep -Fq 'insufficient direct-manipulation frame evidence' "$PHYSICAL_CHECK"

# Physical release signing must be injected after checkout without persisting credentials in the
# repository workspace. Wiring the secrets is a repository contract, not proof that they exist.
grep -Fq 'POSE_STUDIO_RELEASE_KEYSTORE_BASE64' "$PHYSICAL_WORKFLOW"
grep -Fq 'POSE_STUDIO_RELEASE_STORE_PASSWORD' "$PHYSICAL_WORKFLOW"
grep -Fq 'POSE_STUDIO_RELEASE_KEY_ALIAS' "$PHYSICAL_WORKFLOW"
grep -Fq 'POSE_STUDIO_RELEASE_KEY_PASSWORD' "$PHYSICAL_WORKFLOW"
grep -Fq '$RUNNER_TEMP/pose-studio-release.keystore' "$PHYSICAL_WORKFLOW"
grep -Fq "trap 'rm -f \"\$KEYSTORE\"' EXIT" "$PHYSICAL_WORKFLOW"
if grep -Fq 'keystore.properties' "$PHYSICAL_WORKFLOW"; then
  echo "Pose Studio physical workflow must not persist signing passwords in keystore.properties" >&2
  exit 1
fi
grep -Fq 'POSE_STUDIO_RELEASE_STORE_FILE' "$APP_GRADLE"
grep -Fq 'POSE_STUDIO_RELEASE_STORE_PASSWORD' "$APP_GRADLE"
grep -Fq 'POSE_STUDIO_RELEASE_KEY_ALIAS' "$APP_GRADLE"
grep -Fq 'POSE_STUDIO_RELEASE_KEY_PASSWORD' "$APP_GRADLE"

# Frozen candidate tags are immutable. Once the current semver already has a tag+release, later
# main commits must no-op rather than moving the tag or creating a misleading red release job.
grep -Fq 'candidate already frozen for $tag' "$CANDIDATE_WORKFLOW"
grep -Fq 'git ls-remote --exit-code --tags origin' "$CANDIDATE_WORKFLOW"
grep -Fq 'releases/tags/$tag' "$CANDIDATE_WORKFLOW"

# Gated main may prune stale temporary/release branches, but only if they have no open PR and their
# tip is already fully contained in the exact main SHA. This prevents cleanup from deleting work.
python3 -m py_compile "$SOURCE_RELEASE_SCRIPT"
grep -Fq 'fully_merged_into_main' "$SOURCE_RELEASE_SCRIPT"
grep -Fq '/compare/{tip_sha}...{MAIN_SHA}' "$SOURCE_RELEASE_SCRIPT"
grep -Fq 'retained temporary branch with unmerged commits' "$SOURCE_RELEASE_SCRIPT"
grep -Fq 'release/pose-studio-' "$SOURCE_RELEASE_SCRIPT"

# The real-artist benchmark remains external evidence, but its repository-side evidence shape and
# analyzer are executable contracts rather than prose only.
python3 -m py_compile "$BENCHMARK_ANALYZER"
test -s "$BENCHMARK_TEMPLATE"
head -n 1 "$BENCHMARK_TEMPLATE" | grep -Fq 'participant_id,tool,tool_version,task_id,completion_seconds'

python3 - "$ROOT/store/play" <<'PY'
from pathlib import Path
import sys

store = Path(sys.argv[1])
limits = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
}
for locale in ("en-US", "zh-CN", "zh-TW", "zh-HK"):
    directory = store / locale
    for filename, limit in limits.items():
        path = directory / filename
        if not path.is_file():
            raise SystemExit(f"Missing Pose Studio Play metadata for {locale}: {path}")
        text = path.read_text(encoding="utf-8").strip()
        if not text:
            raise SystemExit(f"Empty Pose Studio Play metadata for {locale}: {path}")
        if len(text) > limit:
            raise SystemExit(
                f"Pose Studio Play metadata exceeds {limit} characters for {locale}: "
                f"{filename} has {len(text)}"
            )
PY

privacy_url_file="$ROOT/store/play/PRIVACY_POLICY_URL.txt"
if [[ ! -s "$privacy_url_file" ]] || ! grep -Eq '^https://' "$privacy_url_file"; then
  echo "Pose Studio Play privacy-policy URL must be a non-empty HTTPS URL" >&2
  exit 1
fi
if grep -Fq '/blob/main/' "$privacy_url_file" || grep -Fq '/blob/main/' "$APP_UI"; then
  echo "Pose Studio beta privacy links must not depend on mutable main; pin released beta policy content" >&2
  exit 1
fi

cd "$ROOT/android"
./gradlew --no-daemon --warning-mode all poseStudioCheck

# Validate the final merged release manifest after dependency manifests have been applied. The
# offline-first contract must hold for the distributable package, not merely for src/main.
MERGED_MANIFEST="$(find app/build/intermediates -type f -name AndroidManifest.xml -path '*release*' -print | grep -E '/merged_manifest/|/merged_manifests/' | head -n1 || true)"
[[ -n "$MERGED_MANIFEST" && -s "$MERGED_MANIFEST" ]] || { echo "Pose Studio merged release manifest missing" >&2; exit 1; }
if grep -Fq 'android.permission.INTERNET' "$MERGED_MANIFEST"; then
  echo "Pose Studio merged release manifest unexpectedly requests INTERNET" >&2
  exit 1
fi
if grep -Fq '<profileable' "$MERGED_MANIFEST"; then
  echo "Pose Studio production release must not be profileable" >&2
  exit 1
fi
if grep -Eq 'android:debuggable="true"|android:allowBackup="true"' "$MERGED_MANIFEST"; then
  echo "Pose Studio merged release manifest contains debug/backup behavior forbidden for production" >&2
  exit 1
fi

# Pose Studio is currently Java/Kotlin-only. If a future dependency introduces native code, fail
# closed so 64-bit/16 KiB compatibility is added and proven before that release can pass CI.
RELEASE_APK="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print -quit)"
[[ -n "$RELEASE_APK" && -s "$RELEASE_APK" ]] || { echo "Pose Studio release APK missing" >&2; exit 1; }
if unzip -Z1 "$RELEASE_APK" | grep -Eq '^lib/[^/]+/.*\.so$'; then
  echo "Pose Studio release gained native libraries; add explicit 64-bit/16 KiB validation before shipping" >&2
  exit 1
fi

echo "Pose Studio merged release manifest/package contract OK"
