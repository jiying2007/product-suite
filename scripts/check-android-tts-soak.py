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
parser.add_argument(
    "--mode",
    choices=("background", "foreground-continuous", "foreground-continuous-stress"),
    required=True,
)
parser.add_argument("--summary-json", type=Path, required=True)
args = parser.parse_args()

if args.duration_minutes not in (30, 60):
    raise SystemExit("TTS soak qualification supports only 30 or 60 minutes")

text = args.instrumentation_log.read_text(encoding="utf-8", errors="replace")
logcat = args.logcat.read_text(encoding="utf-8", errors="replace")

sample_re = re.compile(
    r"jingdu\.ttsSoakSample=durationMinutes=(?P<duration>\d+);mode=(?P<mode>[^;]+);sample=(?P<sample>\d+);"
    r"elapsedSeconds=(?P<elapsed>\d+);progress=(?P<progress>\d+);delta=(?P<delta>\d+);"
    r"pssKb=(?P<pss>\d+);javaHeapKb=(?P<java>\d+);nativeHeapKb=(?P<native>\d+);"
    r"graphicsKb=(?P<graphics>\d+);pid=(?P<pid>\d+);runtimeActive=(?P<active>true|false);"
    r"runtimePlaying=(?P<playing>true|false);foregroundPosition=(?P<foreground>-?\d+)"
)
samples = [
    {
        "durationMinutes": int(m.group("duration")),
        "mode": m.group("mode"),
        "sample": int(m.group("sample")),
        "elapsedSeconds": int(m.group("elapsed")),
        "progress": int(m.group("progress")),
        "delta": int(m.group("delta")),
        "pssKb": int(m.group("pss")),
        "javaHeapKb": int(m.group("java")),
        "nativeHeapKb": int(m.group("native")),
        "graphicsKb": int(m.group("graphics")),
        "pid": int(m.group("pid")),
        "runtimeActive": m.group("active") == "true",
        "runtimePlaying": m.group("playing") == "true",
        "foregroundPosition": int(m.group("foreground")),
    }
    for m in sample_re.finditer(text)
]

pass_re = re.compile(
    r"jingdu\.ttsSoakPass=durationMinutes=(?P<duration>\d+);mode=(?P<mode>[^;]+);samples=(?P<samples>\d+);"
    r"start=(?P<start>\d+);end=(?P<end>\d+);advancingSamples=(?P<advancing>\d+);"
    r"peakPssKb=(?P<pss>\d+);peakJavaHeapKb=(?P<java>\d+);peakNativeHeapKb=(?P<native>\d+);"
    r"peakGraphicsKb=(?P<graphics>\d+);pid=(?P<pid>\d+);foregroundPosition=(?P<foreground>-?\d+)"
)
passed = pass_re.search(text)
if passed is None:
    raise SystemExit("TTS soak completion proof missing")
if int(passed.group("duration")) != args.duration_minutes:
    raise SystemExit("TTS soak duration proof mismatch")
if passed.group("mode") != args.mode or any(sample["mode"] != args.mode for sample in samples):
    raise SystemExit("TTS soak mode proof mismatch")
if int(passed.group("samples")) != len(samples):
    raise SystemExit("TTS soak retained sample count proof mismatch")

minimum_samples = args.duration_minutes * 6 - 3
if len(samples) < minimum_samples:
    raise SystemExit(f"TTS soak retained 10s sample floor failed: {len(samples)} < {minimum_samples}")

pids = {sample["pid"] for sample in samples} | {int(passed.group("pid"))}
if len(pids) != 1:
    raise SystemExit(f"TTS process identity changed during soak: {sorted(pids)}")
if not all(sample["runtimeActive"] for sample in samples):
    raise SystemExit("TTS runtime lost active service state in retained evidence")

elapsed = [sample["elapsedSeconds"] for sample in samples]
if any(current <= previous for previous, current in zip(elapsed, elapsed[1:])):
    raise SystemExit("TTS retained sample time is not strictly increasing")

progresses = [sample["progress"] for sample in samples]
if any(current < previous for previous, current in zip(progresses, progresses[1:])):
    raise SystemExit("TTS persisted progress moved backwards during soak")

