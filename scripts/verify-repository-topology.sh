#!/usr/bin/env bash
set -euo pipefail

required=(
  apps/jingdu/android
  apps/jingdu/harmony
  apps/audiolab/android
  apps/network-toolbox/android
  apps/phone-doctor/android
  apps/voice-cleaner/android
  platform/text
  platform/audio
  platform/network
  platform/ai
  platform/billing
  platform/telemetry
)
for path in "${required[@]}"; do
  test -e "$path" || { echo "missing required product-suite path: $path" >&2; exit 1; }
done

forbidden=(
  "apps/""android"
  "apps/""harmony"
  "core/""native"
  "jiying2007/""llm_apps"
)
failed=0
for needle in "${forbidden[@]}"; do
  if git grep -nF "$needle" -- ':!scripts/verify-repository-topology.sh'; then
    echo "forbidden legacy topology reference: $needle" >&2
    failed=1
  fi
done

if [[ -e core ]]; then
  echo "legacy top-level core/ must not exist; shared text code belongs under platform/text" >&2
  failed=1
fi

if [[ $failed -ne 0 ]]; then
  exit 1
fi

echo "product-suite topology: OK"
