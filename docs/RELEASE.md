# Release

Source control contains releasable source, store metadata and the intentionally public Android debug keystore used for the **current GitHub release stage**.

## Current Android release stage

The current Android release is a GitHub Release backed by an immutable, fully-gated source tag plus an installable APK signed with the repository-stable Android debug identity.

Current-stage rules:

- `main` branch protection / repository rulesets are **not required**;
- ordinary source changes continue to use pull requests and hosted CI as the normal workflow;
- the release tag is immutable historical provenance and is never moved by release automation;
- the Android APK is built from the immutable source tag;
- the APK uses `config/signing/android-debug.keystore`, alias `androiddebugkey`;
- the APK certificate SHA-256 is `26:18:E7:88:94:86:AD:EA:5F:C0:83:F7:CB:51:55:F2:EC:62:9B:AF:5D:AE:2A:74:DA:BC:3A:BE:5C:D0:2A:94`;
- `apksigner` verification, APK SHA-256 and signing-certificate SHA-256 are required release evidence;
- `Jingdu-vX.Y.Z-debug-signed.apk`, `SHA256SUMS.txt` and `SIGNING-CERT-SHA256.txt` are published as GitHub Release assets.

The debug key is intentionally public/test signing material so successive GitHub APKs retain a stable Android identity and can upgrade one another. For the current stage this debug-signed APK is the official installable Android release artifact.

This does **not** mean Google Play production signing or rollout has occurred. Play production is a separate future stage described in `PRODUCTION_READINESS.md`.

## Android build

```bash
cd apps/jingdu/android
./gradlew --no-daemon --no-configuration-cache --warning-mode all \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=<monotonic-code> \
  -PjingduVersionName=<semver> \
  androidStoreCheck writeAndroidReleaseChecksums
```

For GitHub release publication the workflow supplies the repository-stable debug keystore and builds the release APK from the immutable source tag. Production Play signing, when that later stage begins, uses the retained production/upload signing path and must never substitute this public debug key for the Play production identity.

## Source release automation

GitHub source provenance has one authority: the `publish-source-release` tail job in `.github/workflows/ci.yml`. There is no separate Source Release workflow, ref-only trigger, `workflow_run` relay, PR-close release path or polling protocol.

A source release is represented by a permanent manifest `releases/source/vX.Y.Z.md`. The source-declared Android version must match `X.Y.Z`, and the manifest must contain:

```text
version: vX.Y.Z
kind: source-release
google_play_production: false
```

A release manifest is reviewed and merged through the normal pull-request process. Every push to `main` executes the same six required hosted gates:

1. shared native Core build/tests/static analysis;
2. Android product build, lint, AndroidTest compilation, release bundle and benchmark assembly;
3. hosted Android Macrobenchmark/frame SLO and Baseline Profile qualification;
4. Harmony source/terminal contract;
5. Play metadata and lifetime-Pro contract;
6. terminal source/product/localization/long-form quality contract.

`publish-source-release` has `needs` on all six jobs and runs only for `push` to `main`. Ordinary PR jobs remain read-only. Only publication jobs receive job-scoped `contents: write`.

`scripts/publish-source-release.py` resolves the Android source version and matching permanent manifest. If no manifest exists for that version, source publication is skipped. If a manifest exists, the publisher uses three explicit states:

1. **tag + GitHub Release already exist** — publication is complete and becomes a permanent no-op on later `main` pushes for the same version; the tag is never moved;
2. **tag exists but Release is missing** — the interrupted first publication may be completed only if the orphan tag already resolves to the current fully-gated `github.sha`;
3. **neither tag nor Release exists** — create an annotated tag object binding the exact fully-gated `github.sha` to the checked-in source-manifest SHA-256, create its GitHub Release, then verify the tag resolves to that SHA.

A Release without its tag is inconsistent and fails hard. Existing published tags remain historical provenance even after `main` advances.

After publication/no-op resolution, the publisher removes closed same-repository temporary PR branches under `feat/`, `fix/`, `chore/`, `ci/`, `refactor/`, `docs/`, `test/`, `perf/`, plus `release/source-v*`, while preserving currently open PR heads and long-lived branch names outside those prefixes.

