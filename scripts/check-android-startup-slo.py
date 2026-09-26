#!/usr/bin/env python3
"""Validate physical Android cold-start Macrobenchmark evidence."""

from __future__ import annotations

import argparse
import json
import math
import os
import pathlib
import sys
from typing import Any, Iterable

COLD_START_BENCHMARK_SUFFIX = "StartupBenchmark.coldStartup"
COLD_START_METRIC = "timeToInitialDisplayMs"
REQUIRED_MIN_SAMPLES = 10
DEFAULT_P95_MS = 1000.0


def finite_numbers(value: Any) -> Iterable[float]:
    if isinstance(value, bool):
        return
    if isinstance(value, (int, float)) and math.isfinite(float(value)):
        yield float(value)
    elif isinstance(value, list):
        for item in value:
            yield from finite_numbers(item)


def androidx_percentile(values: list[float], percentile: int) -> float:
    if not values:
        raise ValueError("percentile requires at least one sample")
    ordered = sorted(values)
    ideal_index = min(100, max(0, percentile)) / 100.0 * (len(ordered) - 1)
    first_index = int(ideal_index)
    second_index = min(first_index + 1, len(ordered) - 1)
    ratio = ideal_index - first_index
    return ordered[first_index] * (1.0 - ratio) + ordered[second_index] * ratio


def collect_files(paths: list[str]) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for raw in paths:
        path = pathlib.Path(raw)
        if path.is_dir():
            files.extend(sorted(path.rglob("*-benchmarkData.json")))
        elif path.is_file():
            files.append(path)
    return list(dict.fromkeys(files))


def cold_start_records(payload: Any) -> list[tuple[str, list[float]]]:
    if not isinstance(payload, dict):
        return []
    benchmarks = payload.get("benchmarks")
    if not isinstance(benchmarks, list):
        return []
    rows: list[tuple[str, list[float]]] = []
    for index, benchmark in enumerate(benchmarks):
        if not isinstance(benchmark, dict):
            continue
        name = str(benchmark.get("name") or f"benchmark[{index}]")
        class_name = str(benchmark.get("className") or "")
        label = f"{class_name}.{name}".strip(".")
        if not label.endswith(COLD_START_BENCHMARK_SUFFIX):
            continue
        metrics = benchmark.get("metrics")
        if not isinstance(metrics, dict):
            continue
        metric = metrics.get(COLD_START_METRIC)
        if not isinstance(metric, dict):
            continue
        samples = list(finite_numbers(metric.get("runs")))
        if samples:
            rows.append((label, samples))
    return rows


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+")
    parser.add_argument(
        "--p95-ms",
        type=float,
        default=float(os.environ.get("JINGDU_COLD_STARTUP_P95_MS", str(DEFAULT_P95_MS))),
    )
    parser.add_argument("--min-samples", type=int, default=REQUIRED_MIN_SAMPLES)
    args = parser.parse_args(argv)

    if not math.isfinite(args.p95_ms) or args.p95_ms <= 0:
        print(f"startup gate: invalid P95 threshold: {args.p95_ms}", file=sys.stderr)
        return 2
    if args.min_samples <= 0:
        print(f"startup gate: invalid sample floor: {args.min_samples}", file=sys.stderr)
        return 2

    files = collect_files(args.paths)
    if not files:
        print("startup gate: no benchmarkData.json evidence found", file=sys.stderr)
        return 2

    found = 0
    failed = False
    for path in files:
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            print(f"startup gate: could not read {path}: {error}", file=sys.stderr)
            return 2

        for label, samples in cold_start_records(payload):
            found += 1
            if len(samples) < args.min_samples:
                print(
                    f"{label} {COLD_START_METRIC}: samples={len(samples)} "
                    f"minimum={args.min_samples} FAIL ({path})",
                    file=sys.stderr,
                )
                failed = True
                continue
            p95 = androidx_percentile(samples, 95)
            status = "PASS" if p95 < args.p95_ms else "FAIL"
            print(
                f"{label} {COLD_START_METRIC} P95: {p95:.3f}ms "
                f"target<{args.p95_ms:.3f}ms samples={len(samples)} {status} ({path})"
            )
            if status == "FAIL":
                failed = True

    if found == 0:
        print(
            f"startup gate: missing required {COLD_START_BENCHMARK_SUFFIX} "
            f"{COLD_START_METRIC} evidence",
            file=sys.stderr,
        )
        return 1

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
