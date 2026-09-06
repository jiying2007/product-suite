#!/usr/bin/env python3
"""Publish a fully populated Jingdu draft release after exact-main CI succeeds."""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def fail(message: str) -> "NoReturn":
    raise SystemExit(message)


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        fail(f"missing required environment variable: {name}")
    return value


API = required_env("GH_API_URL")
REPO = required_env("GH_REPOSITORY")
TOKEN = required_env("GH_TOKEN")
MAIN_SHA = required_env("MAIN_SHA")


def request(path: str, method: str = "GET", payload: object | None = None, allowed: tuple[int, ...] = ()):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        f"{API}/repos/{REPO}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req) as response:
            body = response.read()
            return response.status, json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        if error.code in allowed:
            return error.code, None
        detail = error.read().decode("utf-8", errors="replace")
        fail(f"GitHub API {method} {path} failed: {error.code} {detail}")


def paged(path: str) -> list[dict]:
    output: list[dict] = []
    separator = "&" if "?" in path else "?"
    for page in range(1, 11):
        _, values = request(f"{path}{separator}per_page=100&page={page}")
        batch = values or []
        output.extend(batch)
        if len(batch) < 100:
            break
    return output


def declared_version() -> str:
    app = Path("apps/jingdu/android/app/build.gradle").read_text(encoding="utf-8")
    match = re.search(r'versionNameProperty\.getOrElse\("([^\"]+)"\)', app)
    if not match:
        fail("Android versionName default not found")
    return match.group(1)


def resolve_object_sha(kind: str, sha: str) -> str:
    current_kind, current_sha = kind, sha
    for _ in range(4):
        if current_kind != "tag":
            return current_sha
        _, tag = request(f"/git/tags/{current_sha}")
        obj = tag.get("object") or {}
        current_kind = obj.get("type", "")
        current_sha = obj.get("sha", "")
        if not current_sha:
            fail("annotated tag object has no target sha")
    fail("tag indirection exceeds supported depth")


def read_tag_target(tag: str) -> str:
    encoded = urllib.parse.quote(tag, safe="")
    status, ref = request(f"/git/ref/tags/{encoded}", allowed=(404,))
    if status == 404:
        fail(f"source tag is missing: {tag}")
    obj = ref.get("object") or {}
    return resolve_object_sha(obj.get("type", ""), obj.get("sha", ""))


def find_release(tag: str) -> dict | None:
    for release in paged("/releases"):
        if release.get("tag_name") == tag:
            return release
    return None


def expected_assets(tag: str) -> set[str]:
    version = tag.removeprefix("v")
    return {
        f"Jingdu-v{version}-debug-signed.apk",
        "SHA256SUMS.txt",
        "SIGNING-CERT-SHA256.txt",
    }


def asset_names(release: dict) -> set[str]:
    return {asset.get("name") for asset in release.get("assets") or [] if asset.get("name")}


def require_assets(tag: str, release: dict) -> None:
    missing = sorted(expected_assets(tag) - asset_names(release))
    if missing:
        fail(f"release {tag} is missing required assets: {missing}")


def main() -> int:
    version = declared_version()
    tag = f"v{version}"
    manifest = Path(f"releases/source/{tag}.md")
    if not manifest.is_file():
        print(f"no source manifest for {tag}; finalization skipped")
        return 0

    release = find_release(tag)
    if release is None:
        fail(f"release was not prepared for gated version {tag}")

    target_sha = read_tag_target(tag)
    require_assets(tag, release)

    if release.get("draft") is not True:
        if release.get("immutable") is not True:
            fail(f"published release {tag} is not immutable")
        print(f"immutable release already published: {tag} -> {target_sha}")
        return 0

    if target_sha != MAIN_SHA:
        fail(f"draft release {tag} points to {target_sha}, expected gated main {MAIN_SHA}")

    release_id = release.get("id")
    if not release_id:
        fail(f"draft release {tag} has no database id")

    _, published = request(
        f"/releases/{release_id}",
        method="PATCH",
        payload={"draft": False, "prerelease": False, "make_latest": "true"},
    )
    if not published or published.get("draft") is not False:
        fail(f"GitHub did not publish draft release {tag}")

    encoded = urllib.parse.quote(tag, safe="")
    _, final = request(f"/releases/tags/{encoded}")
    require_assets(tag, final)
    if final.get("immutable") is not True:
        fail(f"published release {tag} did not become immutable")
    if read_tag_target(tag) != MAIN_SHA:
        fail(f"published immutable tag {tag} drifted away from gated main {MAIN_SHA}")

    print(f"published immutable release after complete CI: {tag} -> {MAIN_SHA}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
