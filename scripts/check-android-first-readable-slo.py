#!/usr/bin/env python3
"""Validate retained physical unchanged-book first-readable evidence."""

from __future__ import annotations

import argparse
import json
import math
import os
import pathlib
import re
import sys
from dataclasses import dataclass

DEFAULT_P95_MS = 500.0
REQUIRED_MIN_SAMPLES = 10
METRIC = "unchanged-imported-book"
SAMPLE_RE = re.compile(
    r"jingdu\.firstReadableSample="
    r"metric=unchanged-imported-book;"
    r"iteration=(?P<iteration>\d+);"
    r"durationMs=(?P<duration>[0-9]+(?:\.[0-9]+)?);"
    r"fixtureMiB=(?P<mib>\d+);"
    r"fixtureBytes=(?P<bytes>\d+);"
    r"fixtureSha256=(?P<sha>[0-9a-f]{64});"
    r"position=(?P<position>-?\d+);"
    r"layoutGeneration=(?P<generation>\d+)"
)


@dataclass(frozen=True)
class Sample:
    iteration: int
    duration_ms: float
    fixture_mib: int
    fixture_bytes: int
    fixture_sha256: str
    position: int
    layout_generation: int


def percentile(values: list[float], percentile_value: int) -> float:
    if not values:
        raise ValueError("percentile requires at least one sample")
    ordered = sorted(values)
    ideal_index = min(100, max(0, percentile_value)) / 100.0 * (len(ordered) - 1)
    first_index = int(ideal_index)
    second_index = min(first_index + 1, len(ordered) - 1)
    ratio = ideal_index - first_index
    return ordered[first_index] * (1.0 - ratio) + ordered[second_index] * ratio


def parse_samples(text: str) -> list[Sample]:
    samples: list[Sample] = []
    for match in SAMPLE_RE.finditer(text):
        duration = float(match.group("duration"))
        if not math.isfinite(duration) or duration <= 0:
            continue
        samples.append(
            Sample(
                iteration=int(match.group("iteration")),
                duration_ms=duration,
                fixture_mib=int(match.group("mib")),
                fixture_bytes=int(match.group("bytes")),
                fixture_sha256=match.group("sha"),
                position=int(match.group("position")),
                layout_generation=int(match.group("generation")),
            )
        )
    return samples


def validate_samples(samples: list[Sample], minimum: int) -> list[str]:
    failures: list[str] = []
    if len(samples) < minimum:
        failures.append(f"samples={len(samples)} minimum={minimum}")
    iterations = [sample.iteration for sample in samples]
    if len(set(iterations)) != len(iterations):
        failures.append("duplicate iteration evidence")
    if samples and sorted(iterations) != list(range(1, len(samples) + 1)):
        failures.append(f"non-contiguous iterations={sorted(iterations)}")
    identities = {
        (sample.fixture_mib, sample.fixture_bytes, sample.fixture_sha256)
        for sample in samples
    }
    if len(identities) > 1:
        failures.append("fixture identity drift across samples")
    for sample in samples:
        if sample.position < 0 or sample.layout_generation <= 0:
            failures.append(
                f"iteration={sample.iteration} missing authoritative readiness "
                f"position={sample.position} layoutGeneration={sample.layout_generation}"
            )
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("instrumentation_log")
    parser.add_argument(
        "--p95-ms",
        type=float,
        default=float(os.environ.get("JINGDU_FIRST_READABLE_P95_MS", str(DEFAULT_P95_MS))),
    )
    parser.add_argument("--min-samples", type=int, default=REQUIRED_MIN_SAMPLES)
    parser.add_argument("--summary-json", required=True)
    parser.add_argument("--source-ref", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--manufacturer", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--fingerprint", required=True)
    args = parser.parse_args(argv)

    if not math.isfinite(args.p95_ms) or args.p95_ms <= 0:
        print(f"first-readable gate: invalid P95 threshold: {args.p95_ms}", file=sys.stderr)
        return 2
    if args.min_samples <= 0:
        print(f"first-readable gate: invalid sample floor: {args.min_samples}", file=sys.stderr)
        return 2
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        print(f"first-readable gate: invalid source SHA: {args.source_sha}", file=sys.stderr)
        return 2

    log_path = pathlib.Path(args.instrumentation_log)
    try:
        text = log_path.read_text(encoding="utf-8")
    except OSError as error:
        print(f"first-readable gate: could not read {log_path}: {error}", file=sys.stderr)
        return 2

    samples = parse_samples(text)
    failures = validate_samples(samples, args.min_samples)
    durations = [sample.duration_ms for sample in samples]
    p50 = percentile(durations, 50) if durations else None
    p95 = percentile(durations, 95) if durations else None
    passed = not failures and p95 is not None and p95 < args.p95_ms
    if p95 is not None and p95 >= args.p95_ms:
        failures.append(f"P95={p95:.3f}ms target<{args.p95_ms:.3f}ms")

    fixture = samples[0] if samples else None
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
        "target": {"percentile": 95, "operator": "<", "milliseconds": args.p95_ms},
        "sampleFloor": args.min_samples,
        "samplesMs": durations,
        "medianMs": p50,
        "p95Ms": p95,
        "pass": passed,
        "failures": failures,
        "fixture": None
        if fixture is None
        else {
            "mib": fixture.fixture_mib,
            "bytes": fixture.fixture_bytes,
            "sha256": fixture.fixture_sha256,
        },
    }
    summary_path = pathlib.Path(args.summary_json)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if p95 is None:
        print(
            f"first-readable gate: missing {METRIC} evidence; samples={len(samples)} minimum={args.min_samples}",
            file=sys.stderr,
        )
    else:
        status = "PASS" if passed else "FAIL"
        print(
            f"{METRIC} P95: {p95:.3f}ms target<{args.p95_ms:.3f}ms "
            f"median={p50:.3f}ms samples={len(samples)} {status}"
        )
    for failure in failures:
        print(f"first-readable gate: {failure}", file=sys.stderr)
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
