# Product — 净读 TXT

## Positioning

**净读 is the offline cleaner-reader for Chinese long-form TXT.** It deliberately competes on TXT depth rather than format count.

> **Long TXT · Smart Clean · Fully local**  
> **长篇 TXT · 智能净化 · 完全本地**

The terminal promise is:

> Open messy TXT correctly, become readable quickly, diagnose what is wrong, repair presentation/structure/noise safely, keep very long books responsive, and preserve long-session reading without sending book text anywhere.

Store names remain localized for discovery: `净读 - TXT 小说阅读器`, `淨讀 - TXT 小說閱讀器`, `Jingdu - TXT Novel Reader`.

## Current product generation

- Shared Core ABI: **v2**.
- Smart Clean: **generation 4**.
- Built-in deterministic clean signature pack: **v3**.
- Chinese display conversion: OpenCC-compatible **OpenccJava 1.4.2**.
- Current immutable source baseline: **2.3.12 / Reader**.
- Active product track: **2.4 Guided TXT Rescue** — TXT Health, guided repair, smart collections and long-session evidence.
- Portable local-user backup schema: **v5** (`schema=5`, Reader; schema 3/4 remain importable for pre-production testers). Schema 5 excludes automatically captured book excerpts/re-anchor context while retaining explicitly user-authored note/rule/pronunciation text with explicit privacy markers.
- First-class Android UI locales: **zh-Hans / zh-Hant / en-US**.
- Play discovery listings: **zh-CN / zh-TW / zh-HK / en-US / ja-JP / ko-KR**; Japanese/Korean listings disclose English in-app fallback.

Detailed intelligence architecture lives in `SMART_CLEAN_ARCHITECTURE.md` and `COMPETITIVE_MOAT.md`.

## Primary users

1. Readers with local Chinese TXT novels/archives who want a calm daily reader.
2. Users who encounter GB18030/GBK/GB2312/Big5/UTF-16 mojibake.
3. Web-novel readers whose TXT contains ads, watermarks, URLs, repeated tails, malformed fragments, fixed-width hard wraps or noisy chapter structure.
4. Long-session readers who care about 20/100/200 MiB responsiveness, stable progress/bookmarks and background TTS.
5. Readers with many TXT files who benefit from explicit folder libraries and batch automation.
6. Privacy-conscious users who do not want accounts, ads, analytics, cloud upload or remote AI in the reading path.

## Product loop

```text
TXT / user-selected TXT folder
  -> bounded first-readable preview
  -> immutable private source + normalized revision/index
  -> TXT Health / guided diagnosis
  -> TXT Doctor + Smart Layout
  -> Smart TOC
  -> Smart Clean 4
  -> reading / OpenCC display / background TTS
  -> reusable local rules, corrections, pronunciation, feedback and optional Pro batch automation
```

## Open it correctly and quickly

- Single TXT import, Android shared-text import, multi-select import and user-selected SAF folder roots.
- AUTO encoding detection plus manual re-decode from the retained private source copy.
- Source bytes are never modified or deleted.
- New single-book import can render a disposable bounded preview from at most 512 KiB / 12,000 code points before full canonical private import/normalization/indexing completes.
- The preview is never treated as the authoritative document revision.
- Folder roots use persisted read-only SAF URI permission; no broad storage permission is requested.
- Folder synchronization uses document identity + size + last-modified metadata to skip unchanged files. Providers that do not supply reliable metadata are conservatively re-imported rather than silently missing changes.
- Content-addressed immutable normalized revisions and `.jdx` sparse-index caches remain the canonical large-file architecture.

## TXT Doctor

TXT Doctor turns technical subsystems into one user-understandable health report:

- encoding/text integrity;
- malformed/replacement-character samples;
- chapter/TOC quality;
- Smart Clean candidate count/cleanliness;
- combined TXT health score.

Diagnosis uses bounded Core windows and stores scores/counts only, never sampled book text.

## Smart Layout

Smart Layout repairs presentation defects without creating another document revision.

