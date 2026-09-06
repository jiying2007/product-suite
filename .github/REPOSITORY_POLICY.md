# Repository Policy

`product-suite` is a product-family monorepo. Repository policy separates product ownership from shared platform capabilities and keeps release claims evidence-based.

## Topology and ownership

- Product-specific behavior, permissions, store configuration and release policy belong under `apps/<product>/`.
- Stable capabilities shared by multiple products may live under `platform/<capability>/`.
- Platform code must not depend on a product shell.
- AI is an optional capability; products that do not need it must not acquire an AI dependency merely because the repository contains `platform/ai`.
- Planned product directories are scaffolds until product-specific build, test, privacy, quality and release evidence exists.

CI enforces the canonical product-suite topology and rejects retired pre-suite paths.

Do not introduce committed build artifacts, production signing material, credentials or extracted third-party application packages unless an explicit policy documents a narrowly scoped exception.

## Jingdu governance

Jingdu is currently the release-bearing product. Its Android and HarmonyOS shells share document semantics from `platform/text/native`; platform-specific forks of search/chapter/repair/identity behavior are prohibited.

Shared Jingdu ABI/data behavior changes must update both platform bridges, automated native tests and the corresponding SSOT documents in the same change.

The repository-stable Android debug keystore under `config/signing/android-debug.keystore` remains an intentional exception: it is public/test signing material used for the current Jingdu GitHub release stage so successive downloadable APKs retain a stable Android debug identity.

### Current GitHub release stage

The current Jingdu Android release stage is GitHub distribution from an immutable, fully-gated source tag. It does **not** require GitHub branch protection or repository rulesets on `main`.

Ordinary source work should use pull requests and hosted source gates. Release tags are historical provenance and must never be moved or deleted by project automation. New source releases are annotated tag objects whose message binds the exact fully-gated `main` commit to the checked-in source-manifest SHA-256.

The current installable Android artifact is the APK published by `publish-android-debug-apk`, built from the immutable source tag and signed with the repository-stable Android debug key (`androiddebugkey`). Its APK SHA-256 and signing-certificate SHA-256 must be published alongside it.

### Future Google Play production

Google Play production is a later, separate release stage. Before that stage begins, repository administration may enable platform-enforced `main` / `v*` protection, and Play production must use the retained production/upload signing path and external Play/device evidence defined in `docs/PRODUCTION_READINESS.md`.

The Jingdu source publisher remains fail-closed: it may complete an interrupted release for an existing tag only when that tag already resolves to the exact gated `main` SHA, and it never rewrites an existing tag.

CI must not claim Google Play production qualification from hosted source gates, a GitHub debug-signed APK or repository policy text alone.

## Future products

AudioLab, Network Toolbox, Phone Doctor and Voice Cleaner must establish their own application IDs, permissions/privacy contracts, store products, signing/release configuration and product-specific gates before any production/release claim. They must not inherit Jingdu identifiers, billing SKU or release assumptions by convenience.
