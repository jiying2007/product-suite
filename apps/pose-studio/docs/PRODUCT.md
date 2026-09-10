# Pose Studio product contract

## Problem

Artists often need a human reference with a specific body pose, camera angle and light direction. Existing mobile pose tools prove demand, but common friction includes slow joint manipulation, fragile saved projects, asset/paywall coupling and cluttered 3D-editor interfaces.

Pose Studio treats the phone or tablet as a **digital drawing mannequin**, not Blender-on-a-phone.

## Current 0.2 scope

### Create
- Procedural articulated human mannequin with fixed bone lengths and volume cues.
- Direct joint selection and depth-aware camera-plane dragging.
- Two-bone IK for wrists and ankles.
- Bone-length-preserving manipulation for other articulated joints.
- Neutral, contrapposto, reach and run presets.
- Mirror pose, copy left/right arm or leg and ground the figure.
- Undo/redo for pose edits.
- Schema-v2 joint-roll semantics reserved for hand/foot/twist-aware rendering without invalidating v1 imports.

### Frame
- Orbit by dragging empty scene space.
- Two-finger pan/zoom.
- Yaw, pitch, distance and field-of-view controls.
- Front, side and three-quarter presets.

### Light
- Directional-light azimuth/elevation/intensity.
- Depth/light-aware mannequin shading and perspective floor grid.

### Own the work
- Atomic local project save/open.
- Debounced local recovery journal for unsaved work.
- Dirty-work confirmation before replacing a project.
- Duplicate/delete and explicit damaged-project visibility.
- Versioned JSON export/import with v1-to-v2 migration.
- Bounded/defensive project import.
- PNG export through Android Storage Access Framework.
- No entitlement field may make a stored project unreadable.

### Inclusive/productized shell
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

## Primary success metric

`time_to_reference_pose`: time from opening a project to reaching a usable target pose.

The product may not claim a speed advantage until the frozen ten-pose protocol in `BENCHMARK_PROTOCOL.md` is run with real artists against at least two established Android alternatives. A material median advantage remains the gate for major renderer/model investment.

## Product principles

1. Pose first: every screen should shorten posing, framing or exporting.
2. Direct manipulation over parameter hunting.
3. Stable files over commercial lock-in.
4. Offline by default and by architecture.
5. Deterministic tools before AI.
6. Renderer/model upgrades must preserve released project semantics.
