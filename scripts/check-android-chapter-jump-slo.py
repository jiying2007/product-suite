#!/usr/bin/env python3
"""Validate retained physical active-index chapter-jump evidence."""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import re
import sys
from dataclasses import dataclass

DEFAULT_P95_MS = 100.0
REQUIRED_MIN_SAMPLES = 10
METRIC = "chapter-jump"
SAMPLE_RE = re.compile(
    r"sample=metric=chapter-jump;"
    r"durationMs=(?P<duration>[0-9]+(?:\.[0-9]+)?);"
    r"fixtureMiB=(?P<mib>\d+);"
    r"fixtureSha256=(?P<source>[0-9a-f]{64});"
    r"normalizedSha256=(?P<normalized>[0-9a-f]{64});"
    r"chapterCount=(?P<count>\d+);"
    r"targetOffset=(?P<target>\d+);"
    r"finalPosition=(?P<final>\d+)"
)


@dataclass(frozen=True)
class Sample:
    duration_ms: float
    fixture_mib: int
    fixture_sha256: str
    normalized_sha256: str
    chapter_count: int
    target_offset: int
    final_position: int


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
                duration_ms=duration,
                fixture_mib=int(match.group("mib")),
                fixture_sha256=match.group("source"),
                normalized_sha256=match.group("normalized"),
                chapter_count=int(match.group("count")),
                target_offset=int(match.group("target")),
                final_position=int(match.group("final")),
            )
        )
    return samples


def validate_samples(samples: list[Sample], minimum: int) -> list[str]:
    failures: list[str] = []
    if len(samples) < minimum:
        failures.append(f"samples={len(samples)} minimum={minimum}")
    identities = {
        (sample.fixture_mib, sample.fixture_sha256, sample.normalized_sha256)
        for sample in samples
    }
    if len(identities) > 1:
        failures.append("fixture identity drift across samples")
    for index, sample in enumerate(samples, start=1):
        if sample.chapter_count < 2:
            failures.append(f"sample={index} chapterCount={sample.chapter_count} minimum=2")
        if sample.target_offset != sample.final_position:
            failures.append(
                f"sample={index} targetOffset={sample.target_offset} finalPosition={sample.final_position}"
            )
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("raw_log")
    parser.add_argument("--p95-ms", type=float, default=DEFAULT_P95_MS)
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
        print(f"chapter-jump gate: invalid P95 threshold: {args.p95_ms}", file=sys.stderr)
        return 2
    if args.min_samples <= 0:
        print(f"chapter-jump gate: invalid sample floor: {args.min_samples}", file=sys.stderr)
        return 2
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        print(f"chapter-jump gate: invalid source SHA: {args.source_sha}", file=sys.stderr)
        return 2

    log_path = pathlib.Path(args.raw_log)
    try:
        text = log_path.read_text(encoding="utf-8")
    except OSError as error:
        print(f"chapter-jump gate: could not read {log_path}: {error}", file=sys.stderr)
        return 2

    samples = parse_samples(text)
    failures = validate_samples(samples, args.min_samples)
    durations = [sample.duration_ms for sample in samples]
    median = percentile(durations, 50) if durations else None
    p95 = percentile(durations, 95) if durations else None
    if p95 is not None and p95 >= args.p95_ms:
        failures.append(f"P95={p95:.3f}ms target<{args.p95_ms:.3f}ms")
    passed = not failures and p95 is not None

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
        "medianMs": median,
        "p95Ms": p95,
        "pass": passed,
        "failures": failures,
        "fixture": None
        if fixture is None
        else {
            "mib": fixture.fixture_mib,
            "sourceSha256": fixture.fixture_sha256,
            "normalizedSha256": fixture.normalized_sha256,
            "chapterCount": fixture.chapter_count,
        },
        "proof": [
            {
                "targetOffset": sample.target_offset,
                "finalPosition": sample.final_position,
            }
            for sample in samples
        ],
    }
    out = pathlib.Path(args.summary_json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if p95 is None:
        print(
            f"chapter-jump gate: missing {METRIC} evidence; samples={len(samples)} minimum={args.min_samples}",
            file=sys.stderr,
        )
    else:
        status = "PASS" if passed else "FAIL"
        print(
            f"{METRIC} P95: {p95:.3f}ms target<{args.p95_ms:.3f}ms "
            f"median={median:.3f}ms samples={len(samples)} {status}"
        )
    for failure in failures:
        print(f"chapter-jump gate: {failure}", file=sys.stderr)
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