advancing = sum(1 for sample in samples if sample["delta"] > 0)
# Preserve the prior 15-second progression SLO while sampling memory more densely at 10 seconds.
required_advancing = max(3, int(args.duration_minutes * 4 * 0.80))
if advancing < required_advancing:
    raise SystemExit(f"TTS progress advancing sample floor failed: {advancing} < {required_advancing}")

start = int(passed.group("start"))
end = int(passed.group("end"))
if end <= start:
    raise SystemExit(f"TTS soak made no forward progress: start={start} end={end}")

if args.mode != "background":
    foreground = [sample["foregroundPosition"] for sample in samples]
    if not foreground or any(value < 0 for value in foreground):
        raise SystemExit("foreground continuous TTS position evidence missing")
    if any(current < previous for previous, current in zip(foreground, foreground[1:])):
        raise SystemExit("foreground continuous Reader position moved backwards")
    if int(passed.group("foreground")) < foreground[-1]:
        raise SystemExit("foreground continuous completion position regressed")

peak_pss = max([sample["pssKb"] for sample in samples] + [int(passed.group("pss"))])
if peak_pss <= 0 or peak_pss > 512 * 1024:
    raise SystemExit(f"TTS soak peak PSS outside product ceiling: {peak_pss}KB")
if any(sample[key] < 0 for sample in samples for key in ("javaHeapKb", "nativeHeapKb", "graphicsKb")):
    raise SystemExit("TTS soak component memory evidence missing")

try:
    memory_peaks, memory_slopes = memory_evidence(samples)
    memory_peaks["peakPssKb"] = peak_pss
    memory_peaks["peakJavaHeapKb"] = max(memory_peaks["peakJavaHeapKb"], int(passed.group("java")))
    memory_peaks["peakNativeHeapKb"] = max(memory_peaks["peakNativeHeapKb"], int(passed.group("native")))
    memory_peaks["peakGraphicsKb"] = max(memory_peaks["peakGraphicsKb"], int(passed.group("graphics")))
    enforce_memory_slopes(memory_slopes)
except ValueError as error:
    raise SystemExit(str(error)) from error

bad_patterns = (
    r"ANR in com\.junchen\.jingdu",
    r"Process: com\.junchen\.jingdu.*FATAL EXCEPTION",
    r"com\.junchen\.jingdu.*OutOfMemoryError",
    r"ForegroundServiceDidNotStartInTimeException",
)
for pattern in bad_patterns:
    if re.search(pattern, logcat, re.IGNORECASE | re.DOTALL):
        raise SystemExit(f"TTS soak target failure marker matched: {pattern}")

summary = {
    "durationMinutes": args.duration_minutes,
    "mode": args.mode,
    "sampleIntervalSeconds": 10,
    "memoryWarmupSeconds": WARMUP_SECONDS,
    "sampleCount": len(samples),
    "advancingSamples": advancing,
    "requiredAdvancingSamples": required_advancing,
    "startProgress": start,
    "endProgress": end,
    "progressDelta": end - start,
    "pid": int(passed.group("pid")),
    **memory_peaks,
    "maxPssKb": 512 * 1024,
    **memory_slopes,
    "maxPssSlopeKbPerHour": MAX_PSS_SLOPE_KB_PER_HOUR,
    "maxComponentSlopeKbPerHour": MAX_COMPONENT_SLOPE_KB_PER_HOUR,
    "runtimeActiveSamples": sum(1 for sample in samples if sample["runtimeActive"]),
    "runtimePlayingSamples": sum(1 for sample in samples if sample["runtimePlaying"]),
    "samples": samples,
    "result": "PASS",
}
args.summary_json.parent.mkdir(parents=True, exist_ok=True)
args.summary_json.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(
    f"TTS physical soak PASS: mode={args.mode}, {args.duration_minutes} min, "
    f"progressDelta={summary['progressDelta']}, advancing={advancing}/{len(samples)}, "
    f"peakPss={peak_pss}KB, pssSlope={memory_slopes['pssKbSlopeKbPerHour']:.1f}KB/h"
)
