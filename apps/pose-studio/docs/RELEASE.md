# Pose Studio release contract

Pose Studio is independently release-bearing. It must never inherit Jingdu identity, signing, unprefixed tags or release evidence.

## Identity

- application ID: `com.junchen.posestudio`
- release namespace: `pose-studio-v<semver>`
- current development version: `0.2.0` / versionCode `2`

## Signing

A **production distributable build** requires a Pose-Studio-specific upload/release key. Do not reuse another product's key and never commit passwords or production private keys.

The public `0.2.x` commercial-beta channel is an explicit pre-production exception: a beta APK may use the repository-stable generic Android debug identity only when the release is marked `prerelease=true`, never latest, publishes the exact source SHA/APK checksum/signing-certificate checksum, and explicitly states that the artifact is not production signing or Google Play evidence. That exception must never be promoted into a production AAB or used to satisfy issue #83 signing rows.

For local production qualification, copy `android/keystore.properties.example` to the untracked `android/keystore.properties`, point `storeFile` at a keystore outside the repository and run:

```bash
cd apps/pose-studio/android
./gradlew validatePoseReleaseSigning assembleRelease bundleRelease
```

The self-hosted physical qualification workflow does **not** expect an untracked keystore to survive checkout. Configure these repository/environment secrets instead:

- `POSE_STUDIO_RELEASE_KEYSTORE_BASE64` — base64 encoding of the product-specific keystore bytes;
- `POSE_STUDIO_RELEASE_STORE_PASSWORD`;
- `POSE_STUDIO_RELEASE_KEY_ALIAS`;
- `POSE_STUDIO_RELEASE_KEY_PASSWORD`.

The workflow decodes the keystore only into `$RUNNER_TEMP`, exposes its path to Gradle through `POSE_STUDIO_RELEASE_STORE_FILE`, keeps passwords in step-level environment variables, and removes the temporary keystore on shell exit. Missing signing secrets fail closed. Merely wiring this path is not evidence that production signing has been provisioned; #83 remains open until the exact candidate's certificate/AAB provenance is captured.

Gradle accepts either the local untracked properties file or the corresponding `POSE_STUDIO_RELEASE_*` environment variables. Normal unsigned CI builds continue to compile release artifacts for static verification, while `validatePoseReleaseSigning` is mandatory for production/physical qualification.

## Candidate immutability

Once a `pose-studio-v<semver>` tag/release exists, that semver is frozen. Later `main` commits may temporarily retain the same development version, but the candidate workflow must no-op rather than moving the tag or reporting a false release failure. A new release candidate requires incrementing `versionName`/`versionCode` and adding its matching release manifest first.

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

See `PRODUCTION_QUALIFICATION.md` for the execution/evidence runbook. Production status remains fail-closed until every external row has real evidence.
