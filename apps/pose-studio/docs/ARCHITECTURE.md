# Pose Studio architecture

## Purpose

Pose Studio is intentionally a focused artist-reference application, not a generic 3D editor. Architecture is optimized for three properties:

1. low-latency direct manipulation;
2. durable user-owned project files;
3. ability to replace the prototype renderer/model representation later without invalidating pose projects.

## Product boundary

All product-specific code remains in `apps/pose-studio/`. v0.1 does not depend on a shared `platform/` module. Reuse may be extracted only after another product demonstrates the same stable need.

The Android application owns:

- application ID `com.junchen.posestudio`;
- privacy contract and permissions;
- project schema;
- renderer/UI behavior;
- quality gate;
- future signing/store/release namespaces.

It does not inherit Jingdu release identity, billing, telemetry, signing or unprefixed tag names.

## Layers

### Domain

`model/` contains renderer-independent concepts:

- `Vec3`;
- `JointId`;
- `Bone`;
- `CameraState`;
- `LightState`;
- `PoseProject`;
- mannequin topology and presets.

The durable file format stores semantic joint positions, camera and light state. It deliberately does **not** store GPU buffers, mesh indices, Android view state or commercial entitlement state.

### Pose engine

`engine/` owns deterministic math:

- fixed-length articulated chains;
- two-bone endpoint IK;
- branch manipulation;
- world/screen projection.

The engine is deterministic and does not require Android framework state. Math behavior is protected by JVM unit tests.

### Persistence

`data/ProjectStore.kt` owns app-private project persistence and `ProjectCodec`.

Rules:

- every file has an explicit integer `schemaVersion`;
- decoding is defensive: missing joints fall back to known defaults;
- entitlement state is never required to decode a project;
- writes use a temporary file and replace step so a process death is less likely to leave a partially written project;
- portable JSON import creates a new local project identity rather than overwriting an existing one accidentally.

### Rendering

v0.1 uses two render targets built from the same domain state:

- Compose Canvas for interactive editing;
- Android `Bitmap`/`Canvas` for deterministic PNG export.

Both render a procedural articulated mannequin and floor grid. This avoids shipping third-party mesh assets while the time-to-pose hypothesis is still being tested.

### UI

Compose UI is split into:

- scene surface: direct joint manipulation and camera orbit;
- Pose controls: presets and history;
- Camera controls;
- Light controls;
- Project controls.

Phone layout prioritizes the scene vertically with a compact control panel; large screens use a persistent right-side inspector.

## Renderer replacement seam

A future capsule/mesh renderer must consume the same `PoseProject`/joint graph. The migration path is:

1. keep joint IDs and project-space units stable;
2. add renderer-specific skinning/mesh data as bundled application assets, not project requirements;
3. derive rig transforms from stored joint positions;
4. preserve procedural rendering as a compatibility/fallback path until older project fixtures pass migration tests;
5. only add new schema fields when the user-authored semantic state changes.

A renderer upgrade must never require rewriting a project merely because mesh assets changed.

## Project schema policy

Schema v1 contains:

- `schemaVersion`;
- project `id`, `name`, `modifiedAt`;
- camera yaw/pitch/distance/FOV;
- light azimuth/elevation/intensity;
- named joint XYZ positions.

Future migration requirements:

- loaders support all released schema versions or provide an explicit deterministic migration;
- unknown additive fields are ignored;
- missing optional fields use defaults;
- a schema migration is covered by fixture tests before release;
- no release may intentionally make an existing locally saved project unreadable because the user is no longer entitled to a model/asset.

## Offline architecture

The manifest has no `INTERNET` or `ACCESS_NETWORK_STATE` permission. Import/export use the Storage Access Framework. Local project save uses app-private files. The application contains no advertising, analytics or account SDK in v0.1.

If an online feature is ever proposed it must be additive and optional. The offline create/edit/save/export loop remains independently functional.

## Performance model

Pointer moves must update only in-memory pose/camera state. File serialization, bitmap export and other allocation-heavy work stay outside pointer move paths.

The scene gesture coroutine uses current state through `rememberUpdatedState`; pose updates therefore do not restart the active drag detector on every frame.

Before release, performance instrumentation should measure:

- frame duration during joint drag/orbit;
- gesture-to-visual latency;
- PNG export latency/memory;
- project open/save latency;
- cold-start-to-interactive latency.

The target is 60 Hz interaction on a representative mid-range device. This is a product target until a reproducible device/benchmark baseline is checked in.