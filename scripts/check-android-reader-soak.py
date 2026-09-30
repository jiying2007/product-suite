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

text = args.instrumentation_log.read_text(encoding="utf-8", errors="replace")
logcat = args.logcat.read_text(encoding="utf-8", errors="replace")
if args.duration_minutes not in (60, 180):
    raise SystemExit("reader soak qualification supports only 60 or 180 minutes")

sample_re = re.compile(
    r"jingdu\.readerSoakSample=(?:durationMinutes=(?P<duration>\d+);pageTurns=(?P<turns>\d+);"
    r"position=(?P<position>-?\d+);pssKb=(?P<pss>\d+);pid=(?P<pid>\d+))"
)
samples = [
    {
        "durationMinutes": int(m.group("duration")),
        "pageTurns": int(m.group("turns")),
        "position": int(m.group("position")),
        "pssKb": int(m.group("pss")),
        "pid": int(m.group("pid")),
    }
    for m in sample_re.finditer(text)
]
pass_re = re.compile(
    r"jingdu\.readerSoakPass=durationMinutes=(?P<duration>\d+);pageTurns=(?P<turns>\d+);"
    r"start=(?P<start>-?\d+);end=(?P<end>-?\d+);peakPssKb=(?P<pss>\d+);pid=(?P<pid>\d+)"
)
passed = pass_re.search(text)
if passed is None:
    raise SystemExit("reader soak completion proof missing")
if int(passed.group("duration")) != args.duration_minutes:
    raise SystemExit("reader soak duration proof mismatch")
minimum_samples = args.duration_minutes - 2
if len(samples) < minimum_samples:
    raise SystemExit(f"reader soak retained sample floor failed: {len(samples)} < {minimum_samples}")
pids = {sample["pid"] for sample in samples} | {int(passed.group("pid"))}
if len(pids) != 1:
    raise SystemExit(f"reader process identity changed during soak: {sorted(pids)}")
peak_pss = max([sample["pssKb"] for sample in samples] + [int(passed.group("pss"))])
if peak_pss <= 0 or peak_pss > 512 * 1024:
    raise SystemExit(f"reader soak peak PSS outside product ceiling: {peak_pss}KB")
if int(passed.group("end")) <= int(passed.group("start")):
    raise SystemExit("reader soak did not prove forward reading progress")
if int(passed.group("turns")) < args.duration_minutes * 20:
    raise SystemExit("reader soak did not retain enough real page-turn inputs")

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
    "sampleCount": len(samples),
    "pageTurns": int(passed.group("turns")),
    "startPosition": int(passed.group("start")),
    "endPosition": int(passed.group("end")),
    "pid": int(passed.group("pid")),
    "peakPssKb": peak_pss,
    "maxPssKb": 512 * 1024,
    "samples": samples,
    "result": "PASS",
}
args.summary_json.parent.mkdir(parents=True, exist_ok=True)
args.summary_json.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(
    f"Reader physical soak PASS: {args.duration_minutes} min, "
    f"{summary['pageTurns']} page turns, peakPss={peak_pss}KB, samples={len(samples)}"
)
