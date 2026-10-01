# Reading Experience

Jingdu's reading surface is a first-class product capability, not a thin view over TXT processing. The reader must remain comfortable for long daily sessions even when TXT Doctor, Smart Clean, Pro automation and other differentiated features are ignored.

## Product contract

The Android reader supports two explicit reading modes:

- **Paged** — bounded page windows with side tap zones, horizontal swipe paging, optional slide transition, progress seeking and previous/next controls.
- **Continuous** — a bounded rolling window around the authoritative source offset. The whole TXT is never loaded into Compose.

Both modes share the same `ReaderController` source-offset domain used by progress, bookmarks, search, Smart TOC, TXT Doctor, TTS and re-decode recovery. A UI mode must never create a second persisted position model.

## Core interactions

Default touch behavior is deliberately small and predictable:

- tap the left edge to move backward;
- tap the right edge to move forward;
- tap the center to show or hide reading controls;
- swipe horizontally to turn pages in paged mode;
- long-press remains available to Android text selection;
- optional reversed paging direction applies consistently to swipe and tap zones.

Touch zones and swipe paging may be disabled independently. User gestures stop competing motion such as auto-scroll before navigating. Hardware keyboard navigation keeps the same model: Left/PageUp moves backward, Right/PageDown moves forward, Ctrl+F opens Search and Escape closes an active Reader panel without stealing text input inside the panel.

## Immersive reading

Reader controls are overlays rather than layout chrome that permanently consumes text space. When controls auto-hide, system bars hide as well and can still be revealed transiently by the platform system gesture. Center tap restores the controls.

Reader-only brightness uses `WindowManager.LayoutParams.screenBrightness`; it never changes global system brightness. Orientation can follow the system or be locked to portrait/landscape while the reader is active. Both are restored when the reader leaves the composition.

Android's animator-duration scale is observed while Reader is active. When the user enables the platform remove/reduce-animation preference, page animation becomes `NONE` at runtime without overwriting the saved Reader preference; restoring system animations restores the user's saved choice.

## Auto page versus auto scroll

These are intentionally separate features:

- **Auto page** keeps the existing timed whole-page advance.
- **Auto scroll** advances continuous mode at a user-controlled dp/s rate.

They are mutually exclusive with one another and with TTS. Auto-scroll pauses on user touch, app backgrounding and explicit navigation. Runtime auto-scroll state is not restored after process restart; a reader must opt in again after reopening the app.

## Bounded continuous window

`ContinuousWindowReader` is read-only. It opens the same normalized private revision as the authoritative reader but only calls bounded `readAt` requests. The window includes a small back buffer so upward scrolling is possible without keeping the entire book in memory.

As the viewport approaches a window edge, the UI rebases around the current source offset. Visible display offsets are mapped back to source offsets before calling the existing sync action. Simplified/Traditional presentation conversion remains display-only.

The companion reader must never:

- save progress;
- mutate source TXT;
- own bookmarks or TOC state;
- run whole-document conversion;
- create a second index or persistence format.

## Smart Layout — repair TXT presentation, never the source

The existing presentation cleanup switch is the **Smart Layout** surface. It is precision-first and operates only inside the same bounded Reader window.

Smart Layout may repair fixed-width hard wrapping only when the local window has strong evidence: enough plausible content lines, a consistent wrap width and a high ratio of safe joinable boundaries. It refuses joins across blank lines, detected headings, paragraph indentation, fresh dialogue/block openers and strong terminal punctuation. Excessive blank-line compression remains part of the same presentation pass.

Every Smart Layout edit is followed by `TextProjection.between(source, display)`. Search, chapters, bookmarks, annotations, progress, Smart Clean and Core identity continue to use the immutable normalized source. Disabling Smart Layout immediately restores the original line structure without generating another book revision.

## Location history and visual continuity

Search, chapter, bookmark and progress-seek jumps are browser-like navigation operations. Jingdu keeps a bounded in-memory Back/Forward location stack so a reader can inspect a distant location and return to the prior reading point. The stack is session state; the persisted book progress remains the source offset owned by the main reader.

Typography and window reflow are different: changing font, line height, margins, columns, orientation, Smart Layout or Chinese presentation should not feel like a navigation jump. Paged Reader therefore captures the source offset at the visual center before reflow and repositions the new page around that same center after its viewport is measured. Paged → Continuous begins around the old visual center. This is transient source-coordinate math through the existing sync path, not a second persisted coordinate system and not a Back/Forward history entry.

