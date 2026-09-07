# Pose Studio roadmap

This roadmap is gated by evidence. It is not a promise that every item ships.

## v0.1 — interactive offline MVP (current PR)

Goal: prove the product boundary and direct-manipulation loop.

Implemented:

- independent Android application and CI gate;
- offline/no-account privacy contract;
- 19-joint procedural 3D mannequin;
- fixed bone-length behavior;
- two-bone IK for wrists and ankles;
- direct joint drag and camera orbit;
- four pose presets;
- camera and directional-light controls;
- local save/open;
- pose undo/redo;
- versioned JSON import/export;
- PNG export;
- JVM math tests and instrumentation-test compilation.

Not a release claim: visual anatomy, hand/face controls, product benchmarking and release/store evidence are not yet mature.

## v0.2 — posing speed prototype

Gate: v0.1 CI green and real-artist usability sessions identify manipulation speed as the main opportunity.

Candidate scope:

- capsule/mesh mannequin that preserves v1 project semantics;
- configurable body proportions without medical/anatomical claims;
- mirror/copy limb;
- local pose library;
- hand presets plus finger refinement;
- head/eye/face direction controls;
- foot grounding;
- pinch zoom/two-finger camera gestures;
- reference-image overlay imported locally;
- renderer performance instrumentation.

Evidence:

- frozen ten-pose benchmark protocol;
- compare median/p95 completion time with at least two established Android pose tools;
- test on phone and tablet form factors;
- preserve/import v1 project fixtures.

Kill condition: if direct manipulation does not produce a material speed/clarity advantage, do not compensate by adding hundreds of models/assets.

## v0.3 — artist workflow depth

Only after posing speed is validated:

- multiple mannequins in one scene;
- basic props/primitives;
- camera/reference presets;
- local scene templates;
- silhouette and line-art render modes;
- higher-resolution/transparent export;
- local project duplication/search/tagging;
- richer project migration fixtures.

Still non-goals unless separately justified: social feed, cloud collaboration and generative AI.

## v1.0 — release readiness

Required before a release claim:

- product-specific application signing and documented key ownership;
- product-scoped store listing/privacy disclosure;
- product-prefixed release namespace, e.g. `pose-studio-v1.0.0`;
- product-scoped source manifest/release evidence;
- compatibility test matrix for supported Android versions;
- measured interaction/startup/export performance baseline;
- project schema migration tests;
- accessibility review;
- crash/ANR review without introducing mandatory telemetry;
- asset/license audit for any mannequin/prop assets added after v0.1;
- user testing confirming time-to-pose advantage.

## Commercial hypothesis

Commercialization must not precede product validation. Current hypothesis:

- free core creation sufficient to evaluate posing quality;
- optional lifetime Pro unlock for advanced local features;
- optional one-time asset packs only if artist demand is demonstrated;
- no requirement for a recurring subscription to open or export existing user projects.

Entitlement checks, if later added, guard creation/use of premium capabilities; they do not become a prerequisite for parsing a project file.

## Metrics without mandatory server telemetry

The MVP intentionally has no telemetry. Product tests can gather metrics in controlled studies or through explicit local benchmark/export tooling.

Useful measures:

- median/p95 `time_to_reference_pose`;
- correction operations per pose;
- undo count;
- mis-selection rate;
- export completion time;
- project corruption/recovery failures;
- 60 Hz frame-budget adherence during manipulation.

If opt-in telemetry is later proposed, it requires a separate privacy review and cannot be necessary for core operation.