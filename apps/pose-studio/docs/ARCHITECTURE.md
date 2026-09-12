# Pose Studio architecture

## Purpose

Pose Studio is a focused artist-reference application optimized for low-latency direct manipulation, durable user-owned files and replaceable rendering without invalidating projects.

## Product boundary

All product-specific code remains under `apps/pose-studio/`. The Android application owns `com.junchen.posestudio`, permissions/privacy, project schema, renderer/UI, quality gates and future signing/store/release namespaces. It does not inherit Jingdu identity, billing, telemetry or signing.

## Layers

### Domain

`model/` contains renderer-independent state: `Vec3`, `JointId`, `Bone`, `CameraState`, `LightState`, `PoseProject` and mannequin topology/presets.

Schema v2 stores semantic joint positions plus per-joint roll state, camera and light. Camera composition includes a persistent target vector used by two-finger pan; older v1/v2 files without that optional field decode to the origin. The schema deliberately excludes GPU buffers, mesh indices, Android view state and entitlement state. Schema-v1 files migrate deterministically by assigning zero roll to joints.

### Pose engine

`engine/` owns deterministic fixed-length chains, two-bone endpoint IK, branch manipulation and screen/world projection. Direct manipulation uses camera pitch/yaw, FOV and selected-joint depth so a pixel drag maps to a camera-plane world delta at the manipulated joint rather than a global fixed scale. Camera pan uses the same camera-plane transform to move only the composition target, not mannequin geometry.

### Persistence and recovery

`data/ProjectStore.kt` owns explicit atomic saves, local recovery snapshots, duplicate/delete, corruption discovery and `ProjectCodec`.

Rules:
- every portable file has an explicit schema version;
- released schemas are migrated or rejected explicitly, never guessed;
- missing/invalid joint coordinates fall back to known safe defaults;
- extreme/non-finite values and oversized project text are rejected/sanitized;
- explicit and recovery writes use Android `AtomicFile`, and reads participate in its backup recovery path;
- atomic delete removes both committed and backup state;
- unsaved edits are coalesced by one conflated recovery worker instead of creating/cancelling a coroutine on every pointer update;
- entitlement state is never required to decode a project.

`PoseStudioViewModel` is the UI orchestration boundary for project operations. Startup scan/recovery discovery and explicit save/open/duplicate/delete operations are dispatched off the main thread and serialized by one project-IO mutex. Project JSON encode/decode is also performed away from the Compose main thread. Save captures the project snapshot being persisted; if the user edits while that IO is in flight, the completed save updates the saved-project index without replacing the newer in-memory project and a new recovery snapshot remains scheduled.

### Rendering

`render/PoseRenderModel.kt` is the shared render-description seam. Both interactive Compose Canvas and deterministic Android Bitmap export consume the same projected scene geometry, volume cues, depth order and lighting values. Platform-specific drawing APIs remain thin adapters.

The current renderer stays procedural and asset-free while time-to-pose is tested. A future skinned/capsule renderer must consume the same released semantic state and preserve old fixtures. Joint-roll data remains stored but is not exposed as an interactive control until the renderer can make that state visibly meaningful.

### UI

Compose UI is split into scene/orchestration plus inspector panels. Phone inspector height is responsive; landscape/tablet uses a persistent side inspector. Primary strings are Android resources for en-US/zh-CN/zh-TW/zh-HK.

The scene exposes accessibility semantics. The Pose inspector also exposes a localized, non-canvas joint selector and explicit directional adjustments so TalkBack/keyboard/switch users do not need to touch the Canvas to choose or adjust a joint. New/Open/Import share dirty-work protection, project deletion requires confirmation, and all visible Save entry points share the same failure handling.

## Offline architecture

The manifest has no `INTERNET` or `ACCESS_NETWORK_STATE`. Import/export use Storage Access Framework. Local projects/recovery use app-private files. There is no advertising, analytics or account SDK. The in-app privacy-policy action delegates to a user-selected browser via `ACTION_VIEW`; the app itself still requires no network permission.

Future online features must be additive and optional; offline create/edit/save/export remains independently functional.

## Performance model

Pointer movement updates in-memory pose/camera state and submits a conflated recovery request; no file write or serialization executes on the pointer path. Recovery is debounced on one `Dispatchers.IO` worker; explicit project-file operations are serialized off-main; JSON import/export IO and bitmap render/compression are outside the UI pointer path. CI executes broad API-36 device-emulator regression budgets, while commercial release qualification still requires the physical workflow and recorded device/source provenance in `PERFORMANCE.md`.
