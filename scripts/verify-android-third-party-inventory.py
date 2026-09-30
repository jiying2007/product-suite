#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
gradle = (ROOT / "apps/jingdu/android/app/build.gradle").read_text(encoding="utf-8")
doc = (ROOT / "docs/ANDROID_THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8")

# Exact-version production declarations. Versionless Compose modules are governed by the checked BOM.
declared = set()
for match in re.finditer(r'^\s*implementation\s+"([^"]+)"', gradle, re.MULTILINE):
    coordinate = match.group(1)
    if coordinate.count(":") >= 2:
        declared.add(coordinate)
for match in re.finditer(r'platform\("([^"]+)"\)', gradle):
    coordinate = match.group(1)
    if coordinate.count(":") >= 2:
        declared.add(coordinate)

required_static = {
    "androidx.compose:compose-bom:2026.08.00",
    "androidx.activity:activity-compose:1.13.0",
    "androidx.core:core-ktx:1.19.0",
    "androidx.datastore:datastore:1.2.1",
    "androidx.room3:room3-runtime:3.0.3",
    "androidx.sqlite:sqlite-bundled:2.7.1",
    "androidx.media3:media3-common:1.11.0",
    "androidx.media3:media3-session:1.11.0",
    "androidx.profileinstaller:profileinstaller:1.4.1",
    "io.github.laisuk:openccjava:1.4.2",
    "com.android.billingclient:billing:9.1.0",
    "com.google.android.play:review:2.0.2",
}
missing_from_source = sorted(required_static - declared)
if missing_from_source:
    raise SystemExit("expected production dependency declarations missing: " + ", ".join(missing_from_source))

unreviewed = sorted(coord for coord in declared if coord not in doc and not coord.startswith("androidx.lifecycle:"))
if unreviewed:
    raise SystemExit("production dependencies missing attribution review record: " + ", ".join(unreviewed))

for required in (
    ":app:writeReleaseDependencyInventory",
    "releaseRuntimeClasspath",
    "jingdu-release-dependencies.txt",
):
    if required not in (ROOT / "apps/jingdu/android/app/build.gradle").read_text(encoding="utf-8") + doc:
        raise SystemExit(f"dependency inventory contract missing: {required}")

print(f"Android production dependency inventory contract OK ({len(declared)} exact direct coordinates)")
