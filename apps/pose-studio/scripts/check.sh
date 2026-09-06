#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
if grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  echo "Pose Studio must remain offline-first: INTERNET permission is forbidden" >&2
  exit 1
fi
cd "$ROOT/android"
./gradlew --no-daemon --warning-mode all poseStudioCheck
