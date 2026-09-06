# Repository Policy

`product-suite` is a product-family monorepo. Repository policy separates product ownership from shared platform capabilities and keeps release claims evidence-based.

## Topology and ownership

- Product-specific behavior, permissions, store configuration and release policy belong to one named product boundary.
- Stable capabilities shared by multiple products may live under `platform/<capability>/`.
- `platform/text/native` is the single grandfathered exception: it is the existing stable Jingdu C ABI shared by the Android and HarmonyOS shells. Retaining it under `platform/text` does not claim cross-product reuse, and other products must not depend on it until reuse is independently justified.
- Platform code must not depend on a product shell.
- AI is an optional capability; products that do not need it must not acquire an AI dependency merely because the repository contains `platform/ai`.
- Planned product directories are scaffolds until product-specific build, test, privacy, quality and release evidence exists.

CI enforces the canonical product-suite topology and rejects retired pre-suite paths and migration-layout compatibility markers.

Root support directories with retained Jingdu material have explicit ownership contracts. Existing `docs/`, `releases/source/`, `store/play/`, `fastlane/metadata/android/`, `quality/smartclean/` and `config/signing/` content is Jingdu-owned and retained at stable paths for current contracts and historical provenance. New products must not reuse those unqualified namespaces; they own product-scoped documentation, store, quality, signing and release assets instead.

Do not introduce committed build artifacts, production signing material, credentials or extracted third-party application packages unless an explicit policy documents a narrowly scoped exception.

## Jingdu governance

Jingdu is currently the release-bearing product. Its Android and HarmonyOS shells share document semantics from `platform/text/native`; platform-specific forks of search/chapter/repair/identity behavior are prohibited.

Shared Jingdu ABI/data behavior changes must update both platform bridges, automated native tests and the corresponding SSOT documents in the same change.

The repository-stable Android debug keystore under `config/signing/android-debug.keystore` remains an intentional exception: it is public/test signing material used for the current Jingdu GitHub release stage so successive downloadable APKs retain a stable Android debug identity.

### Current GitHub release governance

The current Jingdu Android release stage is GitHub distribution from an immutable, fully-gated source tag. It does **not** require GitHub branch protection or repository rulesets on `main`.

Jingdu permanently owns the repository's unprefixed `vX.Y.Z` tag namespace and the matching `releases/source/vX.Y.Z.md` manifest namespace. Existing and future Jingdu releases may continue that identity without a second migration layer. Every other product must use a product-prefixed tag such as `audiolab-v1.0.0` and a product-scoped manifest path such as `releases/audiolab/source/audiolab-v1.0.0.md`.

Ordinary source work should use pull requests and hosted source gates. Release tags are historical provenance and must never be moved or deleted by project automation. New source releases are annotated tag objects whose message binds the exact fully-gated `main` commit to the checked-in source-manifest SHA-256.

The current installable Android artifact is the APK published by `publish-android-debug-apk`, built from the immutable source tag and signed with the repository-stable Android debug key (`androiddebugkey`). Its APK SHA-256 and signing-certificate SHA-256 must be published alongside it.

Current `main` release automation supports only the suite-era Jingdu source layout. Historical pre-suite tags remain readable provenance, but current automation does not carry a fallback build path for retired source directories.

### Future Google Play production

Google Play production is a later, separate release stage. Before that stage begins, repository administration may enable platform-enforced `main` / `v*` protection, and Play production must use the retained production/upload signing path and external Play/device evidence defined in `docs/PRODUCTION_READINESS.md`.

The Jingdu source publisher remains fail-closed: it may complete an interrupted release for an existing tag only when that tag already resolves to the exact gated `main` SHA, and it never rewrites an existing tag.

CI must not claim Google Play production qualification from hosted source gates, a GitHub debug-signed APK or repository policy text alone.

## HarmonyOS device qualification

Hosted `harmony-contract` is the automatic source contract. Real HAP/device qualification requires the configured `self-hosted,harmonyos` environment and is launched explicitly through `workflow_dispatch`; ordinary pull requests and `main` pushes must not leave permanently queued device-build runs when that runner is offline.

## Future products

AudioLab, Network Toolbox, Phone Doctor and Voice Cleaner must establish their own application IDs, permissions/privacy contracts, store products, signing/release configuration, product-scoped tag namespace and product-specific gates before any production/release claim. They must not inherit Jingdu identifiers, billing SKU, unprefixed `v*` tags, root Jingdu support namespaces or release assumptions by convenience.
