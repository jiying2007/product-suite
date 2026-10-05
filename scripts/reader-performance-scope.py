#!/usr/bin/env python3
"""Classify whether changed paths can affect Jingdu hosted Reader performance.

The classifier is intentionally conservative: any production Android path is performance-relevant
unless it is isolated test/docs metadata. Shared native/platform runtime and the benchmark/SLO
implementation are always relevant. Workflow/gate-only changes are verified by CI Contracts rather
than consuming a hosted emulator.
"""

from __future__ import annotations

import sys

IGNORED_ANDROID_PREFIXES = (
    "apps/jingdu/android/app/src/androidTest/",
    "apps/jingdu/android/app/src/test/",
)
IGNORED_ANDROID_SUFFIXES = (".md",)
RELEVANT_PREFIXES = (
    "apps/jingdu/android/",
    "apps/jingdu/third_party/",
    "platform/",
    "native/",
)
RELEVANT_EXACT = {
    "scripts/run-android-macrobenchmark-ci.sh",
    "scripts/check-android-performance-slo.py",
    "scripts/test-android-performance-slo.py",
    "scripts/reader-hosted-emulator-baseline.json",
}


def is_performance_relevant(path: str) -> bool:
    path = path.strip().lstrip("./")
    if not path:
        return False
    if path in RELEVANT_EXACT:
        return True
    if path.startswith("apps/jingdu/android/"):
        if path == "apps/jingdu/android/.gitignore":
            return False
        if path.startswith(IGNORED_ANDROID_PREFIXES):
            return False
        if path.endswith(IGNORED_ANDROID_SUFFIXES):
            return False
        return True
    return path.startswith(("apps/jingdu/third_party/", "platform/", "native/"))


def main(argv: list[str]) -> int:
    relevant = [path for path in argv[1:] if is_performance_relevant(path)]
    print("true" if relevant else "false")
    if relevant:
        print("Reader performance-relevant paths:", file=sys.stderr)
        for path in relevant:
            print(f"  {path}", file=sys.stderr)
    else:
        print("No production Reader performance-relevant paths changed.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
