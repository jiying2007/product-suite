# Production Readiness — Android

This document separates the **current GitHub release stage** from a future **Google Play production rollout**. Hosted source CI is authoritative for source acceptance, while Play/device evidence remains external and fail-closed.

## Current release stage

Current Android distribution is complete when all of the following are true:

- the exact source commit passes the required hosted source gates;
- the version has an immutable annotated source tag and GitHub Release;
- the tag resolves to the intended fully-gated source commit;
- the downloadable Android APK is built from that immutable tag;
- the APK is signed with the repository-stable Android debug key (`androiddebugkey`) from `config/signing/android-debug.keystore`;
- `apksigner` verifies the APK certificate;
- `SHA256SUMS.txt` and `SIGNING-CERT-SHA256.txt` are published beside the APK.

For this stage, **`main` branch protection / repository rulesets are not required release gates**. The repository may remain unprotected while pull requests and hosted CI continue to be the normal source workflow.

The debug-signed GitHub APK is the official installable artifact for this stage. It is intentionally **not** Google Play production signing or Google Play rollout evidence.

Hosted source acceptance additionally guards production maturity without changing the current release-governance decision: Android instrumentation tests execute on a hosted emulator, Release native libraries are checked for 16 KiB page-size compatibility, and Release builds must produce FULL native debug symbols. These gates are source/build evidence only and still do not substitute for physical-device or Play-installed qualification.

## Future Google Play production boundary

Google Play production is a later, separate stage. The rows below apply only when a Play production rollout is actually being prepared; they do not block the current GitHub debug-signed release.

### Repository governance for Play production

Before the first Play production staged rollout, capture actual GitHub administration evidence appropriate to that stage, including any chosen `main` / `v*` protection policy, required hosted checks, force-push/deletion controls, and tag immutability controls.

This maturity pass intentionally does **not** enable `main` protection. That remains a later repository-administration choice rather than a current source change.

Regardless of repository settings, the candidate source tag must resolve to the exact intended fully-gated commit and new source tags must remain annotated provenance objects containing the source-manifest SHA-256.

### Production-signed Android artifact

Use the retained production/upload signing path and explicit monotonically increasing version values:

```bash
cd apps/jingdu/android
./gradlew --no-daemon --no-configuration-cache --warning-mode all \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=<production-code> \
  -PjingduVersionName=<source-semver> \
  androidStoreCheck writeAndroidReleaseChecksums
```

Archive and record:

- [ ] signed production AAB;
- [ ] optional signed production APK used for direct device qualification;
- [ ] R8 `mapping.txt`;
- [ ] FULL native debug symbols matching the exact production AAB;
- [ ] `SHA256SUMS`;
- [ ] production/upload signing certificate fingerprint;
- [ ] explicit `versionCode` + `versionName`;
- [ ] exact source commit/tag used to build the artifacts;
- [ ] successful 16 KiB package/ELF compatibility check for the production source candidate;
- [ ] successful Play pre-launch/app-bundle validation for the exact AAB.

Do not commit production signing keys or production-signed binaries to this repository.

The provenance record should make this chain mechanically auditable:

```text
immutable source tag
  -> exact commit SHA
  -> versionCode / versionName
  -> production AAB SHA-256
  -> upload/production signing certificate
  -> R8 mapping + native symbols
  -> Play track / rollout event
```

### Physical Android qualification

Execute `DEVICE_MATRIX.md` and `PERFORMANCE_SLO.md` on real release-derived builds. Minimum evidence:

- [ ] API 26 device plus current target-API device;
- [ ] at least two OEM families;
- [ ] `zh-CN`, `zh-TW`, `zh-HK`, `en-US` plus unsupported-locale English fallback;
- [ ] 200% font scale and TalkBack on the primary Library/Reader/Clean/Settings paths;
- [ ] 10 / 100 / 300 MiB TXT import/read/search/chapter/Clean journeys;
- [ ] UTF-8, UTF-16, GB18030/GBK, Big5 and malformed/truncated samples;
- [ ] process death / reopen / background-foreground recovery;
- [ ] low-storage/write-failure recovery without source/private-copy corruption;
- [ ] wired/Bluetooth TTS route and audio-focus interruption behavior;
- [ ] hardware keyboard navigation on a suitable large-screen/desktop-class Android target without stealing input from active search/edit fields;
- [ ] physical Reader Macrobenchmark meets the release SLOs in `PERFORMANCE_SLO.md`;
- [ ] physical volume-key paging advances the authoritative Reader source position.

