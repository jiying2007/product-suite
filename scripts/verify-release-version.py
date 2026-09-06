#!/usr/bin/env python3
"""Verify Jingdu release identity without hard-coding one release number in CI gates."""

from __future__ import annotations

import re
from pathlib import Path

APP = Path("apps/jingdu/android/app/build.gradle")
ROOT = Path("apps/jingdu/android/build.gradle")
MANIFEST_ROOT = Path("releases/source")


def fail(message: str) -> "NoReturn":
    raise SystemExit(message)


def match(pattern: str, text: str, label: str) -> str:
    found = re.search(pattern, text)
    if not found:
        fail(f"missing {label}")
    return found.group(1)


def main() -> int:
    app = APP.read_text(encoding="utf-8")
    root = ROOT.read_text(encoding="utf-8")

    version_code = int(match(r"versionCodeValue\s*=.*?\.getOrElse\((\d+)\)", app, "Android default versionCode"))
    app_version = match(r'versionNameValue\s*=.*?\.getOrElse\("([^"]+)"\)', app, "Android default versionName")
    root_version = match(r'releaseVersion\s*=.*?\.getOrElse\("([^"]+)"\)', root, "Android staging versionName")

    if version_code <= 0:
        fail(f"Android default versionCode must be positive: {version_code}")
    if not re.fullmatch(r"\d+\.\d+\.\d+", app_version):
        fail(f"Android default versionName is not SemVer: {app_version}")
    if app_version != root_version:
        fail(f"Android version drift: app={app_version} staging={root_version}")

    tag = f"v{app_version}"
    manifest = MANIFEST_ROOT / f"{tag}.md"
    if not manifest.is_file():
        fail(f"missing source release manifest for {tag}: {manifest}")
    text = manifest.read_text(encoding="utf-8")
    required = (f"version: {tag}", "kind: source-release", "google_play_production: false")
    missing = [item for item in required if item not in text]
    if missing:
        fail(f"source release manifest contract missing for {tag}: {missing}")

    print(f"Release version contract OK: {tag}, versionCode {version_code}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
