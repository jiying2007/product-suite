# Product Requirements — Android Reader / 2.4 product track

## Objective

Make Jingdu discoverable, comfortable for long daily reading and worth paying for without weakening the complete Free reader or the offline/privacy architecture.

## P0 reader requirements

### Library / import
- Normal launch lands on Library.
- Cards prioritize title, reading state, last activity and progress; tags, TXT Health and optimization state are secondary metadata rather than encoding/size clutter.
- Single import, bounded Android shared-text import, SAF multi-select batch import and explicit user-selected folder roots are available without broad storage permission.
- Removing a book never deletes the external TXT.
- Favorites/tags remain local user metadata keyed by source identity.
- Existing local tags double as user-owned **Collections** filters, so no second library taxonomy or migration is introduced; portable Reader asset backup retains them through the existing tag contract.
- Smart collections expose Needs attention / Optimized without opening book payloads; library query/filter/sort remains metadata-only.

### Encoding / large files
- AUTO is default; manual re-decode works from retained private source bytes.
- Re-decode creates a new immutable normalized revision; progress/bookmarks/annotations never silently cross an incompatible revision boundary.
- File-size-proportional work stays off the main thread.
- Reopening a valid immutable revision reuses the Core `.jdx` cache when valid and safely rebuilds it when stale/corrupt.
- First-readable preview remains bounded and cannot become the authoritative revision.

### Reader
- Paged and continuous modes use bounded source windows and one authoritative source-offset domain.
- Search, Smart TOC, bookmarks, highlights/notes, progress seeking and base TTS remain Free.
- Reader typography includes font/size/weight/line height/paragraph spacing/first-line indent/alignment/margins and wide-screen column policy.
- Paper/Light/Night/OLED/Low Vision presentation, auto page/auto scroll, sleep timer and configurable volume-key behavior remain Free.
- Reader intent restores through stable source id/revision/offset after configuration/process recreation; native handles/TTS runtime state are recreated.
- Selection and annotation ranges map through display transformations back to source offsets.
- Background TTS uses the local media-session path and semantic previous/next navigation. Before any book text is spoken, the selected/default engine must expose an installed voice with `isNetworkConnectionRequired=false`; if no offline voice exists, playback fails closed instead of allowing the engine to choose a network voice.

## P0 Smart Clean requirements

### Free single-book rescue
- Smart Clean scan is fully local and available to Free users.
- It detects bounded-line high-frequency repetition, URLs/domains and common promotional/watermark markers.
- Candidate UI shows exact text, reason, occurrence count and confidence before any purchase request.
- User controls candidate selection; scan never modifies content.
- Applying selected candidates for the current book and one-step undo remain Free and create only local derived rules/revisions.
- KEEP/DELETE/PROTECT correction memory stores one-way fingerprints and decisions, never candidate/book text.

### Pro automation
- Pro begins at reusable/cross-book automation, not at repairing one book.
- Safe whole-line wildcard rules use `*` matching and run in the shared Core; arbitrary regex is not accepted.
- Existing exact literal rules remain Free.
- Whole-line wildcard export must preserve ordinary content and change only matching lines.
- Batch apply excludes protected/body/unsafe candidate classes unless explicit user DELETE intent makes the decision authoritative.

## P0 monetization requirements

- One-time product ID is exactly `jingdu_pro_lifetime`.
- There is no subscription while the product has no recurring server service.
- Grant Pro only for Google Play `PURCHASED` state; pending purchases never unlock.
- Completed purchases are acknowledged.
- Owned purchases are queried on connection/resume for restore/reinstall.
- Last Play-verified entitlement may be cached for offline use; an authoritative successful query with no ownership may revoke it.
- Billing outage/unconfigured product never blocks Free reading.
- App displays Play `formattedPrice`; no hard-coded currency price.
- Paywall is contextual after value is visible, never a first-launch blocker.

## P0 reusable user assets