- It is bounded and display-only; the normalized source remains authoritative.
- It detects fixed-width hard wrapping only when a Reader window has strong local evidence of consistent wrap width and enough safe joinable line boundaries.
- It never joins across blank paragraphs, detected headings, paragraph indentation, fresh dialogue/block openers or strong terminal punctuation.
- It normalizes truly excessive blank-line runs as part of the same presentation pass.
- Every transformation is followed by a monotonic `TextProjection` back to source code-point coordinates.
- Search, Smart TOC, bookmarks, annotations, progress, Smart Clean and TTS source identity therefore remain unchanged.
- Disabling Smart Layout restores the original line structure immediately; no TXT/source/normalized/Clean file is rewritten.

Smart Layout closes the product repair chain between “decoded correctly” and “comfortable to read”: encoding rescue fixes bytes, Smart Layout fixes presentation, Smart TOC fixes structure metadata, and Smart Clean fixes distracting text through explicit derived revisions.

## Smart TOC

- Core source code-point offsets remain authoritative.
- Base chapter detection is augmented with verified Chinese special headings such as 序章、楔子、番外、后记/後記、尾声/尾聲 and 大结局/大結局.
- TOC quality reports duplicate titles, nearby numeric gaps and suspicious overlong/prose-like headings.
- Users may hide an incorrectly detected heading or add a chapter at the current source offset.
- These repairs are local metadata overlays; they never rewrite TXT or change the persisted reading coordinate system.

## Smart Clean 4

Smart Clean is precision-first and explainable.

### Detection stack

1. deterministic versioned built-in signatures;
2. shared native URL/promotion/repetition statistics;
3. bounded streaming inline-fragment/malformed-line refinement;
4. candidate-only tiny local semantic classifier;
5. local KEEP / DELETE / PROTECT feedback memory.

Every normal candidate remains inspectable with reason, risk, count, approximate impact and sample text before apply. Inline/garbled and semantic-BODY evidence stays conservative.

### Tiny local model

- accepts one pre-filtered candidate only, maximum 512 characters;
- 64 hashed character-bigram buckets with signed-int8-style weights and auditable structural features;
- outputs BODY / AD / UNCERTAIN;
- wide UNCERTAIN range and strong chapter-heading protection;
- never opens a file, receives a whole document or uses a network/runtime ML service.

Training and held-out hard-negative evaluation are checked into the repository and CI verifies exact reproducibility plus precision-first safety gates. The production maturity corpus combines the manually curated v1 held-out set with an adversarial v2 matrix so the quality gate is no longer a tiny regression-only sample.

### User correction memory

- `KEEP`: do not auto-delete this candidate for the book and contributes a keep signal.
- `DELETE`: explicit user-owned delete signal.
- `PROTECT`: strongest protection signal for batch automation.
- Only SHA-256-derived candidate fingerprints + decision metadata are stored; candidate/book text is not retained in the feedback store.
- Portable backup stores only `bookId + fingerprint + decision`; it never stores candidate text.

### Apply / undo

Selected cleanup becomes explicit local rules and an immutable derived Clean revision. The source TXT stays untouched. The immediately previous rule set is retained as a rule-only one-step undo snapshot; history does not copy book text.

## Chinese display conversion

OpenCC-compatible conversion is a display layer, not a source rewrite.

Modes:
- Original;
- Simplified (`t2s`);
- Traditional (`s2t`);
- Taiwan (`s2tw`);
- Taiwan phrases (`s2twp`);
- Hong Kong (`s2hk`).

Conversion applies only to bounded reader/search/chapter/TTS strings. Source TXT, normalized revision, bookmarks, search offsets, TOC offsets and progress remain in normalized source code-point coordinates. Up to 200 local `source => target` phrase overrides take priority over OpenCC and participate in local backup.

## Keep me in the text

- Library: recent/progress, favorite, local tags, unread/reading/finished filters and recent/name/progress sort.
- Search, Smart TOC, bookmarks and direct progress seeking.
- Source-identity bookmarks survive encoding rescue.
- Progress persistence is throttled and force-flushed at lifecycle/navigation boundaries.
- Web-novel, Paper-book, Large-text, Night and Low-Vision reading scenarios remain editable into Custom/named themes.
- CJK phrase/punctuation-aware line breaking improves Chinese prose while paged layout retains its fixed-cost break strategy for performance.
- Typography/mode reflow keeps a transient visual-center source anchor instead of making the reader lose the sentence being read.
- Non-tabletop foldable hinges prefer the existing two-column/book posture without introducing another document model.
- System reduce-motion disables page animation at runtime without erasing the user's saved animation preference.
- Paper/Light/Sepia/Night/OLED, typography, margins, auto paging and sleep timer remain user-controlled.
- Reader intent restores from stable book/revision/offset identity, never native handles.
- Portable backup stages progress against `sourceSha256 + normalizedSha256`; stale progress is never applied to a different normalized revision.
- Long-press selection maps through presentation transforms to source ranges; optional Look up delegates only to installed Android `PROCESS_TEXT` tools.
- Local reading pace drives book remaining time and chapter remaining time in Smart skim.

