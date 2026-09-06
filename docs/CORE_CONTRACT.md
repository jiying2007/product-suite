# Shared Core Contract

`platform/text/native` is the only implementation of document algorithm semantics. Android and HarmonyOS may adapt files, threads, lifecycle, UI, TTS, commerce and OS storage, but must not fork encoding/read/search/chapter/repair/Smart-Clean behavior.

## ABI

Public boundary: `platform/text/native/include/jingdu/core_api.h`. ABI v2 is a stable C ABI used by JNI and Node-API.

Rules:
- breaking ABI changes increment `jd_abi_version()` and update bridges/tests/docs together;
- adding a new exported symbol with unchanged existing signatures may remain ABI v2;
- `jd_status` is signed 32-bit; `0` is success and positive stable values are product errors;
- no STL/exceptions/platform objects cross the boundary;
- strings and paths are UTF-8;
- `jd_handle` is process-local/opaque and closed exactly once;
- `jd_buffer` memory is caller-owned after return and freed by `jd_buffer_free`;
- offsets/counts are Unicode scalar/code-point offsets, not bytes or UTF-16 units;
- APIs are synchronous, so platform shells move whole-file work off UI threads.

## Thread safety / session ownership

The handle registry is synchronized. Read-only calls can be serialized through one active immutable session or use different handles. A handle must never be closed while another thread is using it. Android currently serializes long reader work on one worker and reuses the active immutable handle for Search/Chapters; session replacement is candidate-open → publish → old-close. HarmonyOS may use independent TaskPool handles where required.

## Limits

- sparse index stride: currently 4096 code points;
- bounded read: max 1,048,576 code points;
- search: max 10,000 results;
- chapters: max 20,000 results;
- TTS chunk: max 4,000 code points;
- Smart Clean candidate output: max 200 candidates;
- Smart Clean analyzed line: bounded to 512 bytes;
- packed repair fields/rule counts are defensively bounded by implementation and platform codecs.

Increasing limits requires performance evidence.

## Encoding / normalized input

Core document APIs accept normalized UTF-8 only. Invalid normalized UTF-8 is `JD_EUTF8`, not silent replacement. Source charset decoding remains platform-owned, but AUTO detection semantics are shared through `jd_detect_encoding`.

## Sparse index cache

`jd_open_utf8` may create/load a disposable `.jdx` sparse-index sidecar for an immutable normalized/clean path. Cache validation failure falls back to source scanning and rebuild. `.jdx` never participates in BookIdentity, normalized SHA, repair revision or cross-platform semantic parity.

## Smart Clean

`jd_noise_candidates(handle, limit, out)` is an advisory local scan. It does not modify text. Output is UTF-8 line records:

```text
score<TAB>count<TAB>reason<TAB>text<LF>
```

`score` is 0–100 confidence, `count` is the exact second-pass occurrence count for the retained candidate, and `reason` is a short explanatory category. Candidate discovery currently uses bounded-line repeated-text estimation plus explicit URL/domain/promotional markers. Platform UI must present candidates for user review before applying rules.

The algorithm must remain bounded-memory with respect to full document size; adding an ML/cloud implementation that uploads book text is outside the v2 contract.

## Repair rule pack

Record separator is `0x1e`; field separator is `0x1f`.

### Literal rule

```text
find<0x1f>replacement
```

Literal rules replace every exact occurrence in each streamed line, preserving existing v2 behavior.

### Safe whole-line wildcard rule

```text
@g<0x1f>pattern<0x1f>replacement
```

`pattern` supports only `*` as a wildcard and is matched against the trimmed whole line. It is not a regular expression and must not acquire regex/backtracking semantics implicitly. This keeps performance predictable on very large TXT files. A matching line becomes `replacement` (empty means delete line content); non-matching lines are unchanged.

## Derived revisions

`jd_repair_revision(normalizedSha256, rulePack)` is deterministic. Any change to effective per-book/global rules creates a different repair revision. A derived clean file is valid only for exactly that normalized SHA + packed rules.

## Offset domains

Persisted progress/bookmarks belong only to the normalized source revision. Clean output currently has no source↔derived projection in ABI v2, so Clean offsets are never persisted into normalized progress/bookmarks.

## Commerce boundary

Billing entitlement is deliberately outside the Core. Core Smart Clean/wildcard primitives are deterministic local capabilities; Android decides which UI actions require Pro. Private text is never passed to Google Play Billing or Review APIs.