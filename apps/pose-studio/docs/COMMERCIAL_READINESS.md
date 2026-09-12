# Pose Studio commercial-readiness checklist

Repository gates are fail-closed: code may land before external qualification, but v1/commercial-production status is not granted by documentation alone.

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
- responsive phone/landscape/tablet workspace with scrollable inspector tabs for large-font layouts;
- onboarding plus an always-available localized non-canvas joint selector and directional adjustment controls;
- invisible joint-roll editing is withheld until renderer feedback exists;
- en-US, zh-CN, zh-TW and zh-HK UI resources;
- adaptive/monochrome launcher icon;
- visible in-app privacy-policy entry while the offline app remains free of network permission;
- release APK/AAB compilation in the normal Pose gate;
- API 36 instrumentation, non-canvas joint-selection coverage and 200% font-scale execution in CI;
- physical-device workflow that records immutable device/source provenance;
- frozen artist-benchmark CSV schema plus a fail-closed study-shape/statistics analyzer;
- production qualification runbook that keeps signing/device/Play/accessibility/artist evidence external and auditable.

## External evidence still required before v1 claim

- execute physical qualification on the final candidate across the supported device matrix;
- run TalkBack/manual accessibility audit with an artist workflow;
- provision and archive product-specific production/upload signing provenance;
- publish a stable public privacy-policy page and submit matching Play Data Safety answers;
- capture final store screenshots/feature graphic from the qualified build;
- run the frozen ten-pose study with real illustrators against at least two Android alternatives;
- closed testing and staged rollout with crash/ANR/vitals review.

The user benchmark is a product gate: if direct manipulation is not materially faster/clearer, do not compensate by building a large asset marketplace.

Execution details are in `PRODUCTION_QUALIFICATION.md`; issue #83 remains the authority for external v1 evidence.
