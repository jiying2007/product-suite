#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "apps/jingdu/android/app/src/main/res"
LOCALE_DIRS = {
    "en-US": RES / "values",
    "zh-Hans": RES / "values-b+zh+Hans",
    "zh-Hant": RES / "values-b+zh+Hant",
}
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
SMOKE_KEYS = {
    "app_title", "library_tagline", "library_tagline_terminal", "empty_title", "select_txt",
    "full_text_search", "chapters", "reading_progress", "smart_clean", "smart_clean_undo",
    "smart_clean_risk_high", "library_filter_favorites", "library_sort_progress",
    "unlock_pro_apply", "offline_voice", "local_asset_backup", "billing_unavailable", "error_import",
}


def strings(directory: Path) -> dict[str, str]:
    if not directory.is_dir():
        raise SystemExit(f"missing locale resource directory: {directory.relative_to(ROOT)}")
    files = sorted(directory.glob("strings*.xml"))
    if not files:
        raise SystemExit(f"missing locale string resources: {directory.relative_to(ROOT)}")
    values: dict[str, str] = {}
    for path in files:
        root = ET.parse(path).getroot()
        for node in root:
            if node.tag != "string" or "name" not in node.attrib:
                continue
            name = node.attrib["name"]
            value = "".join(node.itertext()).strip()
            if name in values:
                raise SystemExit(f"duplicate localized string {name} in {directory.relative_to(ROOT)}")
            values[name] = value
    if len(values) < 100:
        raise SystemExit(f"suspiciously small locale resource set: {directory.relative_to(ROOT)} ({len(values)} keys)")
    empty = sorted(name for name, value in values.items() if not value)
    if empty:
        raise SystemExit(f"empty localized strings in {directory.relative_to(ROOT)}: {empty}")
    return values


localized = {locale: strings(directory) for locale, directory in LOCALE_DIRS.items()}
base_keys = set(localized["en-US"])
for locale, values in localized.items():
    current = set(values)
    missing = sorted(base_keys - current)
    extra = sorted(current - base_keys)
    if missing or extra:
        raise SystemExit(f"locale key drift {locale}: missing={missing} extra={extra}")
    smoke_missing = sorted(SMOKE_KEYS - current)
    if smoke_missing:
        raise SystemExit(f"locale smoke keys missing {locale}: {smoke_missing}")

for key in sorted(base_keys):
    expected = FORMAT.findall(localized["en-US"][key])
    for locale in ("zh-Hans", "zh-Hant"):
        actual = FORMAT.findall(localized[locale][key])
        if actual != expected:
            raise SystemExit(f"format placeholder drift {key} {locale}: expected={expected} actual={actual}")

if localized["zh-Hans"]["app_title"] == localized["zh-Hant"]["app_title"]:
    raise SystemExit("Simplified and Traditional app titles unexpectedly identical")
if localized["en-US"]["app_title"] == localized["zh-Hans"]["app_title"]:
    raise SystemExit("English fallback unexpectedly matches Simplified Chinese title")

props = (RES / "resources.properties").read_text(encoding="utf-8").strip()
if props != "unqualifiedResLocale=en-US":
    raise SystemExit("English must remain the unqualified resource fallback")

manifest = (ROOT / "apps/jingdu/android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if 'android:label="@string/app_name"' not in manifest:
    raise SystemExit("manifest label must use @string/app_name")

build = (ROOT / "apps/jingdu/android/app/build.gradle").read_text(encoding="utf-8")
if "generateLocaleConfig = true" not in build:
    raise SystemExit("AGP automatic LocaleConfig generation must remain enabled")

cjk = re.compile(r"[\u3400-\u9fff]")
base = ROOT / "apps/jingdu/android/app/src/main/java/com/junchen/jingdu"
presentation_files = [
    "JingduApp.kt",
    "LibraryScreen.kt",
    "ReaderScreen.kt",
    "ReaderSheets.kt",
    "ReaderQuickPanels.kt",
    "ReaderSettingsScreen.kt",
    "ReaderInsightsPanels.kt",
]
for name in presentation_files:
    path = base / name
    if not path.is_file():
        raise SystemExit(f"localized presentation asset missing: {name}")
    text = path.read_text(encoding="utf-8")
    if cjk.search(text):
        raise SystemExit(f"hard-coded CJK UI copy found in {name}; move it to string resources")
    if "stringResource(R.string." not in text:
        raise SystemExit(f"localized UI resource use missing in {name}")

for name in ["MainActivity.kt", "BillingManager.kt"]:
    text = (base / name).read_text(encoding="utf-8")
    if cjk.search(text):
        raise SystemExit(f"hard-coded CJK runtime copy found in {name}; use R.string resources")
    if "R.string." not in text:
        raise SystemExit(f"runtime localization resource use missing in {name}")

ui_test = (base.parents[4] / "androidTest/java/com/junchen/jingdu/JingduUiTest.kt").read_text(encoding="utf-8")
required_test_contract = (
    "InstrumentationRegistry.getInstrumentation().targetContext",
    "context.getString(R.string.",
    '"promo_repeated"',
)
if any(token not in ui_test for token in required_test_contract):
    raise SystemExit("Compose AndroidTest must resolve UI expectations from the active locale")

print("Android i18n contract OK: en-US / zh-Hans / zh-Hant; split resources/keys/placeholders/smoke aligned")
