#!/usr/bin/env python3
"""Validate retained physical Smart Clean scan evidence."""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import re
import sys
from dataclasses import dataclass

DEFAULT_FIXTURE_MIB = 20
DEFAULT_LIMIT_MS = 1000.0
REQUIRED_MIN_SAMPLES = 5
SAMPLE_RE = re.compile(
    r"sample=metric=(?P<metric>smart-clean-\d+mib);"
    r"durationMs=(?P<duration>[0-9]+(?:\.[0-9]+)?);"
    r"fixtureMiB=(?P<mib>\d+);"
    r"fixtureBytes=(?P<bytes>\d+);"
    r"fixtureSha256=(?P<source>[0-9a-f]{64});"
    r"normalizedSha256=(?P<normalized>[0-9a-f]{64});"
    r"candidateCount=(?P<count>\d+);"
    r"candidateSha256=(?P<candidate_sha>[0-9a-f]{64});"
    r"topScore=(?P<score>\d+);"
    r"topReason=(?P<reason>[a-zA-Z0-9_\-]+)"
)


@dataclass(frozen=True)
class Sample:
    metric: str
    duration_ms: float
    fixture_mib: int
    fixture_bytes: int
    fixture_sha256: str
    normalized_sha256: str
    candidate_count: int
    candidate_sha256: str
    top_score: int
    top_reason: str


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
                metric=match.group("metric"),
                duration_ms=duration,
                fixture_mib=int(match.group("mib")),
                fixture_bytes=int(match.group("bytes")),
                fixture_sha256=match.group("source"),
                normalized_sha256=match.group("normalized"),
                candidate_count=int(match.group("count")),
                candidate_sha256=match.group("candidate_sha"),
                top_score=int(match.group("score")),
                top_reason=match.group("reason"),
            )
        )
    return samples


def validate_samples(
    samples: list[Sample],
    minimum: int,
    fixture_mib: int,
    limit_ms: float,
) -> list[str]:
    failures: list[str] = []
    if len(samples) < minimum:
        failures.append(f"samples={len(samples)} minimum={minimum}")
    identities = {
        (
            sample.fixture_mib,
            sample.fixture_bytes,
            sample.fixture_sha256,
            sample.normalized_sha256,
        )
        for sample in samples
    }
    if len(identities) > 1:
        failures.append("fixture identity drift across samples")
    candidate_checksums = {sample.candidate_sha256 for sample in samples}
    if len(candidate_checksums) > 1:
        failures.append("candidate checksum drift across samples")
    expected_min_bytes = fixture_mib * 1024 * 1024
    for index, sample in enumerate(samples, start=1):
        if sample.fixture_mib != fixture_mib:
            failures.append(
                f"sample={index} fixtureMiB={sample.fixture_mib} expected={fixture_mib}"
            )
        if sample.fixture_bytes < expected_min_bytes:
            failures.append(
                f"sample={index} fixtureBytes={sample.fixture_bytes} minimum={expected_min_bytes}"
            )
        if sample.candidate_count <= 0:
            failures.append(f"sample={index} candidateCount={sample.candidate_count}")
        if sample.top_score <= 0 or not sample.top_reason:
            failures.append(
                f"sample={index} invalid top candidate score={sample.top_score} reason={sample.top_reason!r}"
            )
        if sample.duration_ms >= limit_ms:
            failures.append(
                f"sample={index} duration={sample.duration_ms:.3f}ms target<{limit_ms:.3f}ms"
            )
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("raw_log")
    parser.add_argument("--fixture-mib", type=int, default=DEFAULT_FIXTURE_MIB)
    parser.add_argument("--limit-ms", type=float, default=DEFAULT_LIMIT_MS)
    parser.add_argument("--min-samples", type=int, default=REQUIRED_MIN_SAMPLES)
    parser.add_argument("--summary-json", required=True)
    parser.add_argument("--source-ref", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--manufacturer", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--fingerprint", required=True)
    args = parser.parse_args(argv)

    if args.fixture_mib <= 0:
        print(f"smart-clean gate: invalid fixture MiB: {args.fixture_mib}", file=sys.stderr)
        return 2
    if not math.isfinite(args.limit_ms) or args.limit_ms <= 0:
        print(f"smart-clean gate: invalid threshold: {args.limit_ms}", file=sys.stderr)
        return 2
    if args.min_samples <= 0:
        print(f"smart-clean gate: invalid sample floor: {args.min_samples}", file=sys.stderr)
        return 2
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        print(f"smart-clean gate: invalid source SHA: {args.source_sha}", file=sys.stderr)
        return 2

    path = pathlib.Path(args.raw_log)
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as error:
        print(f"smart-clean gate: could not read {path}: {error}", file=sys.stderr)
        return 2

    metric = f"smart-clean-{args.fixture_mib}mib"
    samples = [sample for sample in parse_samples(text) if sample.metric == metric]
    failures = validate_samples(samples, args.min_samples, args.fixture_mib, args.limit_ms)
    durations = [sample.duration_ms for sample in samples]
    median = percentile(durations, 50) if durations else None
    p95 = percentile(durations, 95) if durations else None
    maximum = max(durations) if durations else None
    passed = not failures and len(samples) >= args.min_samples

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
        "metric": metric,
        "target": {
            "operator": "<",
            "milliseconds": args.limit_ms,
            "appliesTo": "every-retained-sample",
        },
        "sampleFloor": args.min_samples,
        "samplesMs": durations,
        "medianMs": median,
        "p95Ms": p95,
        "maxMs": maximum,
        "pass": passed,
        "failures": failures,
        "fixture": None
        if fixture is None
        else {
            "mib": fixture.fixture_mib,
            "bytes": fixture.fixture_bytes,
            "sourceSha256": fixture.fixture_sha256,
            "normalizedSha256": fixture.normalized_sha256,
        },
        "proof": [
            {
                "candidateCount": sample.candidate_count,
                "candidateSha256": sample.candidate_sha256,
                "topScore": sample.top_score,
                "topReason": sample.top_reason,
            }
            for sample in samples
        ],
    }
    out = pathlib.Path(args.summary_json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if not durations:
        print(
            f"smart-clean gate: missing {metric} evidence; samples={len(samples)} minimum={args.min_samples}",
            file=sys.stderr,
        )
    else:
        status = "PASS" if passed else "FAIL"
        print(
            f"{metric}: median={median:.3f}ms P95={p95:.3f}ms max={maximum:.3f}ms "
            f"target-each<{args.limit_ms:.3f}ms samples={len(samples)} {status}"
        )
    for failure in failures:
        print(f"smart-clean gate: {failure}", file=sys.stderr)
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
