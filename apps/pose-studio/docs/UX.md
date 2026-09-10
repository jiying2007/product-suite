# Pose Studio UX contract

## North-star interaction

The app should feel like picking up a digital drawing mannequin. The artist should spend time **posing**, not navigating property panels. Primary metric: `time_to_reference_pose`.

## Workspace hierarchy

1. Scene — dominant visual surface.
2. Integrated Speed tools — mirror/copy/ground without leaving the scene.
3. Pose — presets, undo/redo and precise accessible adjustments.
4. Camera — composition controls.
5. Light — form-clarity controls.
6. Project — storage, recovery and export.

Project name and unsaved state remain visible in the top bar.

## Direct manipulation

- Pointer down near a visible joint selects the nearest joint within a density-independent 48 dp hit radius.
- Wrists/ankles use two-bone IK.
- Other joints preserve their parent bone length and move descendants coherently.
- Pelvis translates the full mannequin.
- Empty-scene drag orbits the camera.
- Two-finger gestures pan the persistent camera target and pinch zoom without moving the mannequin itself.
- Screen-to-world drag respects camera pitch/FOV and selected-joint depth.
- One continuous pose gesture creates one undo snapshot.

## Pose accelerators

Current: four starting presets, full mirror, left/right arm copy, left/right leg copy and ground. These are evaluated by whether they lower time-to-pose; asset count is not a success metric.

Next accelerators require benchmark/user evidence: local pose library, pose blending, hand-shape presets, explicit foot/hand orientation and balance assistance.

Schema-v2 roll state remains stored for forward compatibility, but roll controls are intentionally not exposed until the renderer can provide visible hand/foot/twist feedback. A control that changes invisible state is not considered usable functionality.

## Project safety

- Save/Open never requires sign-in.
- Dirty work cannot be silently replaced by New, Open or JSON Import.
- Unsaved edits are locally journaled and offered for recovery after process death.
- Explicit project and recovery writes use Android `AtomicFile`; reads participate in backup recovery rather than bypassing it.
- Deleting a saved project requires explicit confirmation and cleans atomic backup state.
- Corrupt saved files remain preserved and visible rather than silently disappearing.
- JSON is versioned/human-inspectable; entitlement changes cannot make an existing project unreadable.

## Responsive design

Phone uses the remaining scene height above a bounded responsive inspector. Landscape/large screens keep scene and a roughly 360 dp inspector side-by-side. Horizontal tool rows scroll instead of shrinking touch targets.

## Accessibility

- Non-canvas controls use Material controls with minimum touch targets and visible labels.
- Scene exposes descriptive and selected-joint state semantics.
- The Pose inspector provides explicit left/right/up/down/forward/back controls as an alternative to gesture-only manipulation.
- CI reruns primary-action reachability at 200% font scale.
- Before v1, manual TalkBack/switch/keyboard audits on the final release build remain mandatory.

## Onboarding

First run is a four-step live workflow: drag the highlighted wrist, orbit empty space, apply a preset, then export. It may be skipped and never front-loads account/store concepts.

## Kill test

Major model/asset investment is blocked until the frozen ten-pose benchmark shows a material median time advantage versus at least two established Android alternatives and artists understand direct manipulation without a long tutorial.
