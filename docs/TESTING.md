# Testing Strategy

## Shared native core

Every `platform/text/native` change passes Release build with `-Wall -Wextra -Wpedantic -Werror`, CTest and clang-tidy analyzer/bugprone/performance/portability gates.

Required automated coverage:
- SHA/file hashing;
- UTF-8/UTF-16/GB18030/Big5 detection and truncated sample boundaries;
- malformed UTF-8 rejection;
- character/byte indexing, bounded reads, search, chapters and speech segmentation;
- deterministic repair revisions;
- literal repair export;
- Smart Clean candidate detection with exact occurrence counts;
- safe whole-line wildcard export (`@g` packed rule) removing matching promotional lines while preserving ordinary正文;
- persistent `.jdx` creation, corrupt/stale fallback and repair;
- large-file stress, concurrent reads and handle lifecycle.

Tests never rely on C/C++ `assert` because Release builds define `NDEBUG`.

## Smart Clean correctness

Smart Clean is advisory until explicit apply. Tests verify:
- high-confidence URL/promotion/repeated lines are discoverable;
- ordinary low-frequency正文 is not automatically converted into a candidate solely because it exists;
- candidate results contain score/count/reason/text and bounded result count;
- whole-line `*` rules match the trimmed whole line, not arbitrary regex;
- literal rules retain existing semantics;
- invalid/oversized packed fields are ignored/rejected safely;
- applying rules still writes an immutable derived file and never mutates normalized/source input;
- portable correction-memory backup contains only `bookId + fingerprint + decision`, declares `containsBookText=false`, and round-trips KEEP/DELETE/PROTECT without candidate text.

The checked-in held-out quality evidence is deliberately larger than the training examples. `eval-v1.tsv` remains the small manually curated regression corpus while `eval-v2-matrix.json` expands Simplified/Traditional/English ads and marker-in-prose hard negatives into a deterministic held-out matrix. `verify-smartclean-model.py` requires at least 500 total held-out rows, at least 100 AD rows and at least 250 BODY rows while preserving zero auto-AD hard-negative false positives, precision >= 0.995 and non-trivial recall.

## Android Free / Pro contract

Automated source/UI contracts verify:
- Free retains import/re-decode, search, chapters, bookmarks, reading settings, base TTS, exact per-book rules and Smart Clean scan/preview;
- `jingdu_pro_lifetime` is the only Android 2.3.x Pro product id;
- Billing Library stays on the approved pinned version in product contract;
- `PURCHASED` is required for unlock and completed purchases are acknowledged;
- restore uses `queryPurchasesAsync` and Billing failure does not block Free UI;
- Smart Clean candidate text is visible before Pro CTA;
- whole-line wildcard/global rule/portable-backup/offline voice actions require Pro;
- price text comes from Play product details rather than a hard-coded currency value.

`BillingEntitlementPolicyTest` additionally keeps the purchase-state policy independent of Play callbacks: PENDING/other-product rows cannot unlock, a successful authoritative no-ownership refresh revokes stale cached entitlement, and a transient Billing outage preserves the last Play-verified offline state.

License-tester device validation additionally covers fresh purchase, cancellation, pending purchase, restore after reinstall, offline launch after verified ownership and authoritative no-ownership refresh. These rows remain external Play evidence and are never fabricated by source CI.

## Portable local-user asset contract

Reader backup schema 4 is bounded, local and text-free. Automated/instrumented tests verify:

- Reader settings use the typed Reader settings import validation;
- global-rule JSON retains its versioned bounded schema and field/count limits;
- annotations remain bounded and keep source/context anchors;
- favorites/tags are portable by source identity;
- progress is staged with the source id and exact `normalizedSha256` and is consumed only by that revision;
- a mismatched normalized revision cannot consume staged progress;
- reading session/pace backup contains identifiers/timestamps/counts only;
- Smart Clean feedback backup contains one-way fingerprints/decisions only;
- backup root declares `containsBookText=false`;
- schema 3 Reader settings/rules/annotation backups remain importable for pre-production testers;
- SAF folder URI grants and unavailable imported font binaries are re-selected rather than represented as portable credentials.

`PortableUserAssetsTest` covers revision-bound staged progress, Smart Clean text-free feedback round-trip, bounded reading-stat restore and malformed-schema preflight. It now executes in the hosted functional instrumentation job rather than being compile-only evidence.

## User asset safety

- Local backup never contains source/normalized/clean book text or book files.
- Backup import validates version/types/ranges before applying bounded state.
- Selected TTS voice is persisted only by system voice name; unavailable voices fall back without breaking base TTS.
- Only voices reporting `isNetworkConnectionRequired == false` are offered as Pro offline voices.
- Batch import is SAF multi-select, bounded to the configured per-operation maximum and reports partial failures.
- Immutable private publication is isolated in `PrivateFilePublisher`; JVM tests verify completed publication, existing-target wins and failed publication leaves the candidate temporary/prior filesystem state intact.

## Privacy-safe diagnostics

