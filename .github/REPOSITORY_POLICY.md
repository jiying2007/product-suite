# Repository Policy

This repository intentionally uses a hard-cut Android + HarmonyOS architecture with one C++ shared core.

Do not reintroduce:

- compatibility or migration implementations for the removed experimental product line;
- prototype production roots or a second Java/Kotlin/ArkTS document core;
- platform-specific search/chapter/repair/identity semantics that belong in `platform/text/native`;
- committed build artifacts, production signing material or extracted third-party application packages.

The repository-stable Android debug keystore under `config/signing/android-debug.keystore` is an intentional exception: it is public/test signing material used for the current GitHub release stage so successive downloadable APKs retain a stable Android debug identity.

Shared ABI/data behavior changes must update both platform bridges, automated native tests and the corresponding SSOT documents in the same change.

## Current GitHub release governance

The current Android release stage is GitHub distribution from an immutable, fully-gated source tag. It does **not** require GitHub branch protection or repository rulesets on `main`.

Ordinary source work should continue to use pull requests and the hosted source gates as the project workflow, but the absence of platform-enforced `main` protection is not a blocker for the current release stage.

Release tags remain historical provenance and must never be moved or deleted by project automation. New source releases are annotated tag objects whose message binds the exact fully-gated `main` commit to the checked-in source-manifest SHA-256.

The current Android installable release artifact is the APK published by `publish-android-debug-apk`, built from the immutable source tag and signed with the repository-stable Android debug key (`androiddebugkey`). Its APK SHA-256 and signing-certificate SHA-256 must be published alongside it.

## Future Google Play production governance

Google Play production is a later, separate release stage. Before that stage begins, repository administration may enable platform-enforced `main` / `v*` protection, and Play production must use the retained production/upload signing path and external Play/device evidence defined in `docs/PRODUCTION_READINESS.md`.

The source publisher remains fail-closed: it may complete an interrupted Release for an existing tag only when that tag already resolves to the exact gated `main` SHA, and it never rewrites an existing tag.

CI must not claim Google Play production qualification from hosted source gates, a GitHub debug-signed APK, or repository policy text alone.
