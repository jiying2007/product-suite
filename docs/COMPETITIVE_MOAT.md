# Competitive Moat — TXT Doctor / Smart Layout / Smart Clean 4 / Smart TOC / Local Automation

## Product boundary

Jingdu wins by going deeper on difficult Chinese long-form TXT, not by matching broad-format readers feature-for-feature.

The terminal product loop is:

```text
select TXT / folder
  -> first-readable bounded preview
  -> private immutable import + normalization/index
  -> TXT Health guided diagnosis
  -> TXT Doctor + Smart Layout presentation repair
  -> Smart TOC structure intelligence
  -> Smart Clean 4 explainable cleanup
  -> long-session reading / background TTS
  -> reusable local feedback, pronunciation, rules and batch automation
```

The competitive promise is not “support more formats.” It is: **take a TXT that another reader treats as broken or ugly, open it correctly, explain what is wrong, repair presentation/structure/noise safely, and keep it comfortable through a long reading session.**

The source TXT is never modified or deleted. Book text is never uploaded. The core Reader remains network-independent; the Google Play release overlay adds `INTERNET` only for optional Billing/In-App Review and does not introduce a Jingdu-operated content or analytics backend.

## P0 — immediate differentiated value

### TXT Health / Guided Repair

TXT Health is the first-class discovery surface over the existing repair stack. One explicit local check summarizes encoding integrity, hard-wrap/layout evidence, TOC quality and Smart Clean candidates, then routes the reader directly to the relevant repair surface. Only score/count/issue metadata is persisted; sampled text remains transient. The Library can therefore expose **Needs attention** and **Optimized** smart collections without opening every book.

Single-book repair is a trust feature: selected Smart Clean candidates may be applied and undone in Free. Pro starts where saved repetitive work begins — wildcard/global rules, batch diagnostics/apply, portable assets and selectable installed offline voices.

### TXT Doctor

TXT Doctor combines bounded text-integrity sampling with encoding, TOC and Smart Clean results. The report stores only scores/counts/metadata, never sampled book text.

### Smart Layout

Legacy TXT often has a fourth defect beyond encoding, chapter structure and ads: fixed-width hard wrapping, inconsistent blank lines and paragraph presentation that makes otherwise correct text unpleasant to read.

Smart Layout is a bounded display-layer repair, not a source rewrite. It joins a newline only when the local window has strong evidence of hard wrapping and refuses joins across headings, blank paragraphs, indentation, fresh dialogue/block openers or strong terminal punctuation. The transformation is followed by an exact monotonic source/display projection, so bookmarks, annotations, search, TOC, progress and Smart Clean remain in Core source coordinates.

This closes a differentiated repair chain that broad readers typically treat as unrelated settings:

```text
encoding wrong   -> re-decode from immutable source
layout wrong     -> Smart Layout display repair
structure wrong  -> Smart TOC overlay repair
noise wrong      -> Smart Clean explain/apply
```

### Smart TOC

Core source code-point offsets remain authoritative. Smart TOC augments Core chapter headings with carefully verified special Chinese headings and reports duplicates, nearby numeric gaps and suspicious titles. User hide/add corrections are metadata overlays; they do not rewrite source text or offsets.

### First-readable import

New single-book import reads at most 512 KiB and renders at most 12,000 code points before the full private copy/normalization/indexing completes. The bounded preview is disposable and is not treated as the canonical document revision.

### Long-session Chinese Reader

Reader quality is part of the moat rather than a neutral shell around repair tools. The same bounded source model now carries phrase-aware CJK line breaking, Web-novel/Paper-book/Large/Night/Low-Vision scenarios, visual-center continuity across reflow, system reduce-motion, foldable book posture, local Process Text dictionary actions, chapter remaining time and local TTS pronunciation overrides.

None of those capabilities creates a second document identity. They compound the TXT-specific repair advantage because the user can stay inside one reader after rescue instead of exporting a “fixed” file to another application.

### Professional TTS

Android can move read-aloud into an exported=false foreground mediaPlayback service with MediaSession controls. Pro may select any installed Android TTS engine; if a Piper or other neural provider exposes an offline Android TTS engine, Jingdu can use it without bundling an unqualified model or sending book text through a Jingdu service. Lock-screen/headset controls operate on source offsets. Current reading-window highlight is presentation-only. Literal local pronunciation overrides compose through the TTS source projection, so Chinese names/polyphones may be corrected without rewriting the book.

