# Pose Studio UX contract

## North-star interaction

The app should feel like picking up a digital drawing mannequin. The artist should spend time **posing and matching a reference**, not navigating property panels. Primary metric: `time_to_reference_pose`.

## Workspace hierarchy

1. Scene — dominant visual surface.
2. Direct pose interaction — select/drag joints and use IK without leaving the scene.
3. Reference matching — optional local image behind the mannequin with alignment controls.
4. Integrated speed tools — Neutral reset, mirror/copy/ground without leaving the scene.
5. Camera — composition controls.
6. Light — secondary form-clarity controls, not a product headline.
7. Project — storage, recovery and export.

Project name and unsaved state remain visible in the top bar.

## Direct manipulation

- Pointer down near a visible joint selects within a density-independent 48 dp hit radius.
- A clearly nearest joint wins; when projected joints fall within a 12 dp overlap band, the visually front-most joint wins before distance tie-breaking so foreshortened/overlapping limbs are less error-prone.
- Wrists/ankles use two-bone IK.
- Other joints preserve their parent bone length and move descendants coherently.
- Pelvis translates the full mannequin.
- Empty-scene drag orbits the camera.
- Two-finger gestures pan the persistent camera target and pinch zoom without moving the mannequin itself.
- Screen-to-world drag respects camera pitch/FOV and selected-joint depth.
- One continuous pose gesture creates one undo snapshot.

Depth manipulation remains an explicit 0.3 improvement area: normal posing should progressively rely less on Forward/Back buttons.

## Reference overlay

- A local image may be chosen through Android's document picker.
- The reference renders **behind** grid/mannequin controls and never intercepts pose gestures.
- Opacity, scale and horizontal/vertical offset are adjustable.
- Large images are downsampled to a bounded display size before decode.
- Hide/show, reset and clear are always explicit.
- The initial 0.3 implementation is session-local by design. It does not enter portable project JSON and does not appear in exported PNGs.
- Persisting or packaging reference assets later requires a deliberate portable-asset contract rather than silently embedding device-specific URIs.

## Pose accelerators

Current: four starting presets, with Neutral also promoted to the always-visible scene Speed toolbar, plus full mirror, left/right arm copy, left/right leg copy and ground.

Next accelerators follow the observable capability gaps in `REFERENCE_BENCHMARK.md`: local pose library, pose blending, hand-shape presets, explicit foot/hand orientation and better depth manipulation.

Schema-v2 roll state remains stored for forward compatibility, but roll controls stay hidden until renderer feedback provides visible hand/foot/twist meaning. A control that changes invisible state is not considered usable functionality.

## Project safety and export

- Save/Open never requires sign-in.
- Dirty work cannot be silently replaced by New, Open or JSON Import.
- Unsaved edits are locally journaled and offered for recovery after process death.
- Explicit project and recovery writes use Android `AtomicFile`; reads participate in backup recovery rather than bypassing it.
- Project scanning, save/open/duplicate/delete and project JSON codec work stay off the Compose main thread; project-file operations are serialized so file maintenance cannot race another project operation.
- A save snapshots the project being persisted and must not overwrite newer in-memory edits that happen while disk IO is in flight.
- Deleting a saved project requires explicit confirmation and cleans atomic backup state.
- Corrupt saved files remain preserved and visible rather than silently disappearing.
- JSON is versioned/human-inspectable; entitlement changes cannot make an existing project unreadable.
- Export filenames preserve Unicode project names while removing path/control characters and truncate by Unicode code point rather than splitting surrogate pairs.
- Standard PNG keeps the workspace background/grid. Transparent PNG exports only the mannequin against alpha so artists can composite it directly in drawing software.
- A reference overlay is an editing aid, not implicit export content.

## Responsive design

Phone uses the remaining scene height above a bounded responsive inspector. Landscape/large screens keep scene and a roughly 360 dp inspector side-by-side. Horizontal tool rows scroll instead of shrinking touch targets. Inspector tabs remain horizontally scrollable so 200% font scale does not force labels into undersized targets.

Light and dark modes keep Compose surfaces and Android status/navigation bars visually aligned rather than leaving bright system chrome around a dark workspace.

## Accessibility

- Non-canvas controls use Material controls with minimum touch targets and visible labels.
- Scene exposes descriptive and selected-joint state semantics.
- The Pose inspector always exposes an explicit localized joint selector plus left/right/up/down/forward/back adjustments. A user must be able to choose and adjust a joint without touching the canvas, including after Open/Import/Recovery leaves the canvas selection empty.
- Reference controls remain ordinary labeled Material controls; the decorative reference image itself stays out of the accessibility tree.
- CI reruns primary-action reachability at 200% font scale and exercises non-canvas joint selection/adjustment.
- Before any production-v1 claim, manual TalkBack/switch/keyboard audits on the final release build remain appropriate.

## Onboarding

First run is a four-step live workflow: drag the highlighted wrist, orbit empty space, apply a preset, then export. It may be skipped and never front-loads account/store concepts.

Reference matching may become part of onboarding only after the 0.3 flow is stable enough that it improves rather than lengthens first-run completion.

## Product-development gate

The current gate is `REFERENCE_BENCHMARK.md`: close observable workflow/capability gaps while preserving local-first ownership and direct manipulation. Real-artist studies are optional during 0.3 and do not block code merge.

Human evidence remains necessary before comparative speed, preference or drawing-quality claims are published.