- Pro global Clean rules apply to all books.
- Recommended rule pack is explicit/editable, never silently destructive.
- Global rules can be imported/exported as bounded JSON.
- Pro portable Reader backup contains privacy-minimized user-owned state: Reader settings, global rules, structural annotations, favorites/tags, revision-safe progress, reading sessions/pace, Smart Clean fingerprint decisions and pronunciation preferences.
- Backup schema 5 declares `containsBookText=false` and `containsAutomaticBookExcerpts=false`: source/normalized/Clean payloads plus automatically captured annotation excerpts/re-anchor context are excluded. Explicit user-authored notes, rule literals and pronunciation text remain portable and are declared by `containsUserAuthoredText=true`.
- Portable progress is staged against source identity + exact `normalizedSha256` and is consumed only by that revision.
- Schema 3/4 Reader backups remain importable for pre-production testers; schema 5 is the current export format. Schema 4 annotation excerpt/anchor fields are discarded when restored through the portable path.
- Import validates schema, field sizes, rule/annotation/library/session/feedback counts and privacy markers.
- SAF URI grants are not represented as portable credentials and must be explicitly re-selected on a destination install.
- Imported font binaries are re-selected when unavailable; backup may retain the preference reference but must fall back safely.

## P1 retention requirements

- Batch import handles partial failure and reports success/failure counts.
- Pro can select an installed Android TTS engine and then select/search/preview voices that engine reports as not requiring network; Free keeps the system-default engine but still binds an installed offline voice before speech. No tier may silently fall back to a network-required voice. Device-local engine package choice is not portable backup identity and falls back safely when unavailable.
- Reading sessions/history/pace are local-only and never require analytics SDK/network upload.
- Play In-App Review is milestone based after meaningful use; no first-launch prompt and no sentiment pre-screen.
- Review request frequency is locally throttled.

## P0 ASO/store requirements

- Default Simplified Chinese store title: `净读 - TXT 小说阅读器`.
- Default Play discovery listings are maintained for zh-CN / zh-TW / zh-HK / en-US / ja-JP / ko-KR; ja-JP/ko-KR copy must disclose that the current in-app UI falls back to English.
- Store metadata obeys Play title/short/full description length limits and avoids promotional superlatives in title.
- Four search-intent Custom Listing specs exist: TXT reader, encoding rescue, Smart Clean/noise removal, local/private novel reading.
- Screenshot brief tells a problem/solution story: TXT Health, mojibake/layout/TOC/noise rescue, optional Pro batch automation, reading comfort, navigation, long-session tools and privacy.
- Store claims must be supported by product behavior/device evidence; no unverifiable “秒开/最快/#1” claims.

## Privacy requirements

- The core Reader `src/main` manifest does not request `android.permission.INTERNET`; the final Google Play `src/release` overlay requests it only for optional Google Play Billing and In-App Review.
- No account, advertising SDK or runtime analytics SDK.
- Google Play Billing and In-App Review are allowed platform commerce/feedback integrations; they must not receive private TXT content.
- Source TXT is never modified or deleted.
- Portable backup/privacy/batch exports contain no complete source/normalized/Clean payload or automatically captured正文 excerpt. Explicit user-authored note/rule/pronunciation literals may be present in their corresponding user-requested exports and must be labeled as user-authored text rather than misrepresented as automatic source capture.

## Source acceptance

Exact candidate head must pass all six hosted jobs:
- `native-core`;
- `android`;
- `android-performance`;
- `play-store-contract`;
- `harmony-contract`;
- `terminal-contract`.

The Android job includes Debug/Release compile, lint, release bundle, AndroidTest assembly and Reader portable-asset test compilation. Hosted performance remains regression evidence rather than physical-device release qualification.

## Production acceptance

Source merge/source release is not Google Play production readiness. Before production rollout, `PRODUCTION_READINESS.md` must have concrete external evidence for:
- platform-enforced `main`/release-tag repository governance;
- signed AAB and mapping/checksum/certificate provenance;
- physical Android device matrix and release performance SLOs;
- active `jingdu_pro_lifetime` plus license-test purchase/pending/cancel/restore/offline/no-ownership behavior;
- localized listing/policy state;
- internal/closed Play-installed testing;
- first-production publication or staged-update rollout evidence (as applicable), with Vitals/crash/ANR signals and source-tag/AAB-checksum traceability.
