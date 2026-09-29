#!/usr/bin/env python3
"""Validate retained physical 200 MiB open/search/Clean stability evidence."""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

METRIC = "200mib-open-search-clean-stability"
MIN_FIXTURE_BYTES = 200 * 1024 * 1024
STATUS_RE = re.compile(
    r"jingdu\.stability200MiB="
    r"fixtureMiB=(?P<mib>\d+);"
    r"fixtureBytes=(?P<bytes>\d+);"
    r"fixtureSha256=(?P<source>[0-9a-f]{64});"
    r"normalizedSha256=(?P<normalized>[0-9a-f]{64});"
    r"pid=(?P<pid>\d+);"
    r"initialPosition=(?P<initial_position>\d+);"
    r"initialLayoutGeneration=(?P<initial_generation>\d+);"
    r"finalPosition=(?P<final_position>\d+);"
    r"finalLayoutGeneration=(?P<final_generation>\d+);"
    r"searchHitCount=(?P<hits>\d+);"
    r"cleanCandidateCount=(?P<clean_candidates>\d+);"
    r"operations=(?P<operations>[a-zA-Z0-9_-]+)"
)
TARGET_CONTEXT = r"(?:Process:\s*com\.junchen\.jingdu|com\.junchen\.jingdu)"
LOGCAT_FAILURES = (
    (re.compile(r"ANR in com\.junchen\.jingdu", re.IGNORECASE), "target ANR"),
    (re.compile(r"am_anr.*com\.junchen\.jingdu", re.IGNORECASE), "target am_anr"),
    (
        re.compile(
            rf"(?:{TARGET_CONTEXT})[\s\S]{{0,2000}}OutOfMemoryError|"
            rf"OutOfMemoryError[\s\S]{{0,2000}}(?:{TARGET_CONTEXT})",
            re.IGNORECASE,
        ),
        "target OutOfMemoryError",
    ),
    (
        re.compile(
            rf"FATAL EXCEPTION[\s\S]{{0,2000}}(?:{TARGET_CONTEXT})|"
            rf"(?:{TARGET_CONTEXT})[\s\S]{{0,2000}}FATAL EXCEPTION",
            re.IGNORECASE,
        ),
        "target fatal exception",
    ),
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("instrumentation_log")
    parser.add_argument("logcat")
    parser.add_argument("meminfo")
    parser.add_argument("exit_info")
    parser.add_argument("--summary-json", required=True)
    parser.add_argument("--source-ref", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--manufacturer", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--fingerprint", required=True)
    args = parser.parse_args(argv)

    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        print(f"200 MiB stability gate: invalid source SHA: {args.source_sha}", file=sys.stderr)
        return 2

    paths = {
        "instrumentation": pathlib.Path(args.instrumentation_log),
        "logcat": pathlib.Path(args.logcat),
        "meminfo": pathlib.Path(args.meminfo),
        "exitInfo": pathlib.Path(args.exit_info),
    }
    texts: dict[str, str] = {}
    for label, path in paths.items():
        try:
            texts[label] = path.read_text(encoding="utf-8", errors="replace")
        except OSError as error:
            print(f"200 MiB stability gate: could not read {label} {path}: {error}", file=sys.stderr)
            return 2

    failures: list[str] = []
    instrumentation = texts["instrumentation"]
    if "INSTRUMENTATION_CODE: -1" not in instrumentation:
        failures.append("instrumentation did not complete successfully")
    runner_status = re.search(r"stability_instrumentation_status=(\d+)", instrumentation)
    if runner_status is not None and int(runner_status.group(1)) != 0:
        failures.append(f"stability instrumentation status={runner_status.group(1)}")
    for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED", "Process crashed", "System has crashed"):
        if marker in instrumentation:
            failures.append(f"instrumentation failure marker: {marker}")

    match = STATUS_RE.search(instrumentation)
    proof = None
    if match is None:
        failures.append("missing jingdu.stability200MiB completion proof")
    else:
        proof = {
            "fixtureMiB": int(match.group("mib")),
            "fixtureBytes": int(match.group("bytes")),
            "fixtureSha256": match.group("source"),
            "normalizedSha256": match.group("normalized"),
            "pid": int(match.group("pid")),
            "initialPosition": int(match.group("initial_position")),
            "initialLayoutGeneration": int(match.group("initial_generation")),
            "finalPosition": int(match.group("final_position")),
            "finalLayoutGeneration": int(match.group("final_generation")),
            "searchHitCount": int(match.group("hits")),
            "cleanCandidateCount": int(match.group("clean_candidates")),
            "operations": match.group("operations"),
        }
        if proof["fixtureMiB"] != 200:
            failures.append(f"fixtureMiB={proof['fixtureMiB']} expected=200")
        if proof["fixtureBytes"] < MIN_FIXTURE_BYTES:
            failures.append(
                f"fixtureBytes={proof['fixtureBytes']} minimum={MIN_FIXTURE_BYTES}"
            )
        if proof["initialLayoutGeneration"] <= 0 or proof["finalLayoutGeneration"] <= 0:
            failures.append("Reader layout generation proof missing")
        if proof["searchHitCount"] <= 0:
            failures.append(f"searchHitCount={proof['searchHitCount']}")
        if proof["operations"] != "complete":
            failures.append(f"operations={proof['operations']} expected=complete")

    logcat_failures: list[str] = []
    for pattern, label in LOGCAT_FAILURES:
        if pattern.search(texts["logcat"]):
            logcat_failures.append(label)
    failures.extend(f"logcat: {label}" for label in logcat_failures)

    if "com.junchen.jingdu" not in texts["meminfo"]:
        failures.append("meminfo does not identify com.junchen.jingdu")

    passed = not failures
    summary = {
        "schemaVersion": 1,
        "kind": "jingdu-physical-release-slo",
        "sourceRef": args.source_ref,
        "sourceSha": args.source_sha,
        "device": {
            "manufacturer": args.manufacturer,
            "model": args.model,
            "sdk": args.sdk,
            "fingerprint": args.fingerprint,
        },
        "metric": METRIC,
        "target": {
            "kind": "stability",
            "requirements": [
                "200 MiB import/open reaches authoritative paged-ready",
                "indexed exact search completes with positive hits",
                "Smart Clean candidate scan completes",
                "Reader remains authoritative paged-ready in the same process",
                "no OOM/ANR/crash marker in sequence-scoped logcat",
            ],
        },
        "pass": passed,
        "failures": failures,
        "fixture": None
        if proof is None
        else {
            "mib": proof["fixtureMiB"],
            "bytes": proof["fixtureBytes"],
            "sourceSha256": proof["fixtureSha256"],
            "normalizedSha256": proof["normalizedSha256"],
        },
        "proof": proof,
        "diagnostics": {
            "logcat": str(paths["logcat"]),
            "meminfo": str(paths["meminfo"]),
            "exitInfo": str(paths["exitInfo"]),
            "logcatFailureMarkers": logcat_failures,
        },
    }
    out = pathlib.Path(args.summary_json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    status = "PASS" if passed else "FAIL"
    print(f"{METRIC}: {status}")
    for failure in failures:
        print(f"200 MiB stability gate: {failure}", file=sys.stderr)
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