The physical performance workflow must be dispatched with an explicit immutable `source_ref`. Its artifact must contain `provenance.txt` with the resolved source SHA and physical device manufacturer/model/API/build fingerprint. A run against an unspecified moving branch is not production evidence.

Hosted emulator numbers are regression evidence only and cannot satisfy Play production physical-device rows.

### Google Play commerce

Using license testers and the production application id `com.junchen.jingdu`:

- [ ] create and activate one-time product `jingdu_pro_lifetime`;
- [ ] localize product title/description for `zh-CN`, `zh-TW`, `zh-HK`, `en-US`;
- [ ] verify displayed price comes from Play `formattedPrice`;
- [ ] successful purchase unlocks Pro and is acknowledged;
- [ ] cancellation does not unlock;
- [ ] pending purchase does not unlock;
- [ ] reinstall/clear-data restore succeeds on the same Play account;
- [ ] verified ownership remains usable offline;
- [ ] authoritative no-ownership refresh removes stale entitlement;
- [ ] product unavailable / Billing unavailable leaves all Free Reader paths functional.

The source policy for these states is host-tested, but license-tester results remain required because Billing service/account/Play behavior cannot be proved by local source simulation.

The current lifetime/no-account product intentionally has no entitlement backend. That preserves the zero-account/privacy architecture while accepting some piracy risk. A minimal purchase-token verification service is a future business tradeoff only if revenue justifies it; such a service must never receive book text, reading state, annotations or search data.

### Privacy-safe support diagnostics

The APK must continue to have no direct `INTERNET` permission and no runtime ads/analytics SDK. When support evidence is needed, the user may explicitly export the local privacy/diagnostic JSON.

Before production verify that this export:

- [ ] declares `containsBookText=false`;
- [ ] contains only bounded stable error codes/operation names/timestamps rather than exception messages;
- [ ] contains no source path, content URI, book name/text, search query or purchase token;
- [ ] includes enough build/device/storage-class metadata to support common failure diagnosis;
- [ ] remains an explicit user action rather than background upload.

### Play listing and policy

- [ ] default listings uploaded from `fastlane/metadata/android` for all four supported Play locales;
- [ ] screenshots captured from the actual release UI using synthetic/public-domain TXT;
- [ ] Custom Listings are applied only where Play supplies the matching keyword targeting capability;
- [ ] Data safety / privacy declarations match the no-book-upload / no-ads / no-runtime-analytics architecture;
- [ ] store contact/category/content declarations are complete;
- [ ] no screenshot or description contains unverified performance/ranking claims;
- [ ] In-App Review remains milestone/cooldown driven and is not a first-launch gate.

### Rollout

- [ ] upload the exact qualified AAB to the intended Play track;
- [ ] complete internal/closed testing on Play-installed builds;
- [ ] verify purchase/restore on a Play-installed candidate;
- [ ] start a staged production rollout instead of immediate 100% exposure after the Reader + commerce hardening changes;
- [ ] record initial staged percentage and start timestamp;
- [ ] inspect Android vitals / crash / ANR / store feedback before each rollout expansion;
- [ ] record the commit/tag/AAB checksum associated with each rollout expansion;
- [ ] complete 100% rollout only after the staged evidence is acceptable.

Android vitals/store platform limits are not the internal product target. Rollout expansion should require materially healthier crash/ANR behavior than the platform bad-behavior thresholds, with the exact installed versionCode/AAB provenance recorded for each decision.

## Portable local-user backup acceptance

Before Play production rollout verify on a device:

- [ ] export/import Reader settings and named/custom reading preferences;
- [ ] export/import global Clean rules;
- [ ] export/import bookmarks/highlights/notes;
- [ ] export/import favorites/tags;
- [ ] same source + same normalized revision restores staged progress;
- [ ] different normalized revision does **not** restore stale progress;
- [ ] reading sessions/pace restore without book text;
- [ ] Smart Clean KEEP/DELETE/PROTECT memory restores from fingerprints only;
- [ ] backup JSON declares `containsBookText=false` and contains no source/normalized/Clean book payload;
- [ ] malformed backup preflight fails before mutating already-valid local assets;
- [ ] SAF folder roots are re-selected rather than pretending URI grants are portable across installs/devices;
- [ ] imported font binaries are re-selected if unavailable on the destination device.

## Release declaration

For the current stage, the correct wording is **GitHub release / debug-signed Android release** once the hosted gates, immutable tag and published debug-signed APK evidence are complete.

A release may be called **Google Play production-qualified** only when all applicable future Play-production rows above have concrete evidence and the exact production AAB is traceable to the fully-gated source tag.
