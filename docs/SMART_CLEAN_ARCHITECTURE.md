# Smart Clean 4 and Chinese Conversion Architecture

## Product intent

Jingdu remains a TXT-only, offline, privacy-first reader optimized for Chinese long-form TXT. Intelligence removes repetitive cleanup work without turning the reader into a cloud service, a remote AI client, or a heavyweight whole-document ML pipeline.

## Smart Clean 4 pipeline

### L0 — Encoding guard

The shared Core remains authoritative for encoding detection/normalization. UTF/GB18030/GBK/GB2312/Big5 handling stays deterministic and users retain manual re-decode from the immutable source copy.

### L1 — Versioned deterministic signatures

`BuiltinCleanRules.PACK_VERSION` identifies the in-app signature pack. Rules are reviewed source assets with stable IDs, locale and confidence metadata. Updates ship with the app; there is no remote rule download.

### L2 — Native statistical scan

The shared Core detects URL/promotion/repetition candidates with bounded memory and stable reason codes. This remains the primary whole-document detector.

### L3 — Bounded streaming refinement

`SmartCleanRefiner` performs a streaming second pass over normalized UTF-8 for deliberately conservative candidate classes:

- inline promotional suffix/fragments;
- lines with a very high malformed/replacement/control-character ratio.

The refiner never owns the complete book in managed memory, caps candidate memory and never mutates source text. These higher false-positive-risk classes are not auto-selected merely because they were detected.

### L4 — Candidate-only local semantic classifier

`TinyLocalSemanticCandidateClassifier` accepts one already-filtered candidate string, capped at 512 characters, and returns `BODY`, `AD` or `UNCERTAIN` plus a score/confidence. It uses a deterministic 64-bucket hashed character-bigram linear model with signed-int8-style weights and auditable structural features.

It has no file, `ReaderController`, normalized-document or network access. A whole TXT document can never be passed into this layer. Chapter-like headings receive a strong BODY bias and the middle score region intentionally remains UNCERTAIN.

Training/evaluation are reproducible repository assets:

- `quality/smartclean/train-v1.tsv`;
- `quality/smartclean/eval-v1.tsv`;
- `scripts/train-smartclean-model.py`;
- `scripts/verify-smartclean-model.py`.

CI requires the runtime weights to reproduce exactly from the training corpus and requires zero held-out BODY false positives among auto-AD decisions, auto-AD precision >= 99.5%, minimum non-trivial recall and chapter-heading protection.

### L5 — local correction memory

Users can explicitly mark a candidate as `KEEP`, `DELETE` or `PROTECT`. `SmartCleanFeedbackStore` persists only SHA-256 candidate fingerprints, reason/decision metadata and aggregate counts; it never stores candidate/book text.

A later decision replaces the earlier contribution instead of double-counting contradictory feedback. `KEEP`/`PROTECT` blocks automatic batch deletion; explicit `DELETE` is a strong user-owned signal.

## Apply / undo / batch semantics

Smart Clean converts selected whole-line candidates into explicit local repair rules. Single-book selected apply + one-step undo is Free; reusable wildcard/global rules and batch automation remain Pro. Before applying a set, Jingdu stores one rule-only snapshot so the latest apply remains reversible without retaining book text.

Pro batch automation follows the same rules across at most 100 library books:

1. dry-run diagnostics first;
2. exclude inline/garbled, protected/kept and semantic-BODY candidates;
3. apply only precision-first safe candidates after explicit user action;
4. write local rule metadata and immutable derived Clean revisions only;
5. never modify/delete source TXT.

Batch reports contain identifiers/names/scores/counts only and explicitly declare `containsBookText=false`.

## Chinese conversion

Chinese conversion is independent from Clean. It changes presentation, not document identity.

Supported modes:

- ORIGINAL
- SIMPLIFIED (`t2s`)
- TRADITIONAL (`s2t`)
- TAIWAN (`s2tw`)
- TAIWAN_PHRASES (`s2twp`)
- HONG_KONG (`s2hk`)

Android uses OpenccJava 1.4.2, a pure-Java OpenCC-compatible engine, avoiding a second JNI/C++ runtime.

### Offset invariant

The shared Core, bookmarks, Smart TOC, search hits and persisted progress remain in normalized source code-point offsets. Reader page state stores the original Core window. Compose converts only bounded presentation strings and maps visible converted length back to source code points before navigation. Search contexts, chapter titles and TTS chunks are converted only after source offsets are resolved.

No full converted-book file is created.

### Local override dictionary

Reader settings may contain up to 200 `source => target` phrase overrides. Overrides are protected before OpenCC conversion and restored afterward so they take priority over the standard dictionary. They participate in local backup and are never uploaded.

## Performance and privacy constraints

- The core Reader `src/main` manifest has no `INTERNET`; the Google Play `src/release` overlay adds `INTERNET` only for optional Billing/In-App Review, with no Jingdu-operated content/analytics backend.
- No runtime analytics/ads/account dependency.
- No remote inference or remote rule update.
- OpenCC operates on bounded UI/TTS strings; the reader window remains 6,000 characters.
- Smart Clean whole-document work is streaming/bounded.
- Semantic inference is candidate-only and quality-gated.
- Source TXT remains immutable.
- Real-device 20/100/200 MiB performance evidence remains a separate release qualification gate.