Jingdu remains a no-INTERNET/no-runtime-analytics product. Production support diagnostics are therefore user-triggered local export rather than background telemetry.

Automated/instrumented tests verify:
- diagnostic history is bounded to stable error-code + operation + timestamp records;
- exception messages, paths, URIs, book names/text, search terms and purchase tokens are not stored in the diagnostic ring;
- the privacy audit declares `containsBookText=false` and explicit false flags for paths/URIs/search queries/purchase tokens;
- repository and Billing failures map to stable non-content error categories.

## Review UX

Play In-App Review source/device tests verify:
- no first-launch request;
- no sentiment pre-screen;
- only meaningful local milestones increase eligibility;
- local cooldown prevents repeated requests;
- review API failure is non-blocking.

## Store / ASO contract

`./scripts/verify-play-store.sh` is a Hosted CI gate. It verifies:
- default zh-CN/en-US metadata exists;
- title/short/full descriptions stay within Play limits;
- zh-CN title is `净读 - TXT 小说阅读器`;
- prohibited title promotion/superlative patterns are absent;
- four search-intent Custom Listing specs exist;
- Billing/Review dependency versions and `jingdu_pro_lifetime` remain pinned/declared.

Store listing experiments and Custom Listing conversion are Play Console evidence, not simulated by source CI.

## Storage/session contract

Both platform shells keep:
- immutable `document-<normalizedSha256>.txt` and `clean-<repairRevision>.txt`;
- `.jdx` as disposable non-identity cache;
- source identity stable across identical bytes;
- decode revision changes isolated from old progress/bookmarks;
- candidate session publication before old-session close/prune;
- Clean offsets isolated from normalized progress/bookmarks.

Portable progress restore adds one further invariant: staged progress never crosses a normalized-revision boundary.

## Android UI / performance

Hosted Android source acceptance now has three independent execution classes in addition to ordinary compile/lint/unit checks:

1. `android-functional` boots a pinned hosted Android emulator and executes `connectedDebugAndroidTest`, including Compose UI, paging regression, portable-user-assets and product-diagnostics instrumentation tests.
2. `android-native-compat` builds Release APK/AAB, requires `FULL` native debug symbols, verifies 16 KiB APK native-library ZIP alignment and checks every packaged ELF `LOAD` segment for >=16 KiB alignment using the pinned NDK toolchain.
3. `android-performance` retains the existing hosted Macrobenchmark/Baseline Profile regression gate. Its checked-in thresholds/baselines are independent from functional/native-compat gates and are not changed to accommodate them.

Device review additionally covers:
- edge-to-edge/predictive back;
- 200% font scale/TalkBack/48dp targets;
- compact/landscape/split/tablet/foldable windows;
- viewport paging and slider commit-on-release;
- configuration/process restoration;
- ACTION_VIEW/ACTION_SEND and multi-select import;
- Smart Clean/Pro/settings/portable-backup surfaces;
- TTS/audio focus/voice selection, auto page and sleep timer;
- hardware keyboard navigation (`Left/Right`, `PageUp/PageDown`, `Ctrl+F`, panel `Escape`) without stealing typing from active panels.

10/100/300 MiB qualification verifies no file-size-proportional work intentionally runs on the main thread, Search/Chapters reuse active session, repeat open uses valid `.jdx`, corrupt cache rebuilds and Clean/Smart Clean remain bounded-memory streaming operations.

`TtsSemanticNavigatorTest` keeps bounded sentence/paragraph navigation Unicode/code-point safe on the host. Physical TTS engine/route/audio-focus behavior remains device evidence.

Hosted Macrobenchmark/Baseline Profile is regression evidence, not physical-device product evidence. The physical workflow requires an explicit immutable `source_ref`, verifies the checked-out SHA, rejects emulators and writes source/device provenance into the evidence artifact before applying the real volume-key Reader release SLO.

## Repository/source provenance

Source-release tests/contracts verify:
- Android source/staging versions match the permanent release manifest;
- publication runs only after all required hosted source jobs, including functional instrumentation and native compatibility;
- existing source tags are never moved;
- new source tags are annotated objects resolving to the exact gated `main` commit;
- the annotated tag message binds the checked-in source-manifest SHA-256;
- interrupted publication may complete only when an orphan tag already resolves to the exact gated SHA;
- source publication never claims signed artifact, physical-device or Play production evidence.

GitHub branch/tag protection itself is repository-administration evidence for a future Play-production governance decision. It is intentionally not a current GitHub/debug-release gate and source CI must not fabricate that setting.

## HarmonyOS

Hosted CI verifies source/bridge/storage contracts. Real HAP/device validation remains on the official `self-hosted,harmonyos` runner and is not an Android-only 2.3.x source-merge blocker.

## Cross-platform parity

Before declaring both platforms jointly production-ready, compare golden corpus source SHA, normalized SHA, encoding, character count, reads, search/chapter offsets, repair revision and clean-output SHA. Smart Clean candidate parity should also be included once Harmony exposes the matching UI/API. `.jdx` is excluded from semantic identity.
