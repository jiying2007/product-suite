# Quality Gates

A change is mergeable only when every applicable **source** gate passes. Hosted CI defines source acceptance.

The **current Android release stage** is GitHub distribution from an immutable source tag with an APK signed by the repository-stable Android debug key. GitHub branch/tag protection is **not** a current-stage release requirement. Google Play production remains a later, separate gate.

## Hosted source gates

1. **Native core** — Release CMake, `-Wall -Wextra -Wpedantic -Werror`, CTest and clang-tidy. Coverage includes encoding/SHA/revision, malformed UTF-8, large-file/concurrency, `.jdx` recovery, Simplified/Traditional Smart Clean candidates and whole-line wildcard golden behavior.
2. **Android product** — Kotlin/Compose Debug/Release compile, Debug/Release lint, Debug APK, Release AAB, AndroidTest assembly and supported JNI ABIs.
3. **Android performance** — hosted Macrobenchmark executes open/page/scroll/chapter/settings journeys, enforces checked-in frame P95/P99 regression thresholds and completes the Reader Baseline Profile journey with retained evidence.
4. **Android localization** — `en-US / zh-Hans / zh-Hant` resource keys and format placeholders match; English is the unqualified fallback; generated LocaleConfig remains enabled; manifest/app/accessibility/runtime messages are resource-backed; major presentation/controller files cannot reintroduce hard-coded CJK UI copy; AndroidTest resolves expected UI text from the active locale.
5. **Android commercial UX** — Free reader stays complete; Smart Clean candidate content is visible before paywall; Pro actions are contextual; Billing uses `jingdu_pro_lifetime`; portable-backup/offline voice/global-rule UI exists; no first-launch paywall/review prompt.
6. **Play store contract** — metadata length/policy checks for `zh-CN / zh-TW / zh-HK / en-US`, localized Custom Listing/screenshot production specs, Billing/Review dependency versions and fixed lifetime product id.
7. **Large-file path** — immutable revisions, validated `.jdx`, active-session Search/Chapters, bounded/streaming Smart Clean and safe fallback/pruning.
8. **Harmony source contract** — Stage/Node-API/TaskPool/storage/source contracts remain valid; real HAP/device qualification is a separate Harmony release gate.
9. **Repository contract** — required product/growth/store/localization/release-readiness docs exist; no legacy roots, compatibility core, floating Actions tags, committed production packages/signing material or direct Android `INTERNET` permission; Reader source-release/product SSOT does not regress to stale 2.2/V2 wording. The repository-stable Android debug keystore is the explicit current-stage signing exception.
10. **Portable user assets** — Reader schema-5 backup excludes source/normalized/Clean payloads and automatically captured annotation excerpts/re-anchor context, explicitly declares portable user-authored text, keeps exact-revision progress restore fail-closed, keeps Smart Clean memory fingerprint-only, keeps reading stats numeric/identity-only and AndroidTest compiles the portable-asset contract tests.
11. **Source provenance** — Android source/staging version matches a permanent manifest; future source publisher creates annotated provenance tags that bind the exact gated `main` SHA to the manifest SHA-256 and never moves existing release tags.

## Android merge acceptance

Before Ready/merge:

- exact PR head passes all six Hosted jobs: `native-core`, `android`, `android-performance`, `play-store-contract`, `harmony-contract`, `terminal-contract`;
- no unresolved PR review thread/comment remains;
- Product/Requirements/UX/Growth/Localization/Core Contract/Testing/Device Matrix/Release/Play setup/Production Readiness docs agree with implementation;
- Free/Pro boundary does not lock basic reader functionality;
- Smart Clean and wildcard rule tests prove deterministic local behavior;
- Simplified and Traditional document behavior is independent of UI locale;
- cross-script search fallback uses curated one-to-one variants and never silently rewrites document text;
- Billing/Review failures remain non-blocking to Free reading;
- portable user backup declares `containsBookText=false`, `containsAutomaticBookExcerpts=false` and `containsUserAuthoredText=true`; it contains no source/normalized/Clean正文 or automatically captured annotation excerpt/re-anchor text, while explicit user-authored note/rule/pronunciation literals remain portable;
- staged progress is consumed only by the matching normalized revision;
- whole-file work remains off Android main thread.

## Current Android GitHub release gate

