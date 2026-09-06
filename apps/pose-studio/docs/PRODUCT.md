# Pose Studio product contract

## Problem

Artists often need a human reference with a specific body pose, camera angle and light direction. Existing mobile pose tools prove large demand, but common friction includes slow joint manipulation, fragile saved projects, asset/paywall coupling and cluttered 3D-editor interfaces.

Pose Studio treats the phone as a **digital drawing mannequin**, not as Blender-on-a-phone.

## v0.1 scope

### Create
- Procedural articulated human mannequin with fixed bone lengths.
- Direct joint selection and drag.
- Two-bone IK for wrists and ankles.
- Bone-length-preserving rotation for other articulated joints.
- Neutral, contrapposto, reach and run presets.
- Undo/redo for pose edits.

### Frame
- Orbit camera by dragging empty scene space.
- Yaw, pitch, distance and field-of-view controls.
- Front, side and three-quarter camera presets.

### Light
- Directional light azimuth/elevation/intensity.
- Depth/light-aware mannequin shading.
- Perspective floor grid for spatial reference.

### Own the work
- Local project save/open.
- Versioned JSON export/import.
- PNG reference export through the Storage Access Framework.
- No entitlement field may make a stored project unreadable.

## Explicit non-goals for v0.1

- General mesh editing, sculpting, UVs, materials or animation timeline.
- Cloud sync, accounts, collaboration or social feed.
- Generative AI.
- Downloadable model marketplace.
- Photorealistic rendering.
- Medical/anatomical accuracy claims.

## Primary success metric

`time_to_reference_pose`: time from opening a project to reaching a usable target pose.

Prototype kill test: on a fixed set of ten reference poses, median time should be materially faster than established Android pose tools. A target of roughly 40–50% faster is the threshold for expanding the renderer/model investment.

## Product principles

1. Pose first: every screen should shorten posing, framing or exporting.
2. Direct manipulation over parameter hunting.
3. Stable files over commercial lock-in.
4. Offline by default and by architecture.
5. Deterministic tools before AI.
6. Small, reversible renderer architecture: the procedural renderer can later be replaced by a mesh renderer without changing the project/pose domain model.