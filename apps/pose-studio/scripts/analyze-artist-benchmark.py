#!/usr/bin/env python3
"""Validate and summarize Pose Studio's frozen ten-pose artist benchmark.

This tool intentionally does not manufacture a pass/fail marketing claim. It checks
that the study shape matches BENCHMARK_PROTOCOL.md and reports the primary/secondary
statistics needed for a human commercial-readiness decision.
"""

from __future__ import annotations

import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

REQUIRED_COLUMNS = {
    "participant_id",
    "tool",
    "tool_version",
    "task_id",
    "completion_seconds",
    "corrections",
    "undo_count",
    "misselections",
    "abandoned",
    "confidence",
    "source_sha",
    "device_model",
    "api_level",
}
POSE_TOOL = "Pose Studio"
EXPECTED_TASKS = set(range(1, 11))


def fail(message: str) -> "NoReturn":
    raise SystemExit(message)


def parse_bool(value: str, row_number: int) -> bool:
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "y"}:
        return True
    if normalized in {"0", "false", "no", "n"}:
        return False
    fail(f"row {row_number}: abandoned must be true/false, got {value!r}")


def parse_nonnegative(value: str, name: str, row_number: int) -> float:
    try:
        parsed = float(value)
    except ValueError:
        fail(f"row {row_number}: {name} is not numeric: {value!r}")
    if not math.isfinite(parsed) or parsed < 0:
        fail(f"row {row_number}: {name} must be finite and non-negative")
    return parsed


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return math.nan
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    weight = position - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def load_rows(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        columns = set(reader.fieldnames or [])
        missing = sorted(REQUIRED_COLUMNS - columns)
        if missing:
            fail(f"missing required CSV columns: {missing}")

        rows: list[dict] = []
        seen: set[tuple[str, str, int]] = set()
        for row_number, raw in enumerate(reader, start=2):
            participant = raw["participant_id"].strip()
            tool = raw["tool"].strip()
            tool_version = raw["tool_version"].strip()
            source_sha = raw["source_sha"].strip()
            device_model = raw["device_model"].strip()
            if not all((participant, tool, tool_version, source_sha, device_model)):
                fail(f"row {row_number}: participant/tool/version/source/device fields must not be blank")
            try:
                task_id = int(raw["task_id"])
                api_level = int(raw["api_level"])
                corrections = int(raw["corrections"])
                undo_count = int(raw["undo_count"])
                misselections = int(raw["misselections"])
                confidence = int(raw["confidence"])
            except ValueError:
                fail(f"row {row_number}: integer field contains a non-integer value")
            if task_id not in EXPECTED_TASKS:
                fail(f"row {row_number}: task_id must be 1..10")
            if api_level < 1 or min(corrections, undo_count, misselections) < 0:
                fail(f"row {row_number}: counts/api level are out of range")
            if confidence not in range(1, 6):
                fail(f"row {row_number}: confidence must be 1..5")

            key = (participant, tool, task_id)
            if key in seen:
                fail(f"row {row_number}: duplicate participant/tool/task row: {key}")
            seen.add(key)

            rows.append(
                {
                    "participant": participant,
                    "tool": tool,
                    "tool_version": tool_version,
                    "task_id": task_id,
                    "completion": parse_nonnegative(raw["completion_seconds"], "completion_seconds", row_number),
                    "corrections": corrections,
                    "undo_count": undo_count,
                    "misselections": misselections,
                    "abandoned": parse_bool(raw["abandoned"], row_number),
                    "confidence": confidence,
                    "source_sha": source_sha,
                    "device_model": device_model,
                    "api_level": api_level,
                }
            )
    return rows


def validate_study(rows: list[dict]) -> None:
    if not rows:
        fail("benchmark CSV is empty")
    participants = sorted({row["participant"] for row in rows})
    tools = sorted({row["tool"] for row in rows})
    if len(participants) < 8:
        fail(f"benchmark requires at least 8 participants; found {len(participants)}")
    if POSE_TOOL not in tools:
        fail(f"tool column must contain exact name {POSE_TOOL!r}")
    if len(tools) < 3:
        fail(f"benchmark requires Pose Studio plus at least two comparators; found {tools}")

    source_shas = {row["source_sha"] for row in rows if row["tool"] == POSE_TOOL}
    if len(source_shas) != 1:
        fail(f"Pose Studio rows must bind to exactly one source SHA; found {sorted(source_shas)}")

    grouped_tasks: dict[tuple[str, str], set[int]] = defaultdict(set)
    for row in rows:
        grouped_tasks[(row["participant"], row["tool"])].add(row["task_id"])
    incomplete = [key for key, tasks in grouped_tasks.items() if tasks != EXPECTED_TASKS]
    if incomplete:
        sample = ", ".join(f"{participant}/{tool}" for participant, tool in incomplete[:5])
        fail(f"every participant/tool must contain all ten frozen tasks; incomplete: {sample}")

    expected_pairs = {(participant, tool) for participant in participants for tool in tools}
    missing_pairs = sorted(expected_pairs - set(grouped_tasks))
    if missing_pairs:
        sample = ", ".join(f"{participant}/{tool}" for participant, tool in missing_pairs[:5])
        fail(f"every participant must run every tool; missing: {sample}")


def summarize(rows: list[dict]) -> None:
    participants = sorted({row["participant"] for row in rows})
    tools = sorted({row["tool"] for row in rows})
    source_sha = next(row["source_sha"] for row in rows if row["tool"] == POSE_TOOL)

    print(f"Pose Studio source SHA: {source_sha}")
    print(f"Participants: {len(participants)}")
    print(f"Tools: {', '.join(tools)}")
    print()
    print("Tool summary (non-abandoned completion times; abandonment reported separately):")

    participant_medians: dict[str, dict[str, float]] = defaultdict(dict)
    for tool in tools:
        tool_rows = [row for row in rows if row["tool"] == tool]
        completed = [row["completion"] for row in tool_rows if not row["abandoned"]]
        abandoned = sum(1 for row in tool_rows if row["abandoned"])
        confidence = [row["confidence"] for row in tool_rows]
        corrections = [row["corrections"] for row in tool_rows]
        undos = [row["undo_count"] for row in tool_rows]
        misselections = [row["misselections"] for row in tool_rows]

        if not completed:
            fail(f"{tool}: every task was abandoned; cannot compute completion statistics")

        print(
            f"- {tool}: median={statistics.median(completed):.2f}s "
            f"P95={percentile(completed, 0.95):.2f}s "
            f"abandoned={abandoned}/{len(tool_rows)} "
            f"confidence_median={statistics.median(confidence):.2f} "
            f"corrections_mean={statistics.fmean(corrections):.2f} "
            f"undos_mean={statistics.fmean(undos):.2f} "
            f"misselections_mean={statistics.fmean(misselections):.2f}"
        )

        for participant in participants:
            values = [
                row["completion"]
                for row in tool_rows
                if row["participant"] == participant and not row["abandoned"]
            ]
            if values:
                participant_medians[participant][tool] = statistics.median(values)

    print()
    print("Paired participant-level median differences vs Pose Studio:")
    pose_all = [row["completion"] for row in rows if row["tool"] == POSE_TOOL and not row["abandoned"]]
    pose_median = statistics.median(pose_all)
    for comparator in tools:
        if comparator == POSE_TOOL:
            continue
        paired_seconds: list[float] = []
        faster_count = 0
        for participant in participants:
            medians = participant_medians.get(participant, {})
            if POSE_TOOL not in medians or comparator not in medians:
                continue
            delta = medians[comparator] - medians[POSE_TOOL]
            paired_seconds.append(delta)
            if delta > 0:
                faster_count += 1
        comparator_all = [row["completion"] for row in rows if row["tool"] == comparator and not row["abandoned"]]
        comparator_median = statistics.median(comparator_all)
        advantage_pct = ((comparator_median - pose_median) / comparator_median * 100.0) if comparator_median else math.nan
        print(
            f"- {comparator}: paired median delta={statistics.median(paired_seconds):.2f}s "
            f"Pose-faster participants={faster_count}/{len(paired_seconds)} "
            f"aggregate median advantage={advantage_pct:.1f}%"
        )

    print()
    print("No automatic PASS is emitted. BENCHMARK_PROTOCOL.md requires the observed advantage to be material,")
    print("consistent, and not purchased with worse confidence/abandonment before a speed claim is allowed.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path, help="benchmark CSV following docs/BENCHMARK_RESULTS_TEMPLATE.csv")
    args = parser.parse_args()
    rows = load_rows(args.csv)
    validate_study(rows)
    summarize(rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
