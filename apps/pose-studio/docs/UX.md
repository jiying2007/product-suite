# Pose Studio UX contract

## North-star interaction

The app should feel like picking up a digital drawing mannequin. The artist should spend time **posing**, not navigating property panels. Primary metric: `time_to_reference_pose`.

## Workspace hierarchy

1. Scene — dominant visual surface.
2. Integrated Speed tools — Neutral reset, mirror/copy/ground without leaving the scene.
3. Pose — presets, undo/redo and precise accessible adjustments.
4. Camera — composition controls.
5. Light — form-clarity controls.
6. Project — storage, recovery and export.

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

## Pose accelerators

Current: four starting presets, with Neutral also promoted to the always-visible scene Speed toolbar, plus full mirror, left/right arm copy, left/right leg copy and ground. These are evaluated by whether they lower time-to-pose; asset count is not a success metric.

Next accelerators require benchmark/user evidence: local pose library, pose blending, hand-shape presets, explicit foot/hand orientation and balance assistance.

Schema-v2 roll state remains stored for forward compatibility, but roll controls are intentionally not exposed until the renderer can provide visible hand/foot/twist feedback. A control that changes invisible state is not considered usable functionality.

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

## Responsive design

Phone uses the remaining scene height above a bounded responsive inspector. Landscape/large screens keep scene and a roughly 360 dp inspector side-by-side. Horizontal tool rows scroll instead of shrinking touch targets. Inspector tabs remain horizontally scrollable so 200% font scale does not force labels into undersized targets.

Light and dark modes keep Compose surfaces and Android status/navigation bars visually aligned rather than leaving bright system chrome around a dark workspace.

## Accessibility

- Non-canvas controls use Material controls with minimum touch targets and visible labels.
- Scene exposes descriptive and selected-joint state semantics.
- The Pose inspector always exposes an explicit localized joint selector plus left/right/up/down/forward/back adjustments. A user must be able to choose and adjust a joint without touching the canvas, including after Open/Import/Recovery leaves the canvas selection empty.
- CI reruns primary-action reachability at 200% font scale and exercises non-canvas joint selection/adjustment.
- Before v1, manual TalkBack/switch/keyboard audits on the final release build remain mandatory.

## Onboarding

First run is a four-step live workflow: drag the highlighted wrist, orbit empty space, apply a preset, then export. It may be skipped and never front-loads account/store concepts.

## Kill test

Major model/asset investment is blocked until the frozen ten-pose benchmark shows a material median time advantage versus at least two established Android alternatives and artists understand direct manipulation without a long tutorial.