## Professional local TTS

- Basic system TTS remains local/free.
- Android can host long read-aloud in an `exported=false` foreground `mediaPlayback` service.
- Framework `MediaSession` provides lock-screen and headset play/pause/next/previous/stop controls.
- Read-aloud source offsets are broadcast only inside the app package and synchronize the reader position/current-window highlight.
- Existing audio-focus behavior pauses/resumes transient interruptions without skipping the interrupted chunk; permanent focus loss stops.
- Installed offline voice selection remains a Pro convenience when the system TTS engine exposes offline voices.
- A bounded local literal pronunciation dictionary supports Chinese names/polyphones (`source => spoken text`) without regex or source rewriting. Speech-range projection composes back to source offsets, and the optional dictionary travels in schema-4 portable backup while older schema-4 backups remain importable.

## Folder library and Pro batch automation

Folder libraries are available through explicit user-selected SAF roots and remain a local organization feature.

Pro sells saved repetitive work:

- batch dry-run across up to 100 library books;
- combined Smart Clean + TOC health report;
- explicit batch apply of precision-first safe cleanup candidates;
- reusable global rules/recommended rule packs;
- global-rule import/export;
- offline TTS voice selection;
- portable local-user backup/restore of text-free Reader assets.

Batch apply excludes KEEP/PROTECT, semantic BODY, inline fragment and garbled-line candidates unless an explicit DELETE decision makes the user intent authoritative. Batch reports contain identifiers/names/scores/counts only and declare `containsBookText=false`.

## Portable local-user assets

Reader schema-4 backup intentionally excludes book/source/normalized/Clean payloads but preserves the user-owned state that can safely travel:

- Reader settings and custom presentation preferences;
- global Clean rules;
- bookmarks, highlights and notes with source/context anchors;
- favorites and local tags;
- progress staged against exact source + normalized revision identity;
- local reading sessions and pace;
- Smart Clean KEEP/DELETE/PROTECT fingerprint memory;
- optional bounded local TTS pronunciation dictionary.

SAF URI grants are device/install capabilities and therefore must be re-selected on a destination installation. Imported font binaries are likewise re-selected when the referenced local font is not available. The backup declares `containsBookText=false`.

## Verifiable privacy

Privacy is an architectural feature and an in-app verifiable fact:

- The core Reader `src/main` manifest has no `INTERNET` permission; the Google Play `src/release` overlay adds `INTERNET` only for optional Billing and In-App Review, with no Jingdu-operated content or analytics backend;
- no broad storage permission;
- no account;
- no advertising SDK;
- no runtime analytics SDK;
- no book-text upload capability;
- no remote clean rules or semantic inference.

The app can export a privacy audit containing configuration/counts and bounded stable diagnostic codes only, never book text, paths, source URIs, search queries or purchase tokens. Third-party OpenCC/OpenccJava legal assets are retained in the repository and packaged into Android application assets.

## Performance position

Performance is a product feature. Qualification uses 1–5 / 20 / 100 / 200 MiB workloads, with 300 MiB as an extended stress class. Metadata-only Library search/filter/sort is regression-tested at 10,000 books, and physical 60/180-minute Reader soak automation retains process/PSS/battery/thermal evidence.

Architectural invariants:

- managed UI never owns the whole canonical document;
- bounded native reading windows;
- content-addressed immutable revisions;
- reusable validated sparse indexes;
- first-readable preview is separated from full canonical import work;
- Smart Layout is bounded display projection only;
- Smart Clean whole-document work streams with bounded memory;
- semantic inference is candidate-only.

