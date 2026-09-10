# Pose Studio quality contract

## Build gate

`poseStudioCheck` must run JVM unit tests, debug/release lint, assemble debug and release APKs, build the release AAB and compile instrumentation tests.

Dedicated Pose Studio CI additionally boots API 36, runs the full instrumentation suite, then reruns primary-action accessibility reachability at 200% font scale. Build and instrumentation artifacts are preserved per exact SHA.

## Domain/data invariants

- Pose transforms never produce NaN/Infinity.
- Endpoint IK preserves configured bone lengths within tolerance.
- Non-root direct manipulation preserves parent bone length.
- Mirror/copy/ground operations preserve expected geometry.
- One gesture is one undo snapshot.
- Project schema is explicit and v1 migrates deterministically to v2.
- Missing/invalid joints fall back safely; future unsupported schemas fail explicitly.
- Project import is bounded in size and rejects/sanitizes pathological numeric input.
- Explicit save is atomic; unsaved changes are recoverable through a debounced local journal.

## Privacy gate

The main manifest must not request `android.permission.INTERNET` or `ACCESS_NETWORK_STATE`. No account/ads/analytics SDK may become required for the core loop.

## UX acceptance

- Direct joint selection/drag and empty-space orbit work without mode switching.
- Pinch zoom is available.
- Mirror/copy/ground and presets are one-step accelerators.
- Unsaved New/Open cannot silently destroy work.
- Save/open/import/export remain account-free.
- phone/landscape/tablet layout keeps the scene dominant.
- primary actions remain reachable at 200% font scale.
- alternate precise joint controls exist for users unable to operate the canvas directly.

## Performance

API-36 instrumentation owns broad regression budgets for render-model, project codec and bitmap rendering. These do not replace physical release qualification. Physical release SLOs and evidence requirements are defined in `PERFORMANCE.md`.
