#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "apps/jingdu/android/app/src/main/java"
ACTIVE = [ROOT / ".github", ROOT / "apps/jingdu/android", ROOT / "scripts"]
GENERATION_PATTERNS = (
    re.compile(r"(?i)(?:reader[a-z0-9_-]*|jingdu|smart[_ -]?clean)[_ -]?" + "v" + "3" + r"\b"),
    re.compile(r"(?i)smart[_ -]?clean[_ -]?" + "3" + r"\b"),
)
REQUIRED = ("BookRepository.kt", "ChineseTextConverter.kt", "NativeCore.kt", "NativeIndexCache.kt", "ReaderController.kt", "ReaderTextPresentation.kt", "SmartCleanRefiner.kt", "TtsController.kt", "ReaderScreen.kt", "ReaderInsightsPanels.kt")
errors = []
java = sorted(MAIN.rglob("*.java"))
if java:
    errors.append("Android main source must be Kotlin-only: " + ", ".join(str(x.relative_to(ROOT)) for x in java))
for name in REQUIRED:
    if not (MAIN / "com/junchen/jingdu" / name).is_file():
        errors.append(f"missing canonical Kotlin source: {name}")
for root in ACTIVE:
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for pattern in GENERATION_PATTERNS:
            if pattern.search(path.name) or pattern.search(text):
                errors.append(f"forbidden generation residue {pattern.pattern!r}: {path.relative_to(ROOT)}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("android source conventions: OK")
