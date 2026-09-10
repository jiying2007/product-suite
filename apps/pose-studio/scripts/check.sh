#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
PHYSICAL_CHECK="$ROOT/scripts/physical-release-check.sh"

if grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  echo "Pose Studio must remain offline-first: INTERNET permission is forbidden" >&2
  exit 1
fi

bash -n "$PHYSICAL_CHECK"
grep -Fq ':macrobenchmark:connectedBenchmarkAndroidTest' "$PHYSICAL_CHECK"
if grep -Fq ':macrobenchmark:connectedCheck' "$PHYSICAL_CHECK"; then
  echo "Pose Studio physical qualification must target the benchmark variant explicitly" >&2
  exit 1
fi

grep -Fq 'insufficient cold-start evidence' "$PHYSICAL_CHECK"
grep -Fq 'insufficient direct-manipulation frame evidence' "$PHYSICAL_CHECK"

cd "$ROOT/android"
./gradlew --no-daemon --warning-mode all poseStudioCheck
