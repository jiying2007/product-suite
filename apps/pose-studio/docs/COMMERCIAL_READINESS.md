# Pose Studio commercial-readiness checklist

Repository gates are fail-closed: code may land before external qualification, but v1/commercial-production status is not granted by documentation alone.

## Repository-side implemented

- offline/no-account core and no INTERNET permission;
- schema-v2 portable project format with v1 migration and defensive import limits;
- debounced autosave recovery plus atomic explicit saves;
- dirty-work confirmation, duplicate/delete and corrupt-project visibility;
- direct IK, symmetry/copy/ground tools, depth-aware drag and two-finger camera zoom/pan;
- shared scene render model used by interactive and bitmap renderers;
- responsive phone/landscape/tablet workspace;
- onboarding and non-canvas accessible joint-adjustment controls;
- en-US, zh-CN, zh-TW and zh-HK UI resources;
- adaptive/monochrome launcher icon;
- release APK/AAB compilation in the normal Pose gate;
- API 36 instrumentation and 200% font-scale execution in CI;
- physical-device workflow that records immutable device/source provenance.

## External evidence still required before v1 claim

- execute physical qualification on the final candidate across the supported device matrix;
- run TalkBack/manual accessibility audit with an artist workflow;
- provision and archive product-specific production/upload signing provenance;
- publish a stable public privacy-policy page and submit matching Play Data Safety answers;
- capture final store screenshots/feature graphic from the qualified build;
- run the frozen ten-pose study with real illustrators against at least two Android alternatives;
- closed testing and staged rollout with crash/ANR/vitals review.

The user benchmark is a product gate: if direct manipulation is not materially faster/clearer, do not compensate by building a large asset marketplace.
