# Jingdu Shared Text Core

`platform/text/native` is the only production business/algorithm core for Jingdu Android and HarmonyOS.

This module is a deliberate grandfathered platform exception: it predates the product-suite split and is already a stable C ABI shared by two Jingdu platform shells. That cross-platform reuse justifies retaining it here without pretending that other products already consume it. New platform capabilities still require demonstrated reusable boundaries; other products must not depend on this ABI merely because it lives under `platform/`.

ABI v2 provides:

- bounded/sample-aware source encoding detection including UTF-8, UTF-16, Big5 and GB18030;
- source/normalized file SHA-256 and deterministic repair revision identity;
- strict normalized UTF-8 validation and sparse code-point indexing;
- bounded random reads, literal full-text search and chapter discovery;
- bounded speech segmentation;
- atomic literal-rule clean export.

Android calls the ABI through JNI; HarmonyOS calls the same ABI through Node-API. Platform shells own charset decoding and OS integration only.

Normative semantics, lifetime/thread rules and limits live in `../../docs/CORE_CONTRACT.md`, `../../docs/DATA_MODEL.md`, `../../docs/ENCODING.md` and `../../docs/PERFORMANCE.md`.

Build and test from repository root:

```bash
./scripts/check-native.sh
```

The Release build treats compiler warnings as errors and executes both contract and stress tests. Do not add a Java/Kotlin/ArkTS fallback implementation or a second compatibility core.
