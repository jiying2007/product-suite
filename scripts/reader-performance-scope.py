#!/usr/bin/env python3
"""Return whether changed repository paths require Jingdu hosted Reader performance qualification."""

from __future__ import annotations

import sys

ANDROID_PREFIX = "apps/jingdu/android/"
EXCLUDED_ANDROID_PREFIXES = (
    "app/src/androidTest/",
    "app/src/test/",
)
EXCLUDED_ANDROID_EXACT = {
    ".gitignore",
}
PERFORMANCE_EXACT = {
    "scripts/run-android-macrobenchmark-ci.sh",
    "scripts/check-android-performance-slo.py",
}
PERFORMANCE_PREFIXES = (
    "platform/",
    "native/",
)


def requires_reader_performance(path: str) -> bool:
    normalized = path.strip().removeprefix("./")
    if not normalized:
        return False
    if normalized in PERFORMANCE_EXACT:
        return True
    if normalized.startswith(PERFORMANCE_PREFIXES):
        return True
    if not normalized.startswith(ANDROID_PREFIX):
        return False

    relative = normalized[len(ANDROID_PREFIX) :]
    if relative in EXCLUDED_ANDROID_EXACT:
        return False
    if normalized.endswith(".md"):
        return False
    if relative.startswith(EXCLUDED_ANDROID_PREFIXES):
        return False
    return True


def main() -> int:
    paths = [line.rstrip("\n") for line in sys.stdin]
    print("true" if any(requires_reader_performance(path) for path in paths) else "false")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
