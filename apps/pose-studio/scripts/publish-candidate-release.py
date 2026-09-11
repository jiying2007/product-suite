#!/usr/bin/env python3
"""Freeze a Pose Studio commercial-beta candidate after exact-main gates are green.

Creates an annotated `pose-studio-v<semver>` tag and a draft prerelease. It never
creates or claims a production AAB/signing artifact; issue #83 remains the external
qualification authority for v1 commercial production.
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
CANDIDATE_SHA = required_env("CANDIDATE_SHA")
REQUIRED_WORKFLOWS = ("CI", "Pose Studio", "CI Contracts")


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
        if isinstance(batch, dict):
            batch = batch.get("workflow_runs") or []
        output.extend(batch)
        if len(batch) < 100:
            break
    return output


def declared_version() -> tuple[str, int]:
    build = Path("apps/pose-studio/android/app/build.gradle").read_text(encoding="utf-8")
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', build)
    code_match = re.search(r"versionCode\s*=\s*(\d+)", build)
    if not name_match or not code_match:
        fail("Pose Studio Android versionName/versionCode are missing")
    version = name_match.group(1)
    version_code = int(code_match.group(1))
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        fail(f"Pose Studio versionName is not SemVer: {version}")
    return version, version_code


def manifest_for(version: str, version_code: int) -> Path:
    tag = f"pose-studio-v{version}"
    manifest = Path(f"apps/pose-studio/releases/{tag}.md")
    if not manifest.is_file():
        fail(f"missing Pose Studio candidate manifest: {manifest}")
    text = manifest.read_text(encoding="utf-8")
    required = (
        f"version: {tag}",
        "kind: pose-studio-commercial-beta-candidate",
        f"android_version_name: {version}",
        f"android_version_code: {version_code}",
        "production_v1: false",
        "production_signing_proven: false",
        "google_play_production: false",
        "qualification_issue: 83",
    )
    missing = [item for item in required if item not in text]
    if missing:
        fail(f"Pose Studio candidate manifest contract missing: {missing}")
    return manifest


def manifest_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main_sha() -> str:
    _, branch = request("/branches/main")
    return ((branch or {}).get("commit") or {}).get("sha", "")


def exact_main_gates_green() -> tuple[bool, dict[str, int]]:
    encoded = urllib.parse.quote(CANDIDATE_SHA, safe="")
    runs = paged(f"/actions/runs?head_sha={encoded}&event=push")
    latest: dict[str, dict] = {}
    for run in runs:
        name = run.get("name")
        if name not in REQUIRED_WORKFLOWS:
            continue
        previous = latest.get(name)
        if previous is None or run.get("run_number", 0) > previous.get("run_number", 0):
            latest[name] = run

    missing = [name for name in REQUIRED_WORKFLOWS if name not in latest]
    if missing:
        print(f"candidate freeze deferred: exact-main workflows missing for {CANDIDATE_SHA}: {missing}")
        return False, {}

    run_ids: dict[str, int] = {}
    for name in REQUIRED_WORKFLOWS:
        run = latest[name]
        run_ids[name] = int(run.get("id"))
        if run.get("status") != "completed" or run.get("conclusion") != "success":
            print(
                "candidate freeze deferred: "
                f"{name} status={run.get('status')} conclusion={run.get('conclusion')} run={run.get('id')}"
            )
            return False, run_ids
    return True, run_ids


def resolve_object_sha(kind: str, sha: str) -> str:
    current_kind, current_sha = kind, sha
    for _ in range(4):
        if current_kind != "tag":
            return current_sha
        _, tag = request(f"/git/tags/{current_sha}")
        obj = (tag or {}).get("object") or {}
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
    obj = (ref or {}).get("object") or {}
    return ref, resolve_object_sha(obj.get("type", ""), obj.get("sha", ""))


def find_release(tag: str) -> dict | None:
    releases = paged("/releases")
    for release in releases:
        if release.get("tag_name") == tag:
            return release
    return None


def create_annotated_tag(tag: str, manifest: Path, version_code: int, run_ids: dict[str, int]) -> None:
    digest = manifest_sha256(manifest)
    message = (
        f"Pose Studio {tag} commercial-beta candidate\n\n"
        f"gated-main-sha: {CANDIDATE_SHA}\n"
        f"android-version-code: {version_code}\n"
        f"manifest: {manifest.as_posix()}\n"
        f"manifest-sha256: {digest}\n"
        f"ci-run: {run_ids['CI']}\n"
        f"pose-studio-run: {run_ids['Pose Studio']}\n"
        f"ci-contracts-run: {run_ids['CI Contracts']}\n"
        "production-v1: false\n"
        "production-signing-proven: false\n"
        "google-play-production: false\n"
        "qualification-issue: #83"
    )
    _, tag_object = request(
        "/git/tags",
        method="POST",
        payload={"tag": tag, "message": message, "object": CANDIDATE_SHA, "type": "commit"},
    )
    tag_object_sha = (tag_object or {}).get("sha", "")
    if not tag_object_sha:
        fail("GitHub did not return Pose Studio annotated tag object sha")
    request("/git/refs", method="POST", payload={"ref": f"refs/tags/{tag}", "sha": tag_object_sha})


def release_body(tag: str, manifest: Path, version_code: int, run_ids: dict[str, int]) -> str:
    return (
        f"Pose Studio `{tag}` commercial-beta candidate source freeze.\n\n"
        f"Exact gated main SHA: `{CANDIDATE_SHA}`  \n"
        f"Android versionName/versionCode: `{tag.removeprefix('pose-studio-v')}` / `{version_code}`  \n"
        f"Manifest: `{manifest.as_posix()}`  \n"
        f"Manifest SHA-256: `{manifest_sha256(manifest)}`  \n"
        f"CI run: `{run_ids['CI']}`  \n"
        f"Pose Studio run: `{run_ids['Pose Studio']}`  \n"
        f"CI Contracts run: `{run_ids['CI Contracts']}`\n\n"
        "This draft prerelease freezes repository-side source provenance only. It does not contain or claim a production-signed AAB, production signing certificate, physical-device qualification, Play Console validation, accessibility sign-off, artist benchmark, closed-track evidence, or staged rollout. Issue #83 remains open and authoritative for those v1 commercial-production gates.\n\n"
        "The physical qualification workflow must run against this exact immutable tag (or its exact SHA) with Pose-Studio-specific release-signing secrets before any distributable production claim."
    )


def create_draft_release(tag: str, manifest: Path, version_code: int, run_ids: dict[str, int]) -> dict:
    _, release = request(
        "/releases",
        method="POST",
        payload={
            "tag_name": tag,
            "target_commitish": CANDIDATE_SHA,
            "name": f"Pose Studio {tag.removeprefix('pose-studio-v')} commercial-beta candidate",
            "body": release_body(tag, manifest, version_code, run_ids),
            "draft": True,
            "prerelease": True,
            "make_latest": "false",
            "generate_release_notes": True,
        },
    )
    if not release or release.get("draft") is not True:
        fail(f"GitHub did not create {tag} as a draft prerelease")
    return release


def main() -> int:
    if main_sha() != CANDIDATE_SHA:
        print(f"candidate freeze skipped: main moved away from {CANDIDATE_SHA}")
        return 0

    green, run_ids = exact_main_gates_green()
    if not green:
        return 0

    version, version_code = declared_version()
    tag = f"pose-studio-v{version}"
    manifest = manifest_for(version, version_code)
    existing = read_tag(tag)
    release = find_release(tag)

    if existing is not None:
        _, target_sha = existing
        if target_sha != CANDIDATE_SHA:
            fail(f"existing {tag} points to {target_sha}, expected exact gated main {CANDIDATE_SHA}")
        if release is None:
            release = create_draft_release(tag, manifest, version_code, run_ids)
            print(f"created draft prerelease for existing candidate tag: id={release.get('id')} tag={tag}")
        else:
            print(f"Pose Studio candidate already frozen: {tag} -> {target_sha} release={release.get('id')}")
        return 0

    if release is not None:
        fail(f"release exists for missing Pose Studio candidate tag {tag}")

    create_annotated_tag(tag, manifest, version_code, run_ids)
    final = read_tag(tag)
    if final is None or final[1] != CANDIDATE_SHA:
        fail(f"created Pose Studio candidate tag {tag} does not resolve to {CANDIDATE_SHA}")
    release = create_draft_release(tag, manifest, version_code, run_ids)
    print(
        f"frozen Pose Studio candidate: tag={tag} sha={CANDIDATE_SHA} "
        f"manifest_sha256={manifest_sha256(manifest)} draft_release_id={release.get('id')}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
