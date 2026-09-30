# UX — Android Reader / 2.4 product track

## Design direction

Jingdu is a calm Material 3 reading product, not an engineering toolbar and not a paywall-first app. Library and Reader are the two product states. TXT Doctor, Search, Smart TOC, annotations, Clean, encoding and settings are contextual/product surfaces around the reading loop.

Commercial UX follows one rule: **show useful local results before asking for money**. Free users must be able to read normally, run TXT Health, inspect Smart Clean candidates and apply/undo selected single-book suggestions; Pro appears only when reusable/cross-book automation or other reusable assets are requested.

All user-facing copy is resource-backed. Android supports Simplified Chinese, Traditional Chinese and English; layouts must tolerate wording expansion and 200% font scale without relying on fixed Chinese string lengths. UI locale and document language are separate concepts.

## Library

- In-app brand follows the active locale (`净读` / `淨讀` / `Jingdu`); store discovery titles may include localized TXT-reader keywords.
- Supporting privacy promise is localized from resources rather than embedded in Compose source.
- Primary action imports one TXT; batch import/folder library are secondary explicit SAF actions.
- Cards prioritize title/progress; favorite/tags/TXT Health/optimization state/last-read are secondary. Encoding and byte size stay out of the normal card hierarchy.
- Smart collections expose Needs attention / Optimized without a horizontally hidden filter rail.
- Empty state explains mojibake rescue, TXT Health/Clean and privacy before file selection.

## Reader

- Text owns the screen; controls are overlays and can auto-hide for immersive reading.
- Paged and continuous modes share the same authoritative source-offset model.
- TXT Health is a first-level Reader tool; Search, Smart TOC, annotations and reading settings remain discoverable without permanently consuming text space.
- Page tap zones, horizontal swipe and optional volume-key paging must arbitrate cleanly with text selection/TTS/auto motion.
- Reader settings use categorized full-screen navigation for advanced configuration rather than one oversized sheet; common typography remains visible while letter/paragraph spacing, custom font, indentation/alignment and named themes are progressively disclosed.
- Selection/highlight/note ranges are mapped back to source coordinates even when display transformations are active.
- Reading Map/history/remaining-time surfaces are local Reader aids, not analytics.
- System back closes an active panel before Reader → Library; predictive back remains compatible.
- Long English or Traditional-Chinese labels must wrap/ellipsize according to Material hierarchy instead of forcing fixed widths.

## Clean conversion flow

### Smart Clean is not a hidden Pro teaser
1. User opens Clean/Smart Clean.
2. The localized free-scan action runs locally.
3. Results show localized reason plus exact text, count and confidence/model signals.
4. User can include/exclude candidates and record KEEP/PROTECT/DELETE intent.
5. Applying the selected suggestions for this book is Free and creates a reversible derived Clean revision.
6. Pro is offered only for wildcard/global rules, batch automation, portable reusable assets or selectable installed offline voices.

Core candidate reasons are stable codes; the Android UI maps those codes to the active locale. Smart Clean content detection itself covers Simplified and Traditional common promotion/watermark forms and never follows the UI locale.

No first-launch Pro modal and no artificial blur/hidden candidate text.

### Per-book rules
- Exact literal rules remain Free.
- Empty replacement means delete.
- Safe whole-line wildcard `*` is labeled Pro and explains that it matches a whole line.
- No arbitrary regex editor in the current product line.

### Global rule library
- Clearly labeled Pro/user-owned asset.
- Recommended Simplified and Traditional rules are opt-in and editable.
- Import/export is visible only as an explicit user action.
- Applying rules never mutates source TXT.

## Search / Smart TOC / annotations

- Exact text search remains primary.
- Android may additionally try curated one-to-one Simplified/Traditional character variants and merge duplicate offsets.
- The UI does not claim general-purpose conversion; ambiguous mappings are intentionally not guessed.
- Cross-script search behavior follows document/query text, not the selected UI language.
- Smart TOC may diagnose/hide/add chapter headings as metadata overlays without rewriting the TXT.
- Bookmarks/highlights/notes are source-range assets and expose search/filter/export affordances without creating a second coordinate system.

## Reading settings

Free groups include:
1. reading mode and page tone/theme;
2. font/font size/weight/line height/paragraph spacing/indent/alignment/margins/columns;
3. base TTS rate/pitch and start/stop;
4. auto page / auto scroll;
5. sleep timer, gesture defaults and accessibility-oriented presets.

