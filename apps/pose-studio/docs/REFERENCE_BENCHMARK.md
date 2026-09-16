# Pose Studio reference-product benchmark

This document is the current product-development gate for Pose Studio 0.3. It deliberately uses observable competitor capabilities, established interaction patterns and executable product tasks before requiring recruitment of external artists.

## Decision boundary

Real-artist studies are **not a blocker for 0.3 product development**. They remain useful later for subjective workflow validation and for any comparative speed/quality marketing claim.

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

Pose Studio should **not** attempt to win by model/asset count. The current differentiated direction is:

1. get from an existing visual reference or an imagined action to a usable construction reference with minimal interaction;
2. keep the workflow offline and account-free;
3. keep project data readable without an entitlement dependency;
4. provide clean exports that fit directly into a drawing workflow;
5. keep the UI closer to a physical drawing mannequin than a mobile 3D editor.

## Current capability gaps

Against the reference set, the highest-value gaps are:

1. **Reference matching** — import a local reference image, align it behind the mannequin, adjust opacity/scale/position, then pose against it.
2. **Readable mannequin volume** — stronger chest/pelvis/head and limb volume cues so the result is useful beyond a stick-figure gesture.
3. **Hand/foot direction** — visible orientation and useful presets, backed by the existing schema-v2 roll semantics.
4. **Depth manipulation** — reduce reliance on Forward/Back buttons for normal 3D posing.
5. **Pose reuse** — a focused local pose library and user-saved poses before any marketplace concept.
6. **Drawing-oriented output** — transparent PNG first, then silhouette/construction-line modes if they preserve a simple workflow.

Multi-character scenes and props are secondary until the single-figure workflow is materially deeper.

## 0.3 acceptance method

A 0.3 increment is justified when it closes one or more gaps above and passes all applicable deterministic checks:

- the primary task is reachable without adding account/network requirements;
- the interaction does not make direct posing harder;
- saved-project compatibility is preserved or migrated explicitly;
- normal export remains deterministic and reference assets do not leak into exports unless explicitly requested;
- large images/assets are bounded before decode/render;
- phone/tablet and 200% font layouts keep primary actions reachable;
- existing API/instrumentation/performance regression gates remain green.

## Current 0.3 sequence

1. local reference-image overlay;
2. stronger procedural mannequin volume and explicit hand/foot orientation;
3. depth-aware direct manipulation improvements;
4. local pose library / saved poses;
5. silhouette/construction export modes;
6. only then consider multiple mannequins/props.

External artist sessions may be added at any point, but they are advisory for this phase rather than a source-control merge gate. Comparative speed/preference claims remain blocked until suitable human evidence exists.
