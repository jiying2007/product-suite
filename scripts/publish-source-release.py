#!/usr/bin/env python3
"""Prepare Jingdu source provenance and a draft GitHub Release from a gated main SHA.

This script is intentionally stdlib-only. It runs only after all required product gates
succeed on a push to main. New versions create an annotated source tag plus a *draft*
GitHub Release. The following Android APK job uploads every release asset while the
release is still mutable. A separate workflow_run finalizer publishes the draft only
after the complete CI run succeeds, so repositories with Immutable Releases enabled
never need to mutate assets or tags after publication.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

TEMP_PREFIXES = ("feat/", "fix/", "chore/", "ci/", "refactor/", "docs/", "test/", "perf/", "tmp/", "dependabot/")
RELEASE_PREFIX = "release/source-v"
CURRENT_STAGE_MARKER = "## Current Android release stage"
CURRENT_STAGE_TEXT = (
    "This release records immutable source provenance. The attached debug-signed APK is the official "
    "installable Android artifact for the current GitHub release stage. GitHub `main` protection is not "
    "a gate for this stage. This is not Google Play production signing or rollout evidence."
)


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
    root = Path("apps/jingdu/android/build.gradle").read_text(encoding="utf-8")
    app_match = re.search(r'versionNameProperty\.getOrElse\("([^\"]+)"\)', app)
    root_match = re.search(r'jingduVersionName"\)\.getOrElse\("([^\"]+)"\)', root)
    if not app_match or not root_match:
        fail("Android source release version defaults are missing")
    if app_match.group(1) != root_match.group(1):
        fail(f"Android version drift: app={app_match.group(1)} staging={root_match.group(1)}")
    version = app_match.group(1)
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        fail(f"Android source version is not SemVer: {version}")
    return version


def verify_manifest(tag: str) -> Path | None:
    manifest = Path(f"releases/source/{tag}.md")
    if not manifest.is_file():
        return None
    text = manifest.read_text(encoding="utf-8")
    required = (f"version: {tag}", "kind: source-release", "google_play_production: false")
    missing = [item for item in required if item not in text]
    if missing:
        fail(f"source release manifest contract missing: {missing}")
    return manifest


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


def read_tag(tag: str):
    encoded = urllib.parse.quote(tag, safe="")
    status, ref = request(f"/git/ref/tags/{encoded}", allowed=(404,))
    if status == 404:
        return None
    obj = ref.get("object") or {}
    sha = resolve_object_sha(obj.get("type", ""), obj.get("sha", ""))
    return ref, sha


def find_release(tag: str) -> dict | None:
    for release in paged("/releases"):
        if release.get("tag_name") == tag:
            return release
    return None


def manifest_sha256(manifest: Path) -> str:
    return hashlib.sha256(manifest.read_bytes()).hexdigest()


def expected_assets(tag: str) -> set[str]:
    version = tag.removeprefix("v")
    return {
        f"Jingdu-v{version}-debug-signed.apk",
        "SHA256SUMS.txt",
        "SIGNING-CERT-SHA256.txt",
    }


def create_annotated_tag(tag: str, manifest: Path) -> None:
    manifest_hash = manifest_sha256(manifest)
    _, tag_object = request(
        "/git/tags",
        method="POST",
        payload={
            "tag": tag,
            "message": (
                f"Jingdu {tag} source provenance\n\n"
                f"gated-main-sha: {MAIN_SHA}\n"
                f"manifest: {manifest.as_posix()}\n"
                f"manifest-sha256: {manifest_hash}\n"
                "google-play-production: false"
            ),
            "object": MAIN_SHA,
            "type": "commit",
        },
    )
    tag_object_sha = (tag_object or {}).get("sha", "")
    if not tag_object_sha:
        fail("GitHub did not return annotated tag object sha")
    request(
        "/git/refs",
        method="POST",
        payload={"ref": f"refs/tags/{tag}", "sha": tag_object_sha},
    )
    final = read_tag(tag)
    if final is None or final[1] != MAIN_SHA:
        fail(f"annotated tag {tag} does not resolve to gated main {MAIN_SHA}")


def create_draft_release(tag: str, target_sha: str, manifest: Path) -> dict:
    _, release = request(
        "/releases",
        method="POST",
        payload={
            "tag_name": tag,
            "target_commitish": target_sha,
            "name": f"Jingdu {tag}",
            "body": release_body(tag, manifest),
            "draft": True,
            "prerelease": False,
            "make_latest": "false",
            "generate_release_notes": True,
        },
    )
    if not release or release.get("draft") is not True:
        fail(f"GitHub did not create {tag} as a draft release")
    return release


def prepare_release(tag: str, manifest: Path) -> None:
    existing = read_tag(tag)
    release = find_release(tag)

    if existing is not None and release is not None:
        _, target_sha = existing
        if release.get("draft") is True:
            if target_sha != MAIN_SHA:
                fail(f"stale draft {tag} points to {target_sha}, expected gated main {MAIN_SHA}")
            print(f"draft source release already prepared for {tag} -> {target_sha}")
            return

        assets = {asset.get("name") for asset in release.get("assets") or []}
        missing = sorted(expected_assets(tag) - assets)
        if missing:
            fail(f"published release {tag} is missing required immutable assets: {missing}")
        print(
            f"source release already published at {tag} -> {target_sha}: "
            f"{release.get('html_url')}"
        )
        return

    if existing is not None:
        _, target_sha = existing
        if target_sha != MAIN_SHA:
            fail(f"orphan tag {tag} points to {target_sha}, expected gated main {MAIN_SHA}")
        release = create_draft_release(tag, target_sha, manifest)
        print(f"prepared draft release for existing source tag: id={release.get('id')} tag={tag}")
        return

    if release is not None:
        fail(f"release exists for missing source tag {tag}")

    create_annotated_tag(tag, manifest)
    release = create_draft_release(tag, MAIN_SHA, manifest)
    final = read_tag(tag)
    if final is None or final[1] != MAIN_SHA:
        fail(f"created source tag {tag} does not resolve to gated main {MAIN_SHA}")
    print(
        f"prepared annotated tag + draft release: id={release.get('id')} tag={tag} "
        f"manifest_sha256={manifest_sha256(manifest)}"
    )


def release_body(tag: str, manifest: Path) -> str:
    version = tag.removeprefix("v")
    apk_name = f"Jingdu-v{version}-debug-signed.apk"
    return (
        f"Jingdu {tag} source provenance and current-stage Android GitHub release.\n\n"
        f"Immutable source manifest: `{manifest.as_posix()}`.\n"
        f"Manifest SHA-256: `{manifest_sha256(manifest)}`.\n\n"
        f"{CURRENT_STAGE_TEXT}\n\n"
        f"{CURRENT_STAGE_MARKER}\n\n"
        f"`{apk_name}` is built from the annotated `{tag}` source tag and signed with the repository-stable "
        "Android debug key (`androiddebugkey`). `SHA256SUMS.txt` and `SIGNING-CERT-SHA256.txt` are attached "
        "while this release is still a draft; the release is published only after all three assets are present.\n\n"
        "A future Play production release uses the separate production/upload signing path plus the Play "
        "Console and physical-device evidence documented in `docs/PRODUCTION_READINESS.md`."
    )


def cleanup_closed_temporary_branches() -> None:
    open_pulls = paged("/pulls?state=open")
    open_heads = {
        pr.get("head", {}).get("ref")
        for pr in open_pulls
        if pr.get("head", {}).get("repo", {}).get("full_name") == REPO
    }

    closed_pulls = paged("/pulls?state=closed&sort=updated&direction=desc")
    candidates: list[str] = []
    for pr in closed_pulls:
        head = pr.get("head") or {}
        head_repo = head.get("repo") or {}
        ref = head.get("ref")
        if head_repo.get("full_name") != REPO or not ref or ref == "main" or ref in open_heads:
            continue
        if ref.startswith(RELEASE_PREFIX) or ref.startswith(TEMP_PREFIXES):
            if ref not in candidates:
                candidates.append(ref)

    for ref in candidates:
        encoded = urllib.parse.quote(ref, safe="/")
        status, _ = request(f"/git/refs/heads/{encoded}", method="DELETE", allowed=(404, 422))
        if status in (204, 404, 422):
            print(f"pruned closed temporary branch: {ref} ({status})")


def main() -> int:
    version = declared_version()
    tag = f"v{version}"
    manifest = verify_manifest(tag)
    if manifest is None:
        print(f"no source manifest for {tag}; publication skipped")
        cleanup_closed_temporary_branches()
        return 0

    prepare_release(tag, manifest)
    cleanup_closed_temporary_branches()
    return 0


if __name__ == "__main__":
    sys.exit(main())
