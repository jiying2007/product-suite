#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
PHYSICAL_CHECK="$ROOT/scripts/physical-release-check.sh"

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
