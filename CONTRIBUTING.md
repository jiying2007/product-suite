# Contributing

This is a hard-cut product repository. Changes must preserve one shared native core and two native platform shells; do not add compatibility implementations or a second business core.

## Workflow

Use a short-lived branch and pull request for production changes. `main` protection is currently an owner workflow choice rather than a repository rule; regardless, do not treat direct-main changes as the normal development path.

Recommended branch prefixes: `feat/`, `fix/`, `refactor/`, `docs/`, `build/`, `test/`.

Use conventional, imperative commit subjects such as `fix(core): preserve truncated UTF-8 detection`.

## Definition of done

Before merge:

1. shared-core Release tests pass;
2. Android source gate passes;
3. repository terminal contract passes;
4. Harmony/shared-ABI changes have an official Hvigor HAP build and applicable device evidence when the runner/device environment is available;
5. persisted/data/ABI/performance behavior changes update the corresponding SSOT document;
6. no generated release binary, signing material, credential, extracted third-party APK or old implementation tree is committed.

## Ownership boundaries

- `platform/text/native`: all document algorithms and cross-platform semantic contracts;
- `apps/jingdu/android`: Android UI, lifecycle, files, charset adapter, TTS/audio and JNI only;
- `apps/jingdu/harmony`: ArkUI/lifecycle/files/charset adapter/Core Speech Kit and Node-API only.

If a behavior can produce different search offsets, chapter offsets, normalized identity, repair output or speech segmentation between platforms, it belongs in the shared core.

## ABI changes

A breaking C ABI change is one atomic change: increment `jd_abi_version`, update Android JNI, Harmony Node-API/type declarations, native tests and `docs/CORE_CONTRACT.md`. Do not add compatibility shims for old ABI versions.

## Native code quality

C++17 code uses `.clang-format` and `.clang-tidy`. Release CI compiles with `-Wall -Wextra -Wpedantic -Werror`. Run the native checks before pushing.
