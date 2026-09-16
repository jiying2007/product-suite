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

## 0.3 — drawing-reference workflow depth — current

Development is guided by `REFERENCE_BENCHMARK.md`. Real-artist recruitment is optional during this phase and does not block repository changes.

Priority order:

1. **Local reference overlay** — choose an image, align it behind the mannequin, tune opacity/scale/offset, and keep pose gestures authoritative.
2. **Readable mannequin volume** — stronger capsule/skinned-style procedural silhouette and anatomical landmarks while preserving project semantics.
3. **Hand/foot orientation** — visible hand/foot direction and useful presets using schema-v2 roll state.
4. **Depth manipulation** — reduce normal dependence on Forward/Back buttons with a direct depth gesture or local manipulator.
5. **Local pose reuse** — focused pose library, user-saved poses and simple pose blending.
6. **Drawing output modes** — high-resolution transparent, silhouette and construction-line export where deterministic.

Secondary, only after the single-figure workflow is substantially deeper:
- configurable artistic body proportions without medical claims;
- multiple mannequins;
- basic props;
- additional scene/grid helpers.

Explicitly avoid chasing competitor asset counts, cloud accounts, generative AI or a marketplace as substitutes for a strong posing workflow.

## v1.0 — production qualification

Production qualification remains a separate operational decision. When resumed, it requires the normal release evidence appropriate to the exact candidate: signing/AAB provenance, supported-device compatibility, accessibility, physical performance, Play policy/store evidence and staged rollout.

A real-artist study is **not required to compile or merge 0.3 product work**. It is required before publishing comparative claims such as "faster than" or quantified preference/quality claims about named alternatives.

## Commercial hypothesis

Core creation must remain sufficient to evaluate posing quality. A future lifetime Pro unlock may guard advanced local creation features, but losing entitlement never becomes a prerequisite for parsing/opening/exporting an existing user project. Recurring subscription is not required merely to regain access to user-owned files.
