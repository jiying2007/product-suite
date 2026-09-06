# Product Suite

A product-family monorepo for end-user applications and deliberately shared platform capabilities.

The repository name describes the engineering boundary, not a specific implementation technology: AI, native C++, Kotlin/Compose, ArkUI and future runtimes are capabilities used only where a product needs them.

## Repository topology

```text
product-suite/
├── apps/
│   ├── jingdu/
│   │   ├── android/
│   │   └── harmony/
│   ├── audiolab/
│   │   └── android/
│   ├── network-toolbox/
│   │   └── android/
│   ├── phone-doctor/
│   │   └── android/
│   └── voice-cleaner/
│       └── android/
├── platform/
│   ├── text/
│   ├── audio/
│   ├── network/
│   ├── ai/
│   ├── billing/
│   └── telemetry/
├── docs/
├── scripts/
└── .github/
```

`./scripts/verify-repository-topology.sh` is the canonical topology gate. CI rejects retired pre-suite paths and migration-layout compatibility markers and requires every declared product/platform boundary above to exist.

## Products

### Jingdu / 净读

Jingdu is the mature product currently implemented in this repository: an offline, privacy-first TXT reader with Android and HarmonyOS shells backed by the shared C++ text engine under `platform/text/native/`.

- Android: `apps/jingdu/android/`
- HarmonyOS: `apps/jingdu/harmony/`
- Product overview: `apps/jingdu/README.md`
- Detailed product, UX, performance, testing and release contracts: `docs/`

The existing v2.x source tags and GitHub releases remain immutable. Current source builds use only suite-era paths; current automation does not retain a retired-directory build fallback.

Jingdu permanently owns the unprefixed `vX.Y.Z` tag namespace and `releases/source/vX.Y.Z.md`. Other products must use product-prefixed tags and product-scoped manifests, for example `audiolab-v1.0.0` plus `releases/audiolab/source/audiolab-v1.0.0.md`.

### Planned Android product boundaries

The following directories are intentional product scaffolds, not claims of shipping functionality:

- `apps/audiolab/android/` — audio measurement, spectrum/diagnostics and related tools.
- `apps/network-toolbox/android/` — network diagnostics and utility tools.
- `apps/phone-doctor/android/` — transparent device and hardware diagnostics.
- `apps/voice-cleaner/android/` — local-first voice/audio cleanup workflows.

Each product becomes release-capable only after it owns product-specific build, test, privacy, quality, signing, store and release gates.

## Platform boundaries

`platform/` is not a dumping ground for generic helpers. A new capability should move into a shared platform module only when stable reuse is demonstrated across products.

- `platform/text/` — the grandfathered Jingdu cross-platform text/document core shared by Android and HarmonyOS. Its location does not claim that another product already consumes it.
- `platform/audio/` — reserved shared audio/DSP contracts.
- `platform/network/` — reserved shared networking contracts.
- `platform/ai/` — optional AI/model runtime boundary; products that do not need AI must not depend on it.
- `platform/billing/` — reserved shared commerce/entitlement contracts.
- `platform/telemetry/` — reserved privacy-preserving observability contracts; its existence does not imply collection is enabled.

Product-specific behavior stays in the product until reuse is real.

## Root support ownership

Some pre-suite Jingdu support assets intentionally remain at stable root paths because existing documentation and immutable release provenance reference them. Their ownership is explicit rather than generic:

- `docs/` — current Jingdu product contracts; new non-Jingdu product docs belong under that product boundary.
- `releases/source/` — Jingdu unprefixed `v*` source manifests.
- `store/play/` and `fastlane/metadata/android/` — Jingdu Play/store material.
- `quality/smartclean/` — Jingdu Smart Clean quality data.
- `config/signing/` — Jingdu current-stage public debug signing exception.
- `third_party/` — repository-wide legal/license material.

Future products must use product-scoped support namespaces instead of extending the retained Jingdu root namespaces.

## Current verification

Jingdu remains the release-bearing product while the rest of the suite is scaffolded. Core checks include:

```bash
./scripts/verify-repository-topology.sh
./scripts/check-native.sh
cd apps/jingdu/android && ./gradlew --no-daemon --warning-mode all androidCheck
cd ../../..
./scripts/verify-android-i18n.py
./scripts/verify-play-store.sh
./scripts/verify-terminal.sh
```

Hosted CI also runs Android functional tests, 16 KiB native compatibility, Reader Macrobenchmark/SLO checks, Harmony source contracts and release provenance gates. Real HarmonyOS HAP/device qualification requires the configured `self-hosted,harmonyos` runner and is launched explicitly; an offline device runner does not leave ordinary PR/main CI permanently queued.

## Repository rules

- `main` remains releaseable source for the currently release-bearing product.
- Historical tags/releases are immutable and are not rewritten for directory migrations.
- Product code may depend on platform code; platform code must not depend on a product shell.
- AI is a capability, not the repository identity or a mandatory dependency.
- New products must not inherit Jingdu product IDs, billing SKUs, permissions, telemetry policy, unprefixed release tags or root Jingdu support namespaces by convenience.
- APK/AAB/HAP, mapping/symbol packages and production signing material are not committed. The checked-in Android debug keystore remains the documented pre-production GitHub-release exception for Jingdu.
