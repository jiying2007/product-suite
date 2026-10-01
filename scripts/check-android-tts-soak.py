#!/usr/bin/env python3
import argparse
import json
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("instrumentation_log", type=Path)
parser.add_argument("logcat", type=Path)
parser.add_argument("--duration-minutes", type=int, required=True)
parser.add_argument("--summary-json", type=Path, required=True)
args = parser.parse_args()

if args.duration_minutes not in (30, 60):
    raise SystemExit("TTS soak qualification supports only 30 or 60 minutes")

text = args.instrumentation_log.read_text(encoding="utf-8", errors="replace")
logcat = args.logcat.read_text(encoding="utf-8", errors="replace")

sample_re = re.compile(
    r"jingdu\.ttsSoakSample=durationMinutes=(?P<duration>\d+);sample=(?P<sample>\d+);"
    r"progress=(?P<progress>\d+);delta=(?P<delta>\d+);pssKb=(?P<pss>\d+);"
    r"pid=(?P<pid>\d+);runtimePlaying=(?P<playing>true|false)"
)
samples = [
    {
        "durationMinutes": int(m.group("duration")),
        "sample": int(m.group("sample")),
        "progress": int(m.group("progress")),
        "delta": int(m.group("delta")),
        "pssKb": int(m.group("pss")),
        "pid": int(m.group("pid")),
        "runtimePlaying": m.group("playing") == "true",
    }
    for m in sample_re.finditer(text)
]

pass_re = re.compile(
    r"jingdu\.ttsSoakPass=durationMinutes=(?P<duration>\d+);samples=(?P<samples>\d+);"
    r"start=(?P<start>\d+);end=(?P<end>\d+);advancingSamples=(?P<advancing>\d+);"
    r"peakPssKb=(?P<pss>\d+);pid=(?P<pid>\d+)"
)
passed = pass_re.search(text)
if passed is None:
    raise SystemExit("TTS soak completion proof missing")
if int(passed.group("duration")) != args.duration_minutes:
    raise SystemExit("TTS soak duration proof mismatch")

minimum_samples = args.duration_minutes - 2
if len(samples) < minimum_samples:
    raise SystemExit(f"TTS soak retained sample floor failed: {len(samples)} < {minimum_samples}")

pids = {sample["pid"] for sample in samples} | {int(passed.group("pid"))}
if len(pids) != 1:
    raise SystemExit(f"TTS process identity changed during soak: {sorted(pids)}")

progresses = [sample["progress"] for sample in samples]
if any(current < previous for previous, current in zip(progresses, progresses[1:])):
    raise SystemExit("TTS persisted progress moved backwards during soak")

advancing = sum(1 for sample in samples if sample["delta"] > 0)
required_advancing = max(3, int(len(samples) * 0.80))
if advancing < required_advancing:
    raise SystemExit(f"TTS progress advancing sample floor failed: {advancing} < {required_advancing}")

start = int(passed.group("start"))
end = int(passed.group("end"))
if end <= start:
    raise SystemExit(f"TTS soak made no forward progress: start={start} end={end}")

peak_pss = max([sample["pssKb"] for sample in samples] + [int(passed.group("pss"))])
if peak_pss <= 0 or peak_pss > 512 * 1024:
    raise SystemExit(f"TTS soak peak PSS outside product ceiling: {peak_pss}KB")

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
    "sampleCount": len(samples),
    "advancingSamples": advancing,
    "requiredAdvancingSamples": required_advancing,
    "startProgress": start,
    "endProgress": end,
    "progressDelta": end - start,
    "pid": int(passed.group("pid")),
    "peakPssKb": peak_pss,
    "maxPssKb": 512 * 1024,
    "runtimePlayingSamples": sum(1 for sample in samples if sample["runtimePlaying"]),
    "samples": samples,
    "result": "PASS",
}
args.summary_json.parent.mkdir(parents=True, exist_ok=True)
args.summary_json.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(
    f"TTS physical soak PASS: {args.duration_minutes} min, "
    f"progressDelta={summary['progressDelta']}, advancing={advancing}/{len(samples)}, "
    f"peakPss={peak_pss}KB"
)
