#!/usr/bin/env python3
from android_soak_memory import (
    MAX_COMPONENT_SLOPE_KB_PER_HOUR,
    MAX_PSS_SLOPE_KB_PER_HOUR,
    enforce_memory_slopes,
    linear_slope_kb_per_hour,
    memory_evidence,
)


def sample(second, pss, java, native, graphics):
    return {
        "elapsedSeconds": second,
        "pssKb": pss,
        "javaHeapKb": java,
        "nativeHeapKb": native,
        "graphicsKb": graphics,
    }


stable = [
    sample(second, 180_000 + index * 2, 50_000 + index, 70_000 + index, 20_000)
    for index, second in enumerate(range(0, 1_800, 10))
]
peaks, slopes = memory_evidence(stable)
assert peaks["peakPssKb"] > 0
assert slopes["pssKbSlopeKbPerHour"] < MAX_PSS_SLOPE_KB_PER_HOUR
enforce_memory_slopes(slopes)

leaking = [
    sample(
        second,
        180_000 + index * 400,
        50_000 + index * 220,
        70_000 + index * 10,
        20_000 + index * 10,
    )
    for index, second in enumerate(range(0, 1_800, 10))
]
assert linear_slope_kb_per_hour(leaking, "pssKb") > MAX_PSS_SLOPE_KB_PER_HOUR
assert linear_slope_kb_per_hour(leaking, "javaHeapKb") > MAX_COMPONENT_SLOPE_KB_PER_HOUR
try:
    enforce_memory_slopes(memory_evidence(leaking)[1])
except ValueError:
    pass
else:
    raise AssertionError("leaking memory trend must fail the slope gate")

print("physical soak memory slope helper: OK")
