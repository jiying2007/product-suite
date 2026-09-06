#!/usr/bin/env bash
set -euo pipefail

required=(
  docs/PRODUCT.md docs/PERFORMANCE_SLO.md docs/SMART_CLEAN_ARCHITECTURE.md docs/COMPETITIVE_MOAT.md
  docs/GROWTH_MONETIZATION.md docs/RELEASE.md docs/PRODUCTION_READINESS.md docs/PRODUCT_MATURITY.md docs/READING_EXPERIENCE.md
  THIRD_PARTY_NOTICES.md third_party/NOTICE.md
  platform/text/native/tests/core_performance_gate_test.cpp
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartToc.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/FolderLibraryStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ProductErrorLog.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/PrivateFilePublisher.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BillingEntitlementPolicy.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPronunciationStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderMotionController.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderAnnotationStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderDatabase.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderFontStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderQuickPanels.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderInsightsPanels.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartLayout.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderVisualContinuity.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSystemMotion.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderReadingPresets.kt
  apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TextProjection.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/BillingEntitlementPolicyTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/PrivateFilePublisherTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderFoundationsTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/SmartLayoutTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderCjkTypographyTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderVisualContinuityTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderAdaptiveLayoutTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderSystemMotionTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderReadingPresetsTest.kt
  apps/jingdu/android/app/src/test/java/com/junchen/jingdu/TtsPronunciationStoreTest.kt
  apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/ProductDiagnosticsTest.kt
  apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/TtsPronunciationBackupTest.kt
  apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
  apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
  quality/smartclean/eval-v2-matrix.json
  scripts/check-android-performance-slo.py scripts/run-android-macrobenchmark-ci.sh
  scripts/run-android-functional-tests-ci.sh scripts/verify-android-16k-page-size.sh scripts/verify-product-maturity.sh
  scripts/train-smartclean-model.py scripts/verify-smartclean-model.py scripts/verify-reader.sh scripts/verify-reading-experience.sh
  scripts/publish-source-release.py scripts/finalize-draft-release.py .github/workflows/finalize-immutable-release.yml
)
for path in "${required[@]}"; do test -f "$path" || { echo "terminal-quality asset missing: $path" >&2; exit 1; }; done

grep -q 'Long TXT · Smart Clean · Fully local' docs/PRODUCT.md
grep -q 'TXT Doctor' docs/PRODUCT.md
grep -q 'Smart TOC' docs/PRODUCT.md
grep -q 'Precision is deliberately more important than recall' docs/COMPETITIVE_MOAT.md

grep -q 'JINGDU_PERF_FIXTURE_MIB' platform/text/native/tests/core_performance_gate_test.cpp
grep -q '1000' platform/text/native/tests/core_performance_gate_test.cpp
grep -q 'peakRssMiB' platform/text/native/tests/core_performance_gate_test.cpp
grep -q 'JINGDU_PERF_FIXTURE_MIB=960' platform/text/native/CMakeLists.txt

grep -q 'SAMPLE_WINDOWS = 8' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TxtDoctor.kt
grep -q 'MAX_PREVIEW_BYTES = 512 \* 1024' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ProgressiveImport.kt
grep -q 'ActivityResultContracts.OpenDocumentTree' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/LibraryScreen.kt
grep -q 'MAX_BOOKS = 100' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/BatchAutomation.kt
grep -q 'Manifest.permission.INTERNET !in permissions' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/PrivacyAudit.kt
grep -q 'enum class SmartCleanFeedback { NONE, KEEP, DELETE, PROTECT }' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartCleanFeedbackStore.kt
grep -q 'TinyLocalSemanticCandidateClassifier' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
python3 scripts/train-smartclean-model.py --verify-source apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt
python3 scripts/verify-smartclean-model.py

bash ./scripts/verify-reading-experience.sh
bash ./scripts/verify-product-maturity.sh
grep -q ':app:testDebugUnitTest' apps/jingdu/android/build.gradle
grep -q 'repeat(100_000)' apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderMotionControllerTest.kt
grep -q 'FrameTimingMetric' apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'pageTurn' apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'continuousScroll' apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'open100MiBTxt' apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/ReaderJourneyBenchmark.kt
grep -q 'Benchmark Novel' apps/jingdu/android/app/src/benchmark/java/com/junchen/jingdu/ReaderBenchmarkFixtureProvider.kt
grep -q 'device.executeShellCommand' apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
grep -q 'jingdu-reader' apps/jingdu/android/macrobenchmark/src/main/java/com/junchen/jingdu/macrobenchmark/BaselineProfileGenerator.kt
! grep -R -q 'Reader V2\|reader-v2' apps/jingdu/android/app/src/benchmark apps/jingdu/android/macrobenchmark

grep -q 'MediaSessionService' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt
grep -q 'SimpleBasePlayer' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderTtsPlayer.kt
grep -q 'previousSentence' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
grep -q 'previousParagraph' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsSemanticNavigator.kt
! grep -q 'android.media.session.MediaSession' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.kt

if grep -q 'android.permission.INTERNET' apps/jingdu/android/app/src/main/AndroidManifest.xml; then echo 'offline/privacy contract forbids INTERNET' >&2; exit 1; fi
if git grep -n -E 'converted(File|Path)|converted-[^ ]+\.txt|opencc.*\.txt' -- apps/jingdu/android/app/src/main/java; then echo 'full-book converted artifact found' >&2; exit 1; fi
if grep -qE 'normalizedFile|documentFile|ReaderController|File\(' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SemanticCandidateClassifier.kt; then echo 'semantic classifier must remain file-blind' >&2; exit 1; fi

for legacy in apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderV2Panels.kt apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderAdvancedSettingsSheet.kt apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPlaybackService.java scripts/verify-reader-v2.sh; do
  test ! -e "$legacy" || { echo "legacy Reader asset remains: $legacy" >&2; exit 1; }
done

test ! -f .github/workflows/source-release.yml
python3 -m py_compile scripts/publish-source-release.py scripts/finalize-draft-release.py
grep -Fq 'needs: [native-core, android, android-functional, android-native-compat, android-performance, harmony-contract, play-store-contract, terminal-contract]' .github/workflows/ci.yml
grep -Fq '"draft": True' scripts/publish-source-release.py
grep -Fq '"make_latest": "false"' scripts/publish-source-release.py
grep -Fq 'published release {tag} is missing required immutable assets' scripts/publish-source-release.py
grep -Fq 'published release {tag} did not become immutable' scripts/finalize-draft-release.py
grep -Fq 'github.event.workflow_run.conclusion == '\''success'\''' .github/workflows/finalize-immutable-release.yml
grep -Fq 'ref: ${{ github.event.workflow_run.head_sha }}' .github/workflows/finalize-immutable-release.yml

echo 'Terminal long-form / moat / Reader quality contract OK'
