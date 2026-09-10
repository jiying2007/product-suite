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
- Camera pan target is portable, bounded and backward-compatible with files that omit it.
- Dirty New/Open/Import cannot silently replace unsaved work.
- Explicit save and recovery use Android `AtomicFile`; committed backups must remain recoverable after an interrupted replacement.
- Delete removes the committed project plus atomic backup only after explicit user confirmation.
- High-frequency edits use one conflated/debounced recovery worker rather than creating file IO or one coroutine per pointer update.

## Privacy gate

The main manifest must not request `android.permission.INTERNET` or `ACCESS_NETWORK_STATE`. No account/ads/analytics SDK may become required for the core loop. A visible in-app privacy-policy entry must remain available before Play production submission.

## UX acceptance

- Direct joint selection/drag and empty-space orbit work without mode switching.
- Pinch zoom and true two-finger camera-target pan are available.
- Mirror/copy/ground and presets are one-step accelerators.
- Unsaved New/Open/Import cannot silently destroy work.
- Destructive saved-project deletion requires confirmation.
- Save/open/import/export remain account-free.
- phone/landscape/tablet layout keeps the scene dominant.
- primary actions remain reachable at 200% font scale.
- alternate precise directional joint controls exist for users unable to operate the canvas directly.
- controls for state that the current renderer cannot visibly express, including joint roll, remain hidden until they become meaningful.

## Performance

API-36 instrumentation owns broad regression budgets for render-model, project codec and bitmap rendering. These do not replace physical release qualification. Physical release SLOs and evidence requirements are defined in `PERFORMANCE.md`.
