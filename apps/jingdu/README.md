# Jingdu / 净读

Jingdu is the repository's current release-bearing product: an offline, privacy-first TXT reader focused on opening messy Chinese TXT correctly, safely cleaning distracting repeated/promotional text, and sustaining comfortable long-form reading.

## Platforms

- `android/` — Kotlin/Compose shell, Android lifecycle/TTS, Google Play Billing & Review integration, JNI, unit/instrumentation tests and Macrobenchmark.
- `harmony/` — HarmonyOS Stage/ArkUI + ArkTS shell and Node-API integration; source-complete/pre-release, with real HAP/device qualification requiring the configured HarmonyOS runner/toolchain.
- `../../platform/text/native/` — shared C++17 document semantics used by both shells through C ABI v2.

## Product invariants

- Book identity is SHA-256 of source bytes.
- Normalized/clean revisions are immutable and content-addressed; `.jdx` is disposable cache, not identity.
- Read/search/chapter/repair/speech/Smart Clean semantics after normalization come from the shared text engine rather than platform forks.
- UI locale and book language are independent.
- Smart Clean changes derived output only after explicit user Apply.
- Source files are never modified and Android does not directly request `INTERNET` for the current product architecture.
- Free remains a complete reader; `jingdu_pro_lifetime` is the current one-time Pro entitlement.

## Android verification

```bash
cd apps/jingdu/android
./gradlew --no-daemon --warning-mode all androidCheck
cd ../../..
./scripts/verify-android-i18n.py
./scripts/verify-play-store.sh
./scripts/verify-reader.sh
```

Repository-wide native, functional, 16 KiB compatibility, performance, terminal and release gates are defined under `scripts/` and `.github/workflows/`.

## Documentation

The existing root `docs/` directory remains the authoritative Jingdu documentation set during this migration, including product requirements, UX, localization, architecture/core contract, performance SLOs, testing, Play Console setup, production readiness and release provenance.

Historical v2.3.x tags/releases are immutable; they are not rewritten to the new repository paths.
