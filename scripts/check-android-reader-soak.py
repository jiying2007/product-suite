#!/usr/bin/env python3
import argparse
import json
import re
from pathlib import Path

from android_soak_memory import (
    MAX_COMPONENT_SLOPE_KB_PER_HOUR,
    MAX_PSS_SLOPE_KB_PER_HOUR,
    WARMUP_SECONDS,
    enforce_memory_slopes,
    memory_evidence,
)

parser = argparse.ArgumentParser()
parser.add_argument("instrumentation_log", type=Path)
parser.add_argument("logcat", type=Path)
parser.add_argument("--duration-minutes", type=int, required=True)
parser.add_argument("--mode", choices=("paged", "continuous-stress"), required=True)
parser.add_argument("--summary-json", type=Path, required=True)
args = parser.parse_args()

text = args.instrumentation_log.read_text(encoding="utf-8", errors="replace")
logcat = args.logcat.read_text(encoding="utf-8", errors="replace")
if args.duration_minutes not in (60, 180):
    raise SystemExit("reader soak qualification supports only 60 or 180 minutes")

sample_re = re.compile(
    r"jingdu\.readerSoakSample=durationMinutes=(?P<duration>\d+);mode=(?P<mode>[^;]+);"
    r"sample=(?P<sample>\d+);elapsedSeconds=(?P<elapsed>\d+);interactions=(?P<interactions>\d+);"
    r"position=(?P<position>-?\d+);pssKb=(?P<pss>\d+);javaHeapKb=(?P<java>\d+);"
    r"nativeHeapKb=(?P<native>\d+);graphicsKb=(?P<graphics>\d+);pid=(?P<pid>\d+)"
)
samples = [
    {
        "durationMinutes": int(m.group("duration")),
        "mode": m.group("mode"),
        "sample": int(m.group("sample")),
        "elapsedSeconds": int(m.group("elapsed")),
        "interactions": int(m.group("interactions")),
        "position": int(m.group("position")),
        "pssKb": int(m.group("pss")),
        "javaHeapKb": int(m.group("java")),
        "nativeHeapKb": int(m.group("native")),
        "graphicsKb": int(m.group("graphics")),
        "pid": int(m.group("pid")),
    }
    for m in sample_re.finditer(text)
]
pass_re = re.compile(
    r"jingdu\.readerSoakPass=durationMinutes=(?P<duration>\d+);mode=(?P<mode>[^;]+);"
    r"samples=(?P<samples>\d+);interactions=(?P<interactions>\d+);start=(?P<start>-?\d+);"
    r"end=(?P<end>-?\d+);peakPssKb=(?P<pss>\d+);peakJavaHeapKb=(?P<java>\d+);"
    r"peakNativeHeapKb=(?P<native>\d+);peakGraphicsKb=(?P<graphics>\d+);pid=(?P<pid>\d+)"
)
passed = pass_re.search(text)
if passed is None:
    raise SystemExit("reader soak completion proof missing")
if int(passed.group("duration")) != args.duration_minutes:
    raise SystemExit("reader soak duration proof mismatch")
if passed.group("mode") != args.mode or any(sample["mode"] != args.mode for sample in samples):
    raise SystemExit("reader soak mode proof mismatch")
if int(passed.group("samples")) != len(samples):
    raise SystemExit("reader soak retained sample count proof mismatch")

minimum_samples = args.duration_minutes * 6 - 3
if len(samples) < minimum_samples:
    raise SystemExit(f"reader soak retained 10s sample floor failed: {len(samples)} < {minimum_samples}")
pids = {sample["pid"] for sample in samples} | {int(passed.group("pid"))}
if len(pids) != 1:
    raise SystemExit(f"reader process identity changed during soak: {sorted(pids)}")

elapsed = [sample["elapsedSeconds"] for sample in samples]
if any(current <= previous for previous, current in zip(elapsed, elapsed[1:])):
    raise SystemExit("reader retained sample time is not strictly increasing")

peak_pss = max([sample["pssKb"] for sample in samples] + [int(passed.group("pss"))])
if peak_pss <= 0 or peak_pss > 512 * 1024:
    raise SystemExit(f"reader soak peak PSS outside product ceiling: {peak_pss}KB")
if any(sample[key] < 0 for sample in samples for key in ("javaHeapKb", "nativeHeapKb", "graphicsKb")):
    raise SystemExit("reader soak component memory evidence missing")
if int(passed.group("end")) <= int(passed.group("start")):
    raise SystemExit("reader soak did not prove forward reading progress")
if int(passed.group("interactions")) < args.duration_minutes * 20:
    raise SystemExit("reader soak did not retain enough real reading interactions")

try:
    memory_peaks, memory_slopes = memory_evidence(samples)
    enforce_memory_slopes(memory_slopes)
except ValueError as error:
    raise SystemExit(str(error)) from error

bad_patterns = (
    r"ANR in com\.junchen\.jingdu",
    r"Process: com\.junchen\.jingdu.*FATAL EXCEPTION",
    r"com\.junchen\.jingdu.*OutOfMemoryError",
)
for pattern in bad_patterns:
    if re.search(pattern, logcat, re.IGNORECASE | re.DOTALL):
        raise SystemExit(f"reader soak target failure marker matched: {pattern}")

summary = {
    "durationMinutes": args.duration_minutes,
    "mode": args.mode,
    "sampleIntervalSeconds": 10,
    "memoryWarmupSeconds": WARMUP_SECONDS,
    "sampleCount": len(samples),
    "interactions": int(passed.group("interactions")),
    "startPosition": int(passed.group("start")),
    "endPosition": int(passed.group("end")),
    "pid": int(passed.group("pid")),
    "peakPssKb": peak_pss,
    "maxPssKb": 512 * 1024,
    **memory_peaks,
    **memory_slopes,
    "maxPssSlopeKbPerHour": MAX_PSS_SLOPE_KB_PER_HOUR,
    "maxComponentSlopeKbPerHour": MAX_COMPONENT_SLOPE_KB_PER_HOUR,
    "samples": samples,
    "result": "PASS",
}
args.summary_json.parent.mkdir(parents=True, exist_ok=True)
args.summary_json.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(
    f"Reader physical soak PASS: {args.duration_minutes} min, "
    f"{summary['interactions']} interactions ({args.mode}), peakPss={peak_pss}KB, "
    f"pssSlope={memory_slopes['pssKbSlopeKbPerHour']:.1f}KB/h, samples={len(samples)}"
)
