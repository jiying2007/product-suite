# Pose Studio commercial-readiness checklist

Repository gates are fail-closed: code may land before external qualification, but v1/commercial-production status is not granted by documentation alone.

Current direction: **production-v1 work is secondary to the 0.3 product-value track**. Product-development decisions use `REFERENCE_BENCHMARK.md`; recruiting external artists is not required to continue 0.3 implementation.

## Repository-side implemented

- offline/no-account core and no INTERNET permission;
- schema-v2 portable project format with v1 migration, persistent camera pan target and defensive import limits;
- rollback-safe Android `AtomicFile` project/recovery writes and backup-aware reads/deletes;
- conflated/debounced autosave recovery off the pointer path;
- project scanning/save/open/duplicate/delete plus JSON codec work off the Compose main thread, with serialized project-file operations and save-in-flight edit protection;
- dirty-work confirmation for New/Open/Import, duplicate/delete management and corrupt-project visibility;
- explicit delete confirmation for saved user projects;
- direct IK, symmetry/copy/ground tools, depth-aware drag and true two-finger camera-target pan plus pinch zoom;
- shared scene render model used by interactive and bitmap renderers;
- local reference-image overlay with bounded decode, opacity/scale/offset controls and pose-gesture pass-through;
- responsive phone/landscape/tablet workspace with scrollable inspector tabs for large-font layouts;
- onboarding plus an always-available localized non-canvas joint selector and directional adjustment controls;
- invisible joint-roll editing is withheld until renderer feedback exists;
- en-US, zh-CN, zh-TW and zh-HK UI resources;
- adaptive/monochrome launcher icon;
- visible in-app privacy-policy entry while the offline app remains free of network permission;
- release APK/AAB compilation in the normal Pose gate;
- API 36 instrumentation, non-canvas joint-selection coverage and 200% font-scale execution in CI;
- physical-device workflow that records immutable device/source provenance;
- optional real-artist benchmark schema/analyzer retained for later comparative claims;
- production qualification runbook that keeps signing/device/Play/accessibility evidence external and auditable.

## Product-value evidence for 0.3

`REFERENCE_BENCHMARK.md` is the active product gate. It compares observable capabilities and task structure against established Android pose/reference products and prioritizes gaps that can be judged without recruiting participants:

- reference-image matching;
- mannequin volume/readability;
- hand/foot orientation;
- direct depth manipulation;
- focused local pose reuse;
- drawing-oriented export modes.

Real-artist sessions remain optional advisory evidence. They are required before publishing comparative speed/preference/quality claims, not before implementing or merging 0.3 product work.

## External evidence still required before a production-v1 claim

When production qualification resumes, retain evidence appropriate to the exact candidate:

- execute physical qualification across the supported device matrix;
- run final manual accessibility/device UX audit;
- provision and archive product-specific production/upload signing provenance;
- publish a stable public privacy-policy page and submit matching Play Data Safety answers;
- capture final store screenshots/feature graphic from the qualified build;
- complete Play closed testing and staged rollout with crash/ANR/vitals review.

A real-artist study is **not** a production-v1 checkbox by itself. It becomes mandatory only if release/store copy makes comparative human-performance claims that need such evidence.

Execution details are in `PRODUCTION_QUALIFICATION.md`; issue #83 remains the authority for operational production evidence if/when that track resumes.
