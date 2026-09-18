# Pose Studio reference-product benchmark

This document is the product-development benchmark for Pose Studio. It deliberately uses observable competitor capabilities, established interaction patterns and executable product tasks before requiring recruitment of external artists.

## Decision boundary

Real-artist studies are **not a blocker for product development**. They remain useful for subjective workflow validation and are required before making comparative speed, preference or quality claims about named alternatives.

The repository may make implementation decisions from reproducible desk research when the evidence is observable, for example:

- whether established pose products expose direct manipulation, inverse kinematics, hands/feet, perspective controls, pose libraries, props or transparent export;
- whether a proposed Pose Studio workflow removes obvious navigation or translation steps;
- whether an interaction can be exercised deterministically in product tests;
- whether the app preserves its local-first and user-owned-project advantages.

The repository must **not** convert desk research into an unsupported claim that Pose Studio is faster, preferred by artists, or produces better drawings than a named competitor.

## September 2026 reference set

The reference set is intentionally small and established:

### Easy Pose

Google Play listing: `com.madcat.easyposer`

Observable reference capabilities include detailed joint manipulation, mirroring, multiple body types, multi-model scenes, pose presets, hand controls, perspective/FOV control, lighting/shadows, line/wire presentation, autosave and transparent PNG export.

### Poseit

Google Play listing: `com.OneManBand.PoseIt`

Observable reference capabilities include a low-detail drawing mannequin, screen-space rotation, inverse kinematics, multiple mannequins and props. Its positioning is especially relevant because it treats the mannequin as a drawing aid rather than a general 3D editor.

### Magic Poser

Google Play listing: `com.magicposernew`

Observable reference capabilities include drag-and-pose interaction, a large body/hand/foot pose library, props, multi-character scenes, perspective controls, saved camera angles, three-plane grids and high-resolution PNG export.

## What Pose Studio should compete on

Pose Studio should **not** attempt to win by model/asset count. The differentiated direction is:

1. get from an existing visual reference or an imagined action to a usable construction reference with minimal interaction;
2. keep the workflow offline and account-free;
3. keep project data readable without an entitlement dependency;
4. provide clean exports that fit directly into a drawing workflow;
5. keep the UI closer to a physical drawing mannequin than a mobile 3D editor.

## 0.3 benchmarked gaps — closed in source

The six highest-value single-figure gaps identified for 0.3 are now represented in source main:

1. **Reference matching — #109.** Local reference image, bounded decode, behind-mannequin rendering, opacity/scale/position controls and no export/project leakage.
2. **Readable mannequin volume — #111.** Procedural limb capsules plus oriented torso masses shared between interactive and PNG rendering.
3. **Hand/foot direction — #117.** Visible endpoint orientation backed by schema-v2 roll semantics and explicit wrist/foot roll controls.
4. **Depth manipulation — #118.** Explicit selected-joint camera-axis Depth mode alongside normal camera-plane dragging.
5. **Pose reuse — #119.** Focused local pose library with save/apply/delete, preserved project/camera/light identity and normal Undo integration.
6. **Drawing-oriented output — #120.** Shaded, transparent, silhouette and construction PNG modes from the same deterministic geometry.

#121 then reorganized the product shell around **Pose / Scene / Export**, making the creation sequence more task-oriented and preventing workspace scroll state from leaking between tasks, including at 200% font scale.

These changes close the specific 0.3 capability sequence. They do **not** establish a comparative claim that Pose Studio is faster or preferred versus the reference products.

## 0.4 interaction-cost sequence — closed in source

The next single-figure evaluation sequence identified after 0.3 has now been implemented and validated:

1. **Direct reference alignment — #124.** Choosing a reference enters an explicit canvas alignment mode; one-finger drag moves the image and pinch adjusts it, while leaving the mode restores pose/camera gestures immediately.
2. **Stable reference actions — #125.** Choose / Hide / Reset / Clear no longer depend on nested horizontal scrolling and remain reachable at 200% font scale.
3. **Reusable framing helpers — #126.** Front / Three-quarter / Side restore default distance, FOV and target while reusing the camera state already stored in the project.
4. **Artistic drawing proportions — #127.** Balanced / Long legs / Long torso retarget segment lengths without anatomical/medical claims, are absolute/idempotent, and remain compatible with the existing joints-based project/library/export data paths.

The 0.4 feature closure exact main is `aa907e76a7f39ee96fa8f4351cc181abbcb652a6`. Post-merge CI Contracts #182, Pose Studio #188 and canonical CI #1534 completed successfully, including API36/200% font, Android functional, hosted performance, 16 KiB compatibility, immutable source release and stable-debug-key APK publication.

#127 also produced useful negative evidence: its initial layout placed Drawing proportions above the joint picker and caused two existing API36 first-screen contracts to fail. The tests were not relaxed; the product hierarchy was changed so core joint controls remain ahead of the secondary proportion block.

These results are implementation evidence only. They do **not** establish a comparative claim that Pose Studio is faster or preferred versus Easy Pose, Poseit or Magic Poser.

## Post-0.4 decision boundary

Multiple mannequins and basic props remain observable reference-product capabilities, but the reassessment is complete and the current decision is to defer them. The completed 0.4 work removes concrete single-figure setup costs; adding more scene entities would increase navigation and state complexity without current task evidence that it shortens the primary `time_to_reference_pose` workflow.

Reopen multi-character/basic-prop work only when a concrete drawing-reference task shows that scene breadth improves the task more than it increases setup cost. Until then, prioritize small single-figure interaction improvements that can be exercised deterministically or supported by later human evidence.

## Increment acceptance method

A product increment is justified when it closes an observable workflow gap and passes all applicable deterministic checks:

- the primary task is reachable without adding account/network requirements;
- the interaction does not make direct posing harder;
- saved-project compatibility is preserved or migrated explicitly;
- normal export remains deterministic and reference assets do not leak into exports unless explicitly requested;
- large images/assets are bounded before decode/render;
- phone/tablet and 200% font layouts keep primary actions reachable;
- existing API/instrumentation/performance regression gates remain green.

External artist sessions may be added at any point, but they are advisory for source development rather than a merge gate. Comparative speed/preference claims remain blocked until suitable human evidence exists.
