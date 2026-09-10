#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
APP_GRADLE="$ROOT/android/app/build.gradle"
PHYSICAL_CHECK="$ROOT/scripts/physical-release-check.sh"
PHYSICAL_WORKFLOW="$REPO_ROOT/.github/workflows/pose-studio-physical-release.yml"

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

cd "$ROOT/android"
./gradlew --no-daemon --warning-mode all poseStudioCheck
