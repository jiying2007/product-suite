#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${HARMONY_HVIGORW:-}" ]]; then
  HVIGORW="$HARMONY_HVIGORW"
elif command -v hvigorw >/dev/null 2>&1; then
  HVIGORW="$(command -v hvigorw)"
elif [[ -n "${DEVECO_TOOLS_HOME:-}" && -x "${DEVECO_TOOLS_HOME}/bin/hvigorw" ]]; then
  HVIGORW="${DEVECO_TOOLS_HOME}/bin/hvigorw"
else
  echo "HarmonyOS Command Line Tools 6.1.1+ are required (set HARMONY_HVIGORW or DEVECO_TOOLS_HOME)." >&2
  exit 2
fi

cd "$(dirname "$0")/../apps/jingdu/harmony"
"$HVIGORW" --mode project -p product=default -p buildMode=debug assembleApp
