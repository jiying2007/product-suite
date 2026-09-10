# Pose Studio architecture

## Purpose

Pose Studio is a focused artist-reference application optimized for low-latency direct manipulation, durable user-owned files and replaceable rendering without invalidating projects.

## Product boundary

All product-specific code remains under `apps/pose-studio/`. The Android application owns `com.junchen.posestudio`, permissions/privacy, project schema, renderer/UI, quality gates and future signing/store/release namespaces. It does not inherit Jingdu identity, billing, telemetry or signing.

## Layers

### Domain

`model/` contains renderer-independent state: `Vec3`, `JointId`, `Bone`, `CameraState`, `LightState`, `PoseProject` and mannequin topology/presets.

Schema v2 stores semantic joint positions plus per-joint roll state, camera and light. It deliberately excludes GPU buffers, mesh indices, Android view state and entitlement state. Schema-v1 files migrate deterministically by assigning zero roll to joints.

### Pose engine

`engine/` owns deterministic fixed-length chains, two-bone endpoint IK, branch manipulation and screen/world projection. Direct manipulation uses camera pitch/yaw, FOV and selected-joint depth so a pixel drag maps to a camera-plane world delta at the manipulated joint rather than a global fixed scale.

### Persistence and recovery

`data/ProjectStore.kt` owns explicit atomic saves, local recovery snapshots, duplicate/delete, corruption discovery and `ProjectCodec`.

Rules:
- every portable file has an explicit schema version;
- released schemas are migrated or rejected explicitly, never guessed;
- missing/invalid joint coordinates fall back to known safe defaults;
- extreme/non-finite values and oversized project text are rejected/sanitized;
- explicit saves use a temporary file and replace step;
- unsaved edits are journaled with debounce off the pointer path;
- entitlement state is never required to decode a project.

### Rendering

`render/PoseRenderModel.kt` is the shared render-description seam. Both interactive Compose Canvas and deterministic Android Bitmap export consume the same projected scene geometry, volume cues, depth order and lighting values. Platform-specific drawing APIs remain thin adapters.

The current renderer stays procedural and asset-free while time-to-pose is tested. A future skinned/capsule renderer must consume the same released semantic state and preserve old fixtures.

### UI

Compose UI is split into scene surface, integrated speed toolbar and Pose/Camera/Light/Project inspectors. Phone inspector height is responsive; landscape/tablet uses a persistent side inspector. Primary strings are Android resources for en-US/zh-CN/zh-TW/zh-HK.

The scene exposes accessibility semantics; non-visual/keyboard/switch users can select a joint and apply explicit directional/roll adjustments from the Pose inspector rather than relying exclusively on canvas gestures.

## Offline architecture

The manifest has no `INTERNET` or `ACCESS_NETWORK_STATE`. Import/export use Storage Access Framework. Local projects/recovery use app-private files. There is no advertising, analytics or account SDK.

Future online features must be additive and optional; offline create/edit/save/export remains independently functional.

## Performance model

Pointer movement updates only in-memory pose/camera state. Recovery is debounced onto `Dispatchers.IO`; JSON import/export IO and bitmap render/compression are outside the UI pointer path. CI executes broad API-36 device-emulator regression budgets, while commercial release qualification requires the physical workflow and recorded device/source provenance in `PERFORMANCE.md`.
