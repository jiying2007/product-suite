# Jingdu Shared Core

`core/native` is the only production business/algorithm core for Android and HarmonyOS.

ABI v2 provides:

- bounded/sample-aware source encoding detection including UTF-8, UTF-16, Big5 and GB18030;
- source/normalized file SHA-256 and deterministic repair revision identity;
- strict normalized UTF-8 validation and sparse code-point indexing;
- bounded random reads, literal full-text search and chapter discovery;
- bounded speech segmentation;
- atomic literal-rule clean export.

Android calls the ABI through JNI; HarmonyOS calls the same ABI through Node-API. Platform shells own charset decoding and OS integration only.

Normative semantics, lifetime/thread rules and limits live in `../docs/CORE_CONTRACT.md`, `DATA_MODEL.md`, `ENCODING.md` and `PERFORMANCE.md`.

Build and test from repository root:

```bash
./scripts/check-native.sh
```

The Release build treats compiler warnings as errors and executes both contract and stress tests. Do not add a Java/Kotlin/ArkTS fallback implementation.
