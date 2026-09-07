# Pose Studio

Pose Studio is an offline-first Android 3D pose and reference workspace for illustrators, comic artists, storyboard artists and other visual creators.

The product goal is not to become a general-purpose 3D modeller. It optimizes one task: **reach a useful drawing reference pose quickly, then keep that project usable forever**.

## Product promises

- Core creation works with no account and no network connection.
- The Android manifest does not request `INTERNET`.
- Saved projects remain readable regardless of future commercial packaging.
- Project JSON and rendered PNG can be exported through Android's Storage Access Framework.
- The first renderer is procedural and asset-free: a 3D articulated mannequin, camera, light and floor grid.
- The primary UX metric is time-to-pose, not model/asset count.

## Android

The standalone Android project lives under `android/` and uses its own application ID: `com.junchen.posestudio`.

```bash
cd apps/pose-studio/android
./gradlew --no-daemon --warning-mode all poseStudioCheck
```

See `docs/PRODUCT.md`, `docs/PRIVACY.md` and `docs/QUALITY.md` for the product contract.