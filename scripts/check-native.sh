#!/usr/bin/env bash
set -euo pipefail

cmake -S . -B build/native -DCMAKE_BUILD_TYPE=Release -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
cmake --build build/native --parallel
ctest --test-dir build/native --output-on-failure

TIDY=""
for candidate in clang-tidy clang-tidy-21 clang-tidy-20 clang-tidy-19 clang-tidy-18; do
  if command -v "$candidate" >/dev/null 2>&1; then
    TIDY="$(command -v "$candidate")"
    break
  fi
done
if [[ -z "$TIDY" ]]; then
  echo "clang-tidy is required for native quality checks" >&2
  exit 1
fi

"$TIDY" \
  platform/text/native/src/core_api_cached.cpp platform/text/native/src/index_cache.cpp platform/text/native/src/sha256.cpp \
  -p build/native \
  --warnings-as-errors='clang-analyzer-*,bugprone-*,-bugprone-easily-swappable-parameters,-bugprone-implicit-widening-of-multiplication-result,performance-*,portability-*'
