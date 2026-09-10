# Pose Studio release contract

Pose Studio is independently release-bearing. It must never inherit Jingdu identity, signing, unprefixed tags or release evidence.

## Identity

- application ID: `com.junchen.posestudio`
- release namespace: `pose-studio-v<semver>`
- current development version: `0.2.0` / versionCode `2`

## Signing

A distributable build requires a Pose-Studio-specific upload/release key. Copy `android/keystore.properties.example` to an untracked `keystore.properties` and provide a keystore outside the repository. Run:

```bash
cd apps/pose-studio/android
./gradlew validatePoseReleaseSigning assembleRelease bundleRelease
```

Do not reuse another product's key and never commit passwords or production private keys.

## Required v1 evidence

A v1 release may be declared only when all of the following are bound to an exact source SHA/tag:

- unit/lint/debug/release/AAB build gate;
- API 36 instrumentation journey gate and 200% font-scale gate;
- physical compatibility/performance evidence covering the supported matrix;
- v1 schema migration fixtures, including released v1/v2 input fixtures as applicable;
- accessibility review of primary workflows;
- production/upload signing certificate and AAB checksums;
- product privacy policy/Data Safety/store assets;
- closed-track install test plus crash/ANR review;
- frozen ten-pose user benchmark showing a material time-to-pose advantage against at least two established alternatives.

A release never revokes the ability to parse a previously released user project because entitlement changes.
