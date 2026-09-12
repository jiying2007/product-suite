# Pose Studio

Pose Studio is an offline-first Android pose and drawing-reference workspace for illustrators, comic artists, storyboard artists and other visual creators.

The product goal is not to become a general-purpose 3D modeller. It optimizes one task: **reach a useful drawing reference pose quickly, then keep that project usable forever**.

## Current product state

The current development line is `0.2.1`. It is a commercial-beta candidate, not a v1 production claim.

Implemented product foundations include direct joint manipulation with IK, symmetry/copy/ground/Neutral speed tools, depth-aware camera-plane dragging, two-finger camera zoom/pan, responsive phone/tablet layout, an explicit non-canvas joint selector plus directional adjustments, asynchronous project I/O and autosave recovery, versioned schema migration, portable JSON/PNG export, localized UI resources and executable Android instrumentation gates.

## Product promises

- Core creation works with no account and no network connection.
- The Android manifest does not request `INTERNET` or `ACCESS_NETWORK_STATE`.
- Saved projects remain readable regardless of future commercial packaging.
- Project JSON and rendered PNG can be exported through Android's Storage Access Framework.
- The renderer remains procedural and asset-free while the time-to-pose hypothesis is being validated.
- The primary UX metric is time-to-pose, not model/asset count.

## Android

```bash
cd apps/pose-studio/android
./gradlew --no-daemon --warning-mode all poseStudioCheck
```

See `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/UX.md`, `docs/QUALITY.md`, `docs/PERFORMANCE.md`, `docs/COMMERCIAL_READINESS.md`, `docs/PRODUCTION_QUALIFICATION.md`, `docs/PRIVACY_POLICY.md` and `docs/RELEASE.md` for product and release contracts.