Pro groups include:
- searchable offline TTS voice selection with local preview, showing only voices the system engine marks as not requiring network;
- portable local-user backup/restore.

When no voice has been explicitly selected, Android infers a suitable `zh-CN`, `zh-TW`, `zh-HK` or English TTS locale from the current document text. Engine package selection is a device capability, not portable Reader backup state; changing engine clears an incompatible saved voice. A user-selected offline voice always has priority over automatic language selection.

## Portable backup UX

The Pro backup surface represents **user-owned local Reader assets**, not books/cloud sync.

Current Reader schema-4 backup includes:
- Reader settings;
- global Clean rules;
- bookmarks/highlights/notes;
- favorites/tags;
- progress staged against exact source + normalized revision identity;
- reading sessions/pace;
- Smart Clean KEEP/DELETE/PROTECT fingerprint memory.

Backup copy must explicitly state:
- `containsBookText=false` / book正文 is excluded;
- nothing is uploaded by Jingdu;
- source/normalized/Clean files are not embedded;
- progress is restored only to the exact normalized revision;
- SAF folder access must be selected again on a new install/device;
- unavailable imported font files must be re-selected and otherwise fall back safely.

The user must never be led to believe this JSON is a cloud/book-library backup.

## Pro purchase surface

- Product is one-time lifetime, never described as subscription.
- CTA uses Google Play `formattedPrice` when available.
- Billing/error messages are localized with Android resources.
- If Billing/product details are unavailable, show retry/restore wording without blocking Free features.
- Existing owners always get a visible localized restore/re-check path.
- `PENDING` purchases do not show Pro unlocked.

## Review prompt

- No review on first launch.
- Request only after meaningful local milestones such as multiple book opens, Smart Clean use, encoding rescue or successful batch automation.
- No “Do you like Jingdu?” pre-gate.
- Respect Play’s system UI and local cooldown.

## Reading surface / navigation

- Base type remains adjustable across the supported comfort/accessibility range; Low Vision is an explicit preset rather than a separate app mode.
- Expanded windows cap text measure and may use columns rather than stretching paragraphs.
- Viewport layout reports visible code-point span for sequential paging.
- Search/chapter/annotation/progress jumps participate in bounded location history.
- Clean/display-conversion offsets never persist into normalized-source progress/bookmarks/annotations.
- Auto page, auto scroll and TTS are mutually arbitrated so the user always has a direct pause/stop path.

## Adaptive / accessibility

- Material controls retain 48dp targets.
- Icon-only actions have localized content descriptions.
- 200% font scale keeps primary navigation usable.
- Low Vision preset increases type/line-height and uses a focused alignment/ruler policy.
- Color is never the sole state signal.
- Busy work has progress indicator + localized descriptive text.
- Destructive actions require confirmation.
- Pro is never communicated by color alone; use text/icon labels.
- Locale changes must not alter document identity, reader offset semantics, rules, annotations or TTS voice preference.

## Error language

Errors explain next action rather than exposing native status codes. Translation belongs in Android resources; stable internal error/reason codes may cross business boundaries, but localized display text must not be persisted as identity or protocol data.

Representative categories include encoding/import failure, Google Play unavailability, TTS readiness/audio focus, invalid rule/backup format, backup size/privacy validation and export destination failure. All active locales must retain equivalent actionability rather than literal machine translation.

## Localization verification

- `en-US`, `zh-Hans` and `zh-Hant` resource key sets must stay identical.
- Format placeholders (`%1$s`, `%1$d`, etc.) must stay aligned across locales.
- Major Compose and runtime-controller files may not contain hard-coded CJK UI copy.
- Compose AndroidTest resolves labels/descriptions through `targetContext.getString(...)`, so the same smoke test contract works under the active locale.
- Hosted CI assembles AndroidTest sources; real-device locale switching at `zh-CN`, `zh-TW`, `zh-HK`, `en-US` belongs in the Android qualification/device matrix.

See `LOCALIZATION.md` for resource and store-locale structure.

## Store-to-product continuity

The first-run experience must deliver the same promises shown in localized Play screenshots: encoding rescue, TXT Doctor/local Smart Clean, long-form comfort and privacy. Store imagery must not advertise roadmap-only features, and English localization must not imply EPUB/cloud catalog support that the product does not provide.

Google Play production wording is allowed only after the external evidence contract in `PRODUCTION_READINESS.md` is complete for the exact production AAB/source tag.
