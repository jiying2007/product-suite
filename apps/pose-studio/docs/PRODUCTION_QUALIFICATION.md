# Pose Studio production qualification runbook

This runbook turns issue #83 into an execution sequence. It does not let source control self-certify evidence that only a physical device, real artist, Play Console or production signing system can produce.

## 1. Freeze a new immutable candidate

1. Increment `versionName` and `versionCode` before attempting a candidate newer than an existing `pose-studio-v<semver>` tag.
2. Add/update `apps/pose-studio/releases/pose-studio-v<semver>.md` with the non-production flags still false until external qualification is complete.
3. Merge only after Pose Studio, CI Contracts and canonical CI are terminal green on exact main.
4. Let `Pose Studio Candidate Release` freeze the exact SHA. Never move an existing Pose Studio release tag.
5. Record the tag, source SHA and release-manifest SHA-256 in issue #83.

## 2. Provision product-specific production signing

Provision a Pose-Studio-only upload/release key outside the repository. Configure the self-hosted release environment with:

- `POSE_STUDIO_RELEASE_KEYSTORE_BASE64`
- `POSE_STUDIO_RELEASE_STORE_PASSWORD`
- `POSE_STUDIO_RELEASE_KEY_ALIAS`
- `POSE_STUDIO_RELEASE_KEY_PASSWORD`

Run `validatePoseReleaseSigning` only against the immutable candidate. Preserve the production AAB SHA-256 and signer certificate SHA-256. A commercial-beta generic debug certificate is never valid production evidence.

## 3. Execute the physical Android matrix

Run `.github/workflows/pose-studio-physical-release.yml` with the immutable candidate ref. Evidence must include source SHA, manufacturer, model, API, build fingerprint and refresh rate.

Minimum matrix before v1:

| Slot | Required evidence |
| --- | --- |
| Legacy | Physical API 26 device |
| Current | Physical API 36 device |
| OEM diversity | At least two OEM families across the matrix |
| Form factors | Portrait phone, landscape phone, representative tablet/large screen |

For each applicable device preserve:

- cold startup P95 < 1.0 s;
- direct-manipulation frame CPU P95 < 16.7 ms and P99 < 33.4 ms;
- save/open P95 < 100 ms for normal procedural projects;
- 1440 px PNG render+write P95 < 1.0 s on a representative mid-range device;
- repeated edit/save/import/export without OOM, ANR or project corruption;
- process-death recovery and low-storage/write-failure behavior without losing the last valid committed project.

Do not substitute emulator timings for this section.

## 4. Manual accessibility/device UX audit

Use the final release candidate, not a development mock. Repeat the primary create/save/open/export journey with:

- 200% font scale;
- TalkBack;
- keyboard/D-pad navigation where supported;
- switch-access style focus/activation;
- en-US, zh-CN, zh-TW and zh-HK;
- an unsupported locale to confirm English fallback;
- phone portrait, phone landscape and tablet/large-screen layout.

The Pose inspector must allow a user to select a joint and make left/right/up/down/forward/back adjustments without touching the canvas. Record any focus traps, unlabeled controls, clipped text or unreachable destructive confirmations as release blockers.

## 5. Frozen real-artist benchmark

Follow `BENCHMARK_PROTOCOL.md` without changing targets/exclusions after results are visible.

1. Freeze ten public-domain/original target references before timing starts.
2. Recruit at least 8 practicing illustrators/comic/storyboard artists; 12+ is preferred before a marketing speed claim.
3. Compare the exact Pose Studio candidate against at least two established Android pose/reference tools.
4. Record one row per participant/tool/task using `BENCHMARK_RESULTS_TEMPLATE.csv`.
5. Validate and summarize the raw data:

```bash
python3 apps/pose-studio/scripts/analyze-artist-benchmark.py path/to/results.csv
```

The analyzer checks study completeness and reports median/P95, abandonment, confidence, corrections, undo/mis-selection rates and paired participant-level differences. It intentionally does not emit an automatic marketing PASS. A speed claim requires a material and consistent advantage without materially worse confidence/abandonment.

## 6. Privacy and Play policy

Before production submission, complete the exact production AAB review and every applicable Play Console **App content** item:

- publish the final privacy policy at the stable public HTTPS URL in `apps/pose-studio/store/play/PRIVACY_POLICY_URL.txt`; it must not depend on mutable `blob/main` content, and the in-app privacy action must match it;
- inspect the exact production AAB permissions, merged manifest and dependency graph, then submit matching **Data Safety** answers from `apps/pose-studio/store/play/DATA_SAFETY.md`;
- **Ads:** declare No while the exact production build contains no advertising SDK or ad placement;
- **App access:** Pose Studio has no account/sign-in/reviewer credential gate; declare unrestricted access unless that architecture changes;
- **Target audience and content:** select only the age groups the product is actually intended for. Do not include children solely to broaden distribution; if children are intentionally included, complete the applicable Families-policy review before release;
- **Content rating:** complete and retain the Play rating questionnaire/result; an unrated production listing is release-blocking;
- complete developer contact and any other item surfaced by Play Console under **Needs attention** before review;
- capture store screenshots/feature graphic from the exact qualified build for en-US, zh-CN, zh-TW and zh-HK. Do not substitute mockups that show behavior not present in the submitted build.

Pose Studio currently declares no foreground service and no sensitive/runtime permission. If the exact merged production manifest changes that fact, re-run policy review and update this runbook/Data Safety before upload rather than copying the prior declaration.

## 7. Closed track and rollout

Upload the exact qualified AAB to internal/closed testing and verify:

- clean install;
- update from the prior supported build;
- reinstall behavior;
- opening/migrating existing local Pose Studio projects;
- save/open/import/export on the Play-installed build;
- crash/ANR/vitals review.

Define rollback/version handling before production. Use staged rollout and record every expansion against the exact versionCode, source SHA, AAB checksum and signing certificate.

## 8. Closure rule

Issue #83 is the authority for v1 commercial-production qualification. Check a row only when its real evidence exists and is bound to the exact candidate. Repository CI, documentation, emulator runs or a commercial-beta APK cannot close physical-device, production-signing, real-artist, Play Console, manual-accessibility or staged-rollout rows on their own.
