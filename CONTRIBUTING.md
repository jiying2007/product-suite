# Contributing

`product-suite` is a product-family monorepo. Keep product ownership explicit, share capabilities only when reuse is real, and preserve the release contracts of products that are already shipping.

## Workflow

Use a short-lived branch and pull request for production changes. `main` protection is currently an owner workflow choice rather than a repository rule; regardless, direct-main changes are not the normal development path.

Recommended branch prefixes: `feat/`, `fix/`, `refactor/`, `docs/`, `build/`, `test/`, `perf/`.

Use conventional, imperative commit subjects such as `fix(jingdu): preserve truncated UTF-8 detection` or `feat(audiolab): add calibrated spectrum capture`.

## Repository topology

The canonical boundaries are enforced by `scripts/verify-repository-topology.sh`:

- `apps/<product>/...` owns product-specific UI, behavior, permissions, store configuration and release policy.
- `platform/<capability>/...` owns a stable shared capability only after more than one product genuinely consumes the contract.
- Product code may depend on platform code; platform code must not depend on a product shell.
- Do not create generic shared modules merely to avoid a small amount of duplication.
- AI is optional platform capability, not a mandatory dependency or repository identity.

The planned AudioLab, Network Toolbox, Phone Doctor and Voice Cleaner directories are scaffolds until they have real product implementations and gates. Do not make them appear production-ready through documentation alone.

## Definition of done

For every change:

1. the repository topology gate passes;
2. all affected product/platform unit and source gates pass;
3. privacy, permissions, persistence, ABI and performance contracts are updated when behavior changes;
4. release-bearing products retain reproducible provenance and do not rewrite historical tags/releases;
5. no generated release binary, production signing material, credential or extracted third-party application package is committed unless an explicit repository policy documents the exception.

A new product is not release-capable until it owns build, test, privacy/permission, quality and release gates appropriate to that product.

## Jingdu-specific ownership

Jingdu is currently the release-bearing product:

- `platform/text/native` — shared document algorithms and cross-platform semantic contracts;
- `apps/jingdu/android` — Android UI/lifecycle/files/TTS/store integration and JNI;
- `apps/jingdu/harmony` — HarmonyOS UI/lifecycle/files/speech integration and Node-API.

If Jingdu behavior can produce different search offsets, chapter offsets, normalized identity, repair output or speech segmentation between Android and HarmonyOS, it belongs in the shared text engine.

A breaking Jingdu C ABI change is one atomic change: increment `jd_abi_version`, update Android JNI, Harmony Node-API/type declarations, native tests and `docs/CORE_CONTRACT.md`. Do not add compatibility shims for old ABI versions.

## Native code quality

C++17 code uses `.clang-format` and `.clang-tidy`. The text platform Release CI compiles with `-Wall -Wextra -Wpedantic -Werror`; run the native checks before pushing changes to it.
