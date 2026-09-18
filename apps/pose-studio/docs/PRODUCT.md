# Pose Studio product contract

## Problem

Artists often need a human reference with a specific body pose, camera angle and light direction. Existing mobile pose tools prove demand, but common friction includes slow joint manipulation, fragile saved projects, asset/paywall coupling and cluttered 3D-editor interfaces.

Pose Studio treats the phone or tablet as a **digital drawing mannequin**, not Blender-on-a-phone.

## Current implemented foundation

### Create
- Procedural articulated human mannequin with fixed bone lengths and readable capsule/torso volume cues.
- Direct joint selection and depth-aware camera-plane dragging.
- Explicit selected-joint camera-axis Depth mode for direct foreshortening changes.
- Two-bone IK for wrists and ankles.
- Bone-length-preserving manipulation for other articulated joints.
- Visible wrist/foot orientation driven by schema-v2 roll semantics, with endpoint roll controls where renderer feedback exists.
- Neutral, contrapposto, reach and run pose presets; applying Fast Pose preserves the current stylized bone lengths.
- Artistic drawing-proportion presets: Balanced, Long legs and Long torso. They retarget segment lengths while preserving the current pose direction and make no medical/anatomical accuracy claim.
- Mirror pose, copy left/right arm or leg and ground the figure.
- Undo/redo for pose edits.

### Match a reference
- Import a local image through Android's document picker.
- Render it behind the mannequin without intercepting pose gestures.
- Choosing a reference enters an explicit canvas alignment mode: one-finger drag moves the image and pinch adjusts it; leaving alignment immediately restores pose/camera gestures.
- Keep opacity, scale and horizontal/vertical sliders as precise/accessibility fallback controls.
- Keep Choose / Hide / Reset / Clear reachable in a stable 2×2 action layout, including at 200% font scale.
- Bound large image decoding before display.
- Keep the current reference overlay session-local and outside portable project JSON / exported PNG until a future portable asset contract is deliberately designed.

### Reuse poses
- Save pose-only snapshots into a local user-owned pose library.
- Apply a saved pose without replacing project identity, camera or light.
- Keep saved-pose apply inside normal Undo history.
- Delete saved poses locally without an account, marketplace or network dependency.

### Frame
- Orbit by dragging empty scene space.
- Two-finger pan/zoom.
- Yaw, pitch, distance and field-of-view controls.
- Front, side and three-quarter presets that also restore default distance, FOV and target while preserving each preset view angle.

### Light
- Directional-light azimuth/elevation/intensity.
- Depth/light-aware procedural shading and perspective floor grid.

### Hand off to drawing
- Shaded PNG export.
- Transparent PNG export.
- Silhouette PNG for negative-space/gesture checking.
- Construction PNG with mannequin masses plus construction centerlines/joints.
- Reference images never leak into exported PNGs.

### Own the work
- Atomic local project save/open.
- Debounced local recovery journal for unsaved work.
- Dirty-work confirmation before replacing a project.
- Duplicate/delete and explicit damaged-project visibility.
- Versioned JSON export/import with v1-to-v2 migration.
- Bounded/defensive project import.
- No entitlement field may make a stored project unreadable.

### Inclusive/productized shell
- Top-level **Pose / Scene / Export** workflow instead of exposing Camera/Light/Project as equal implementation categories.
- Camera and Directional Light grouped under Scene; drawing handoff leads Export.
- Independent workspace scroll identity so switching tasks opens the destination at its task top instead of inheriting a previous panel's scroll offset.
- Responsive phone/landscape/tablet inspector.
- Four-step first-run interaction guide.
- Alternate button-based joint adjustments for keyboard/switch users.
- en-US, zh-CN, zh-TW and zh-HK UI resources.
- adaptive and monochrome launcher icon.

## Explicit non-goals before evidence justifies them

- General mesh editing, sculpting, UVs, materials or animation timeline.
- Cloud sync, accounts, collaboration or social feed.
- Generative AI.
- Downloadable model marketplace.
- Photorealistic rendering.
- Medical/anatomical accuracy claims.
- Asset-count parity with general 3D pose editors.

## Primary success metric

`time_to_reference_pose`: time from opening a project or choosing a reference to reaching a usable construction reference.

The 0.3 source work closed the benchmarked single-figure capability gaps around reference matching, readable mannequin volume, hand/foot direction, direct depth manipulation, local pose reuse and drawing-oriented export. The 0.4 source work then reduced deterministic interaction cost through direct reference alignment, stable reference actions, reusable framing helpers and artistic drawing proportions. The 0.4 feature closure exact main is `aa907e76a7f39ee96fa8f4351cc181abbcb652a6`.

Multiple mannequins and basic props were reassessed against the same `time_to_reference_pose` goal and remain deferred: they add scene breadth, but there is not yet task evidence that they shorten the common single-figure reference workflow enough to justify the added interaction cost.

Real-artist studies are **not a source-control merge gate** for product development. They remain appropriate for subjective workflow evidence and any comparative speed/preference claim.

Pose Studio must not claim that it is faster or preferred versus named competitors without suitable human evidence.

## Product principles

1. Pose first: every screen should shorten posing, reference matching, framing or exporting.
2. Direct manipulation over parameter hunting.
3. Stable files over commercial lock-in.
4. Offline by default and by architecture.
5. Deterministic tools before AI.
6. Renderer/model upgrades must preserve released project semantics.
7. Compete on workflow clarity and ownership, not asset-count parity.
8. Add multi-person/props only when evidence shows scene complexity improves the drawing-reference task more than it increases interaction cost.
