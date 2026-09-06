#!/usr/bin/env bash
set -euo pipefail

# The generic reading-experience entry point follows the current prelaunch architecture only.
# Reader V2 implementation-specific gates were intentionally removed before the first store launch.
bash ./scripts/verify-reader.sh

# TXT repair stays presentation-only and projection-backed.
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/SmartLayout.kt
grep -Fq 'SmartLayout.present(intermediate).text' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
grep -Fq 'TextProjection.between(source, intermediate)' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderPresentationPipeline.kt
test -f apps/jingdu/android/app/src/test/java/com/junchen/jingdu/SmartLayoutTest.kt

# CJK line breaking improves punctuation/phrase boundaries without replacing the fixed-cost paged
# BREAK_STRATEGY_SIMPLE hot-path strategy.
grep -Fq 'wordBreak = LineBreak.WordBreak.Phrase' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderTypographySpec.kt
grep -Fq 'LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
grep -Fq 'LineBreaker.BREAK_STRATEGY_SIMPLE' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderViewportEngine.kt
test -f apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderCjkTypographyTest.kt

# Visual continuity is transient source-coordinate math; no second persisted reading coordinate is introduced.
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderVisualContinuity.kt
grep -Fq 'ReaderVisualContinuity.centerAnchor' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
grep -Fq 'actions.onSyncTtsPosition(target)' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
test -f apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderVisualContinuityTest.kt

# System reduce-motion is runtime-only; the saved Reader preference is preserved and restored.
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSystemMotion.kt
grep -Fq 'ANIMATOR_DURATION_SCALE' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSystemMotion.kt
grep -Fq 'readerEffectivePageAnimation' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt

# Foldables use the existing adaptive two-column reader rather than a separate document model.
grep -Fq 'val bookPosture: Boolean get() = hasHinge && !tabletop' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
grep -Fq 'bookPosture ||' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderRoute.kt
test -f apps/jingdu/android/app/src/test/java/com/junchen/jingdu/ReaderAdaptiveLayoutTest.kt

# Reading-basic tools stay real and local: system Process Text dictionary, chapter pace, and literal
# TTS pronunciation projection with portable text-free user-asset backup.
grep -Fq 'Intent.ACTION_PROCESS_TEXT' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -Fq 'settings.dictionaryProcessTextEnabled' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
grep -Fq 'chapterRemainingMinutes' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderStatsStore.kt
grep -Fq 'reader_chapter_remaining' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderScreen.kt
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsPronunciationStore.kt
grep -Fq 'sourceChunk.projection.compose(spoken.projection)' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/TtsController.kt
grep -Fq 'ttsPronunciation' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/UserBackup.kt
test -f apps/jingdu/android/app/src/androidTest/java/com/junchen/jingdu/TtsPronunciationBackupTest.kt

# User-facing scenarios are stable enum values with tuned product bundles, not a settings migration.
test -f apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderReadingPresets.kt
grep -Fq 'applyProductPreset(preset)' apps/jingdu/android/app/src/main/java/com/junchen/jingdu/ReaderSettingsScreen.kt

echo 'Reading experience contract OK: Smart Layout/CJK/reflow/foldable/motion/dictionary/chapter/TTS/source-offset invariants aligned'
