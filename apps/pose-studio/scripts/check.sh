#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
if grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  echo "Pose Studio must remain offline-first: INTERNET permission is forbidden" >&2
  exit 1
fi

for locale in en-US zh-CN zh-TW zh-HK; do
  listing="$ROOT/store/play/$locale/full_description.txt"
  if [[ ! -s "$listing" ]]; then
    echo "Missing non-empty Pose Studio Play listing source for $locale: $listing" >&2
    exit 1
  fi
done

privacy_url_file="$ROOT/store/play/PRIVACY_POLICY_URL.txt"
if [[ ! -s "$privacy_url_file" ]] || ! grep -Eq '^https://' "$privacy_url_file"; then
  echo "Pose Studio Play privacy-policy URL must be a non-empty HTTPS URL" >&2
  exit 1
fi

cd "$ROOT/android"
./gradlew --no-daemon --warning-mode all poseStudioCheck