## Android debug-signed release automation

After source publication, `publish-android-debug-apk` resolves the current source version and its immutable source tag, verifies the repository-stable debug keystore checksum/certificate, detaches to the tag commit, runs `validateStoreRelease assembleRelease`, verifies the resulting APK with `apksigner`, and publishes:

- `Jingdu-vX.Y.Z-debug-signed.apk`;
- `SHA256SUMS.txt`;
- `SIGNING-CERT-SHA256.txt`.

Existing complete assets are not rebuilt or overwritten unnecessarily. The workflow still ensures the GitHub Release notes state that the debug-signed APK is the current-stage Android release artifact and is not Google Play production evidence.

## Android 2.3.x commercial / Reader release

Android 2.3.x carries the Reader product line and lifetime Pro model while keeping Free as a complete reader.

### Future Play Console prerequisites

When Google Play production work begins, follow `PLAY_CONSOLE_SETUP.md` and `PRODUCTION_READINESS.md` and verify:

- INAPP one-time product id exactly `jingdu_pro_lifetime`;
- product is active and localized price configured;
- license testers cover successful purchase, cancel, pending, acknowledgement, reinstall restore and offline launch after verified ownership;
- no subscription product is required while there is no recurring server service;
- default listing metadata comes from `fastlane/metadata/android`;
- Custom Listing/search keyword specs and screenshot brief under `store/play/` are applied where supported;
- Data safety/privacy declarations match actual app behavior;
- production repository governance, signing, device and staged-rollout evidence is captured for that later stage.

Source CI cannot create/activate Play Console products, publish listings, qualify physical devices or establish Google Play production evidence.

### Future Play production artifact acceptance

A Google Play production release requires:

- exact candidate hosted gates green;
- `androidStoreCheck` green with production identity/version/signing;
- production-key-signed APK/AAB verified with the retained production/upload path;
- mapping/checksum/certificate evidence archived;
- physical-device matrix and release performance SLO evidence;
- Play license-test purchase/restore/acknowledge evidence;
- store listing screenshots/text uploaded and reviewed;
- internal/closed Play-installed candidate testing;
- staged rollout evidence;
- immutable source tag/GitHub Release provenance.

The current debug-key-signed GitHub APK satisfies the **current GitHub release stage**, but does not satisfy future Google Play production signing.

## Portable local-user backup

Reader schema 4 backs up portable, text-free user assets:

- Reader settings;
- global Clean rules;
- bookmarks/highlights/notes;
- favorites/tags;
- progress staged against `sourceSha256 + normalizedSha256` and consumed only for the exact normalized revision;
- local reading sessions and pace;
- Smart Clean KEEP/DELETE/PROTECT memory as one-way candidate fingerprints and decisions.

The backup declares `containsBookText=false` and does not contain source, normalized or Clean book payloads. Schema 3 settings/rules/annotation backups remain importable. SAF folder URI grants and imported font binaries are not treated as portable credentials/assets; destination devices must re-select them when unavailable.

## Growth release checklist

Before a future Play rollout:

1. run `./scripts/verify-play-store.sh`;
2. verify default zh-CN title is `净读 - TXT 小说阅读器`;
3. confirm screenshot claims reflect device-tested behavior;
4. verify Free Smart Clean scan shows full candidate text before paywall;
5. verify Pro CTA displays Play `formattedPrice`;
6. exercise global-rule import/export and schema-4 local-user backup/restore;
7. verify no backup/export contains book正文;
8. verify staged progress restores only for the exact normalized revision;
9. verify Smart Clean feedback backup contains fingerprints/decisions only;
10. verify In-App Review is not shown on first launch;
11. capture Play experiment plan with one major variable at a time.

## HarmonyOS

HarmonyOS remains source-complete/pre-release until official HAP/device gates execute. Android GitHub release or Play readiness is not Harmony production evidence.

## Historical hard cut

Android v2.0.0 established the current terminal source line as a new root and removed ordinary refs to the experimental lineage. Reader / Android 2.3.x is a normal forward release on that hard-cut line; do not rewrite history again merely for a product update.

Version 2.x does not promise compatibility with pre-2.x experimental private metadata/ABI.
