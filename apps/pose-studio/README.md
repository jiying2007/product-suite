# Pose Studio

Pose Studio is an offline-first Android pose and drawing-reference workspace for illustrators, comic artists, storyboard artists and other visual creators.

The product goal is not to become a general-purpose 3D modeller. It optimizes one task: **reach a useful drawing reference quickly, then keep the owned project usable without an account or commercial lock-in**.

## Current product state

The frozen distributed line is `0.2.1` commercial beta. Current source development is a 0.3 product-value track, not a v1 production push.

Implemented foundations include direct joint manipulation with IK, symmetry/copy/ground/Neutral speed tools, depth-aware camera-plane dragging, two-finger camera zoom/pan, responsive phone/tablet layout, non-canvas joint selection plus directional adjustments, asynchronous project I/O and autosave recovery, versioned schema migration, portable JSON/PNG export, localized UI resources and executable Android instrumentation gates.

The 0.3 track adds drawing-reference workflow depth before further productionization. The first increment is a local reference-image overlay behind the mannequin with opacity, scale and alignment controls while keeping pose gestures authoritative.

## Product promises

- Core creation works with no account and no network connection.
- The Android manifest does not request `INTERNET` or `ACCESS_NETWORK_STATE`.
- Saved projects remain readable regardless of future commercial packaging.
- Project JSON and rendered PNG can be exported through Android's Storage Access Framework.
- Reference images remain local editing aids and are not silently embedded into portable project/export formats.
- Product development is guided by observable competitor/task evidence in `docs/REFERENCE_BENCHMARK.md`; real-artist recruitment is not a source-control gate for 0.3.
- Comparative speed/preference claims still require appropriate human evidence.
- The primary UX metric is time-to-reference-pose, not model/asset count.

## Android

```bash
cd apps/pose-studio/android
./gradlew --no-daemon --warning-mode all poseStudioCheck
```

See `docs/PRODUCT.md`, `docs/REFERENCE_BENCHMARK.md`, `docs/ARCHITECTURE.md`, `docs/UX.md`, `docs/QUALITY.md`, `docs/PERFORMANCE.md`, `docs/COMMERCIAL_READINESS.md`, `docs/PRODUCTION_QUALIFICATION.md`, `docs/PRIVACY_POLICY.md` and `docs/RELEASE.md` for product and release contracts.
