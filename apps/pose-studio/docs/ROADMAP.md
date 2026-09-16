# Pose Studio roadmap

This roadmap is evidence-gated and describes remaining work, not a promise to ship every item.

## 0.1 — offline interaction MVP — complete

Established independent Android identity/CI, 19-joint procedural mannequin, fixed bone lengths, wrist/ankle IK, direct manipulation, four presets, camera/light, local save/open, undo/redo, versioned JSON and PNG export.

## 0.2 — commercial-beta foundation — complete

- mirror/copy/ground speed tools;
- depth-aware camera-plane joint dragging;
- pinch zoom/true two-finger camera-target pan;
- shared render model with procedural volume cues;
- schema-v2 roll semantics plus v1 migration fixtures;
- rollback-safe atomic save plus conflated/debounced autosave recovery;
- dirty-work confirmation for New/Open/Import, duplicate/delete and corruption visibility;
- bounded/defensive project import;
- responsive phone/landscape/tablet inspector;
- interactive first-run guide and accessible precise joint controls;
- en-US/zh-CN/zh-TW/zh-HK resources and product launcher icon;
- Unicode-safe export filenames and clean transparent PNG export;
- light/dark workspace and system-chrome alignment;
- release APK/AAB compilation, API-36 instrumentation, 200% font checks and release-like performance tooling;
- product-specific release/signing/privacy/Data Safety/store-source contracts.

The 0.2.1 prerelease remains the frozen commercial-beta artifact. Production-v1 work is intentionally secondary to product-value depth.

## 0.3 — drawing-reference workflow depth — landed on source main

Development was guided by `REFERENCE_BENCHMARK.md`. Real-artist recruitment was optional during this phase and did not block repository changes.

Landed sequence:

1. **Local reference overlay — #109.** Choose a local image, align it behind the mannequin, tune opacity/scale/offset, and keep pose gestures authoritative.
2. **Readable mannequin volume — #111.** Procedural capsule body segments plus oriented chest/pelvis construction masses share one screen/export render model.
3. **Hand/foot orientation — #117.** Schema-v2 roll is visible through procedural wrist/foot orientation paddles, with roll controls exposed only where renderer feedback exists. #117 is the clean-stack replacement for the original #112.
4. **Direct depth manipulation — #118.** A selected joint can be dragged along the camera depth axis without making Forward/Back buttons the ordinary posing path. #118 replaces the original stacked #113.
5. **Local pose reuse — #119.** User-owned local pose snapshots support save/apply/delete while preserving camera/light/project identity. #119 replaces the original stacked #114.
6. **Drawing output modes — #120.** Shaded/transparent output remains available alongside deterministic silhouette and construction PNG handoff modes. #120 replaces the original stacked #115.
7. **Task-oriented workspace — #121.** Top-level navigation is Pose / Scene / Export; Camera and Light live under Scene, drawing exports lead Export, and each workspace keeps its own scroll identity so switching tasks starts at the task top even at 200% font scale.

The single-figure 0.3 workflow is therefore materially deeper than the 0.2 commercial-beta foundation: reference matching → readable volume/orientation → planar/depth posing → local pose reuse → drawing-oriented export.

### Post-0.3 exploration

Do not immediately turn Pose Studio into a general scene editor. The next product work should continue optimizing `time_to_reference_pose` and remain evidence-gated. Candidate directions, in priority order for evaluation rather than as shipment promises:

- reduce reference-alignment interaction cost while preserving authoritative pose gestures;
- add configurable artistic body proportions without medical/anatomical claims;
- evaluate saved camera/grid helpers where they reduce repeated setup;
- reassess multiple mannequins/basic props only if single-figure workflow evidence shows they materially improve common drawing-reference tasks.

Explicitly avoid chasing competitor asset counts, cloud accounts, generative AI or a marketplace as substitutes for a strong posing workflow.

## v1.0 — production qualification

Production qualification remains a separate operational decision. When resumed, it requires the normal release evidence appropriate to the exact candidate: signing/AAB provenance, supported-device compatibility, accessibility, physical performance, Play policy/store evidence and staged rollout.

A real-artist study is **not required to compile or merge product work**. It is required before publishing comparative claims such as "faster than" or quantified preference/quality claims about named alternatives.

## Commercial hypothesis

Core creation must remain sufficient to evaluate posing quality. A future lifetime Pro unlock may guard advanced local creation features, but losing entitlement never becomes a prerequisite for parsing/opening/exporting an existing user project. Recurring subscription is not required merely to regain access to user-owned files.