After a `main` push passes all required hosted jobs, current-stage Android publication is complete when:

- the source version has a permanent manifest;
- the source tag is an immutable annotated provenance object;
- the tag resolves to the intended fully-gated source commit;
- the GitHub Release exists for that tag;
- `publish-android-debug-apk` publishes `Jingdu-vX.Y.Z-debug-signed.apk` from that immutable tag;
- the APK is signed with the repository-stable Android debug key (`androiddebugkey`);
- `apksigner` verifies the signing certificate;
- `SHA256SUMS.txt` and `SIGNING-CERT-SHA256.txt` are present beside the APK.

No `main` branch protection or repository ruleset evidence is required for this current GitHub release stage.

## Future Google Play production gate

Source merge and the current debug-signed GitHub release are not the same as Google Play production readiness. The authoritative external checklist is `PRODUCTION_READINESS.md`.

### Repository governance

When Play production begins, capture the repository-administration controls selected for that production stage, including `main` / `v*` protection policy where enabled, force-push/deletion controls and required source checks. The candidate source tag must remain immutable and resolve to the exact intended fully-gated commit.

### Build/signing

- exact candidate Hosted gates green;
- `androidStoreCheck` green with production package/version;
- production APK/AAB uses the retained production/upload signing path rather than the public debug key;
- APK/AAB signing verification, R8 mapping, SHA-256 manifest and certificate fingerprint archived;
- exact source tag/commit recorded with the production artifact.

### Physical Android qualification

- required API/OEM matrix executes on real devices;
- Reader 10/100/300 MiB journeys execute without OOM/ANR/corruption;
- release-device startup/open/search/chapter/Smart Clean/frame SLO evidence meets `PERFORMANCE_SLO.md`;
- locale/200% font/TalkBack/process-death/low-storage/TTS-route cases from `DEVICE_MATRIX.md` are recorded;
- hosted emulator metrics are not substituted for these physical rows.

### Google Play commerce

- one-time INAPP product `jingdu_pro_lifetime` exists and is active;
- product title/description are localized for `zh-CN / zh-TW / zh-HK / en-US`;
- localized price is configured;
- license tester validates purchase, cancel, pending, acknowledge, restore after reinstall, authoritative no-ownership refresh and offline verified ownership;
- purchase UI displays Play `formattedPrice`;
- no subscription is configured without a recurring server service.

### Store discovery

- default `zh-CN / zh-TW / zh-HK / en-US` metadata uploaded from repository assets;
- screenshot/feature graphic follows the matching locale brief under `store/play/`;
- Custom Listings are created for relevant search keyword clusters when Play traffic supports them;
- listing claims avoid unsupported superlatives/performance promises;
- privacy/data-safety declarations match no direct INTERNET permission, no ads/analytics SDK and no text upload.

### Portable local-user backup

- settings/rules/annotations round-trip;
- favorites/tags round-trip by source identity;
- progress restores only to the exact normalized revision;
- reading sessions/pace restore without book text;
- Smart Clean KEEP/DELETE/PROTECT memory restores from one-way fingerprints only;
- SAF URI grants/imported font binaries are re-selected where destination capabilities differ.

### Rollout

- internal/closed Play-installed test first;
- staged production rollout with Play Vitals/crash/ANR/refund monitoring;
- rollback artifact/version plan exists;
- every rollout expansion remains traceable to source tag + AAB checksum;
- listing and price experiments change one major variable at a time.

## Review/privacy guardrails

- Play In-App Review only after meaningful local milestones and local cooldown.
- No sentiment pre-screen or fabricated rating prompt.
- Google Play Billing/Review receives no private TXT content.
- No advertising or runtime analytics SDK is introduced for growth.

## Harmony production gate

HarmonyOS remains source-complete/pre-release until official toolchain HAP build and device matrix execute on `self-hosted,harmonyos`. A queued workflow is not success, but does not block Android-only 2.x source merge/current GitHub release.

## Device gates

Android device qualification covers 10/100/300 MiB, first-open/reopen/cache recovery, lifecycle, all supported app locales, cross-script search, Simplified/Traditional Smart Clean, purchase/restore, portable backup, offline voice/TTS locale behavior, accessibility and adaptive windows. Cross-platform golden parity blocks only a claim that both Android and HarmonyOS are jointly production-ready.