Release-device targets and hosted Core gates remain defined in `PERFORMANCE_SLO.md`. Hosted CI is not a substitute for physical-device startup/frame/memory evidence. Richer typography/layout behavior must pass the existing performance definitions naturally; it does not justify changing benchmark definitions, SLO thresholds or hosted baselines.

## Free / Pro business model

### Free is a complete reader

Free includes:
- import/re-decode, bounded shared-text import and folder library;
- large-file reading/index cache;
- TXT Doctor, Smart Layout and Smart TOC;
- search, chapters, bookmarks, progress;
- themes/typography and reading scenarios;
- OpenCC display conversion and local phrase overrides;
- basic system TTS, local pronunciation overrides, auto paging and sleep timer;
- exact per-book Clean rules;
- TXT Health guided encoding/layout/TOC/noise diagnosis;
- Smart Clean scan/preview, selected single-book apply + one-step undo, and local correction visibility.

### Pro Lifetime sells automation and reusable local assets

One-time Google Play product: `jingdu_pro_lifetime`.

Pro includes:
- safe whole-line wildcard rules;
- reusable automation beyond one book;
- reusable global rule library / recommended patterns;
- global-rule JSON import/export;
- batch diagnostics + explicit safe batch apply;
- selectable installed Android TTS engine plus offline voice search/preview;
- portable local-user backup/restore.

No subscription is justified while there is no recurring cloud/server service. Do not move basic reading, Smart Layout, search, TXT Doctor, Smart TOC, bookmarks, themes, local pronunciation correction or base TTS behind Pro.

## Product principles

1. **TXT depth over format breadth.**
2. **Offline/privacy by architecture and verification.**
3. **Precision before deletion or structural repair.** A missed repair is preferable to corrupting prose.
4. **Explain before apply; preserve undo.**
5. **Source/Core offsets are the permanent coordinate system.**
6. **First readable before background completeness where safely possible.**
7. **Free sells trust; Pro sells saved repetitive work.**
8. **Chinese-content depth, multilingual shell.**
9. **Long-session reliability and typography are product features.**
10. **User-owned local assets compound value without an account.**

## Non-goals

- Online bookstore/OPDS/social/community.
- Mandatory account or cloud sync.
- In-reader advertising.
- AI features that upload private book text.
- Subscription without a real recurring service.
- Arbitrary whole-book regex execution.
- Generic EPUB/PDF/MOBI/multi-format breadth.
- Whole-document local LLM/BERT inference for cleanup.
- Whole-book Simplified/Traditional converted copies.
- Compatibility layers for pre-2.x experimental private state.
- Feature-count competition through many page animations or permanent Reader toolbar buttons.

## Success measures

Without adding runtime analytics SDKs, release/support/store evidence should show:

- typical TXT reaches a readable preview/open state without encoding setup;
- strongly hard-wrapped TXT becomes naturally readable through Smart Layout while ordinary paragraph/dialogue/title boundaries remain unchanged;
- typography/mode changes retain the reader's visual source anchor instead of visibly losing the sentence;
- phrase-aware CJK line breaking improves Simplified/Traditional prose without changing source identity or existing performance gates;
- source-identity progress/bookmarks survive encoding rescue;
- Smart TOC finds useful structure without changing source offsets;
- Smart Clean held-out auto-AD decisions maintain precision-first quality gates with no hard-negative BODY false positives;
- KEEP/DELETE/PROTECT memory never stores正文;
- Pro batch dry-run precedes explicit apply and never changes source TXT;
- background TTS survives ordinary Activity lifecycle changes, supports MediaSession controls and keeps source highlights correct through local pronunciation replacements;
- folder sync skips reliably unchanged documents and conservatively reimports unknown metadata;
- portable backup restores text-free user assets, including optional local pronunciation rules, and applies progress only to the exact normalized revision;
- 10/100/300 MiB real-device qualification is recorded against `PERFORMANCE.md` / `DEVICE_MATRIX.md`, with release SLOs from `PERFORMANCE_SLO.md`;
- Android resources remain complete across en-US / zh-Hans / zh-Hant;
- Android retains no broad-storage permission and no ads/analytics runtime SDK; the core Reader remains network-independent while the Play release package exposes network capability only for optional Billing/In-App Review;
- Google Play production is declared only after `PRODUCTION_READINESS.md` external evidence is complete.
