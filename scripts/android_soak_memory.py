#!/usr/bin/env python3
"""Shared physical-soak memory trend helpers."""

WARMUP_SECONDS = 5 * 60
MIN_POST_WARMUP_SAMPLES = 12
MAX_PSS_SLOPE_KB_PER_HOUR = 64 * 1024
MAX_COMPONENT_SLOPE_KB_PER_HOUR = 32 * 1024

MEMORY_KEYS = ("pssKb", "javaHeapKb", "nativeHeapKb", "graphicsKb")


def linear_slope_kb_per_hour(samples, key, warmup_seconds=WARMUP_SECONDS):
    points = [
        (float(sample["elapsedSeconds"]) / 3600.0, float(sample[key]))
        for sample in samples
        if int(sample["elapsedSeconds"]) >= warmup_seconds
    ]
    if len(points) < MIN_POST_WARMUP_SAMPLES:
        raise ValueError(
            f"memory slope sample floor failed for {key}: "
            f"{len(points)} < {MIN_POST_WARMUP_SAMPLES} after {warmup_seconds}s warm-up"
        )
    mean_x = sum(x for x, _ in points) / len(points)
    mean_y = sum(y for _, y in points) / len(points)
    denominator = sum((x - mean_x) ** 2 for x, _ in points)
    if denominator <= 0:
        raise ValueError(f"memory slope time range collapsed for {key}")
    return sum((x - mean_x) * (y - mean_y) for x, y in points) / denominator


def memory_evidence(samples):
    if not samples:
        raise ValueError("memory evidence has no retained samples")
    peaks = {f"peak{key[0].upper()}{key[1:]}": max(int(sample[key]) for sample in samples) for key in MEMORY_KEYS}
    slopes = {f"{key}SlopeKbPerHour": linear_slope_kb_per_hour(samples, key) for key in MEMORY_KEYS}
    return peaks, slopes


def enforce_memory_slopes(slopes):
    if slopes["pssKbSlopeKbPerHour"] > MAX_PSS_SLOPE_KB_PER_HOUR:
        raise ValueError(
            "post-warm-up PSS growth slope exceeded: "
            f"{slopes['pssKbSlopeKbPerHour']:.1f} > {MAX_PSS_SLOPE_KB_PER_HOUR} KB/hour"
        )
    for key in ("javaHeapKb", "nativeHeapKb", "graphicsKb"):
        field = f"{key}SlopeKbPerHour"
        if slopes[field] > MAX_COMPONENT_SLOPE_KB_PER_HOUR:
            raise ValueError(
                f"post-warm-up {key} growth slope exceeded: "
                f"{slopes[field]:.1f} > {MAX_COMPONENT_SLOPE_KB_PER_HOUR} KB/hour"
            )