## Typography and Chinese line breaking

The product includes reading scenarios rather than an engineering-only settings matrix:

- **Web novel** — paragraph spacing, no forced first-line indent;
- **Paper book** — serif-oriented layout with 2-em first-line indent and deliberately small paragraph gaps;
- **Large text**;
- **Night reading**;
- **Low Vision** — large/heavier text plus focus band.

Users may still customize every underlying value and save named themes.

The terminal typography baseline includes paper/light/sepia/night/OLED palettes, local/system/custom fonts, size, line height, letter spacing, paragraph spacing, margins, indent, alignment, weight, single/two-column wide-screen policy and optional focus/status surfaces.

Continuous Compose text uses high-quality phrase-aware line breaking and centered trimmed line height. Paged `StaticLayout` deliberately retains `BREAK_STRATEGY_SIMPLE` for fixed-cost page-turn behavior, while API 33+ applies CJK phrase/punctuation `LineBreakConfig` and a bounded document-derived zh-CN/zh-TW/zh-HK text locale. This improves Chinese break quality without redefining page-turn SLOs or moving work to the whole document.

## Foldables and expanded windows

Expanded windows already cap text measure and may use two columns. A non-tabletop hinge is treated as book posture and prefers the same two-column Reader so prose occupies the two panes instead of stretching across the hinge. Tabletop posture does not force book layout, and a hinge suppresses optional side-control placement near the crease. Foldable behavior reuses the same source offsets and Reader session; there is no foldable-only document model.

## Selection, dictionary and annotations

Selection ranges are projected back to exact source coordinates, including after display conversion and Smart Layout. Copy/share/highlight/note operate on the selected source-backed excerpt. When the local setting is enabled, **Look up** uses Android `ACTION_PROCESS_TEXT` so installed dictionary/text tools can handle the selection; Jingdu does not add a network dictionary service or upload selected text itself.

Two-stage paged selection may extend a stable source anchor across the previous/next page. Highlights and notes remain local source-range assets.

## Volume keys and TTS

Volume-key paging is configurable. The default policy turns pages only when TTS is not active, so volume buttons keep their normal audio meaning during foreground or background read-aloud. Users may explicitly choose always-page or always-system-volume behavior and may reverse the page direction.

Background TTS keeps MediaSession/lock-screen/headset controls, bounded semantic sentence/paragraph navigation and source-range highlighting. A local pronunciation dictionary accepts bounded literal `source => spoken text` rules for names/polyphones. Pronunciation edits affect only the speech projection: a length-changing spoken replacement composes with the existing source projection so TTS highlight/progress stays in source coordinates. The dictionary is stored locally and is included as an optional user-authored asset in the current schema-5 portable backup; schema-3/4 Reader backups remain importable, and missing pronunciation preferences fall back safely.

## Reading progress and remaining time

Book remaining time uses the locally learned bounded reading pace. Smart skim additionally reports current chapter progress and chapter remaining time using the next chapter source offset. These are local aids, not analytics, and never become document identity.

## Accessibility and safety

- Primary reader controls retain localized content descriptions.
- The reading surface has a localized semantic label.
- The interaction layer observes pointer events without intentionally consuming long-press text selection.
- Text-size changes remain within the existing supported range and settings continue to use localized strings across English, Simplified Chinese and Traditional Chinese.
- Auto motion always has a visible or direct tap path to pause.
- System reduce-motion is honored without erasing user preferences.
- Foldable/book-posture behavior never creates a second coordinate or persistence model.

## Validation contract

Reading-experience changes are accepted only when the ordinary hosted product gates are green. Android must compile debug/release, lint, execute AndroidTest, pass the 16 KiB runtime/native compatibility gates and pass the unchanged hosted Macrobenchmark/Baseline Profile performance gate. `scripts/verify-reading-experience.sh` locks Smart Layout, source projection, CJK line break, visual continuity, foldable, reduce-motion, dictionary, chapter pace and pronunciation invariants. Any fix that changes the PR head requires a fresh canonical run; a failed real performance run is never rerun for luck.

## Non-goals

This reading-experience layer does not add EPUB/PDF support, cloud sync, accounts, analytics, ads, network access or whole-book ML. It does not rewrite Core ABI v2, modify source TXT, add arbitrary whole-book regex or trade the existing Reader performance thresholds for richer visual effects.