## P1 — retention and paid automation

### SAF folder library

Library search/filter/sort stays metadata-only and has a 10,000-book regression guard. Existing local tags become user-owned Collections filters, while TXT Health summaries power Needs attention / Optimized smart collections; neither path opens book payloads.

Folder roots are selected explicitly through the Storage Access Framework. Jingdu persists read-only URI permission and uses documentId + size + lastModified signatures to skip unchanged TXT files. It requests no broad storage permission.

### Pro batch automation

Batch automation scans at most 100 library books, supports dry-run before apply, and only auto-applies precision-first safe candidates. KEEP/PROTECT and semantic BODY decisions block automatic deletion. Batch output may create local repair-rule metadata and immutable derived Clean revisions; source TXT is unchanged.

Reports contain book identifiers/names, scores and counts only and declare `containsBookText=false`.

### Verifiable privacy

The installed app can report whether INTERNET permission is absent and whether runtime analytics/ads/book-upload capabilities are present. Exported privacy audits contain configuration/counts and bounded stable diagnostic codes only, not book text, paths, URIs, search queries or purchase tokens.

### User-owned local assets

Retention compounds without an account: annotations, TOC repairs, Clean feedback/rules, reading history/pace, themes, exact-revision progress and the bounded local TTS pronunciation dictionary remain user-owned local assets. Portable backup carries the text-free Reader state while excluding source/normalized/Clean book payloads.

## P2 — Smart Clean 4 data/model moat

### Feedback memory

`KEEP`, `DELETE` and `PROTECT` decisions are stored as SHA-256 candidate fingerprints plus reason/decision metadata. The store does not persist candidate text. Book-specific decisions override generic learned deltas.

### Candidate-only tiny model

The semantic classifier accepts one bounded candidate string (maximum 512 characters). It never receives a file, ReaderController or whole document. The runtime model is a deterministic 64-bucket hashed character-bigram linear classifier with signed-int8-style weights and auditable structural features.

The wide middle score range is `UNCERTAIN`; chapter-like headings receive a strong BODY protection bias. The model may strengthen or protect a pre-filtered candidate but is not allowed to scan a complete book.

### Reproducible quality assets

- `quality/smartclean/train-v1.tsv` is the local labeled training corpus.
- `quality/smartclean/eval-v1.tsv` is the manually curated held-out hard-negative-heavy evaluation set.
- `quality/smartclean/eval-v2-matrix.json` expands independent adversarial coverage to a production-scale combined held-out matrix.
- `scripts/train-smartclean-model.py --verify-source ...` proves runtime weights are reproducible from the training corpus.
- `scripts/verify-smartclean-model.py` enforces zero held-out BODY false positives among auto-AD decisions, auto-AD precision >= 99.5%, minimum non-trivial AD recall, chapter-heading protection, minimum held-out row/category counts and candidate-only source constraints.

Precision is deliberately more important than recall: missing one ad is annoying; deleting prose is unacceptable.

## What Jingdu deliberately does not compete on

Jingdu is not a reduced ReadEra/Moon+/KOReader clone and is not a source-ecosystem replacement for Legado. It deliberately does not chase:

- format-count leadership;
- dozens of page animations or gesture-count leadership;
- online bookstore/community/source ecosystems;
- cloud accounts as a prerequisite for retention;
- remote AI over private book text;
- advertising-driven recommendation feeds.

Those categories reward breadth. Jingdu's defensible depth is Chinese TXT rescue + safe repair + long-session local reading.

## Long-form invariants

- Source/Core offsets remain the only persisted reading coordinate system.
- No whole-document managed copy is introduced.
- No arbitrary whole-book regex is introduced.
- No generic EPUB/PDF/MOBI expansion is introduced.
- Smart Layout is display-only and projection-backed.
- Smart Clean remains explainable and reversible.
- Folder sync, batch automation, TTS and model inference remain local.
- CJK typography/foldable/reflow work must not redefine hosted performance thresholds or baselines.
- Real-device 20/100/200 MiB SLO evidence remains a release/device qualification gate and is not fabricated by hosted CI.
