#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP_GRADLE="apps/jingdu/android/app/build.gradle"
CI=".github/workflows/ci.yml"
PHYSICAL=".github/workflows/android-physical-release-performance.yml"
MANIFEST="apps/jingdu/android/app/src/main/AndroidManifest.xml"
FUNCTIONAL="scripts/run-android-functional-tests-ci.sh"
NATIVE_COMPAT="scripts/verify-android-16k-page-size.sh"
READER_FIXTURE="apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/ReaderInstrumentationFixture.kt"

# Production-native compatibility and symbolication must not regress silently.
grep -Fq 'ndkVersion = "29.0.14206865"' "$APP_GRADLE"
grep -Fq 'debugSymbolLevel = "FULL"' "$APP_GRADLE"
test -f "$NATIVE_COMPAT"
grep -Fq -- '"$ZIPALIGN" -c -P 16 -v 4' "$NATIVE_COMPAT"
grep -Fq 'llvm-readelf' "$NATIVE_COMPAT"
grep -Fq 'arm64-v8a x86_64' "$NATIVE_COMPAT"
grep -Fq '64-bit ELF alignment' "$NATIVE_COMPAT"

# AndroidTest must execute on an Android 15+ 16 KiB runtime. Hosted functional CI currently pins
# the API 36 Google APIs 16 KiB image: Android's current 16 KiB guidance allows VanillaIceCream or
# higher, while the old API 35 experimental image has proven unstable in system_server/ART under
# hosted instrumentation. Functional CI owns app instrumentation only; Macrobenchmark/Baseline
# Profile stay isolated in android-performance so the two suites cannot replace/uninstall each
# other's target package while running. Reader UI tests must seed a real private BookRepository
# source/document revision rather than relying on stale app data or synthetic ids that
# ReaderViewportEngine correctly rejects on a clean device.
test -f "$FUNCTIONAL"
grep -Fq 'android-functional:' "$CI"
grep -Fq ':app:connectedDebugAndroidTest' "$FUNCTIONAL"
if grep -Fq ':macrobenchmark:connectedDebugAndroidTest' "$FUNCTIONAL"; then
  echo "functional gate must not execute macrobenchmark instrumentation" >&2
  exit 1
fi
grep -Fq 'system-images;android-36;google_apis_ps16k;x86_64' "$FUNCTIONAL"
grep -Fq 'ro.build.version.sdk' "$FUNCTIONAL"
grep -Fq 'getconf PAGE_SIZE' "$FUNCTIONAL"
grep -Fq '"16384"' "$FUNCTIONAL"
grep -Fq 'chmod 666 /dev/kvm' "$FUNCTIONAL"
grep -Fq 'exited before boot completed' "$FUNCTIONAL"
grep -Fq 'Functional checkout SHA:' "$FUNCTIONAL"
grep -Fq 'JingduUiTest source SHA256:' "$FUNCTIONAL"
grep -Fq -- '--no-build-cache clean :app:connectedDebugAndroidTest' "$FUNCTIONAL"
grep -Fq 'OK \(15 tests\)' "$FUNCTIONAL"
if grep -Eq 'dalvik\.vm\.(gctype|backgroundgctype)' "$FUNCTIONAL"; then
  echo "functional gate must not override the system image ART collector" >&2
  exit 1
fi
test -f "$READER_FIXTURE"
grep -Fq 'BookRepository(appContext)' "$READER_FIXTURE"
grep -Fq 'repository.importUri' "$READER_FIXTURE"
grep -Fq 'ReaderInstrumentationFixture.book(context)' apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/JingduUiTest.kt
grep -Fq 'ReaderInstrumentationFixture.book(context)' apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/ReaderPagingRegressionTest.kt
test -f apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/NativePageSizeSmokeTest.kt
grep -Fq 'NativeCore.fileSha256' apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/NativePageSizeSmokeTest.kt
grep -Fq 'ReaderController(false)' apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/NativePageSizeSmokeTest.kt
grep -Fq 'android-native-compat:' "$CI"
grep -Fq 'android-functional, android-native-compat, android-performance' "$CI"

# Physical evidence is explicitly bound to an immutable ref/SHA and never accepts an emulator.
grep -Fq 'source_ref:' "$PHYSICAL"
grep -Fq 'JINGDU_QUALIFIED_SOURCE_SHA' "$PHYSICAL"
grep -Fq 'Physical Release gate refuses emulator/generic devices' scripts/run-android-physical-release-performance.sh
grep -Fq 'source_sha=' scripts/run-android-physical-release-performance.sh

# Privacy moat remains architectural: diagnostics are local/bounded and the APK still has no INTERNET.
if grep -Fq 'android.permission.INTERNET' "$MANIFEST"; then
  echo "Android Manifest unexpectedly requests INTERNET" >&2
  exit 1
fi
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ProductErrorLog.kt
grep -Fq 'never exception messages, paths, URIs, book names/text, search queries or purchase' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ProductErrorLog.kt
grep -Fq 'containsPurchaseTokens' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt

# Commercial behavior and immutable private publication are isolated into testable policies.
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BillingEntitlementPolicy.kt
test -f apps/jingdu/android/app/src/test/java/com/junchen/jingdu/BillingEntitlementPolicyTest.kt
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/PrivateFilePublisher.kt
test -f apps/jingdu/android/app/src/test/java/com/junchen/jingdu/PrivateFilePublisherTest.kt

# Bounded TTS semantic navigation stays host-testable while real engine/routes remain device evidence.
test -f apps/jingdu/android/app/src/test/java/com/junchen/jingdu/TtsSemanticNavigatorTest.kt

# Smart Clean held-out evidence must remain production-scale and independent from training rows.
test -f quality/smartclean/eval-v2-matrix.json
python3 scripts/verify-smartclean-model.py

echo "Product maturity source contracts OK"
