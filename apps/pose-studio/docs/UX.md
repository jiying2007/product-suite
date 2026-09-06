# Pose Studio UX contract

## North-star interaction

The app should feel like picking up a digital drawing mannequin. The artist should spend time **posing**, not navigating property panels.

Primary metric: `time_to_reference_pose`.

## Workspace hierarchy

1. **Scene** — largest visual area, always the primary surface.
2. **Pose** — presets, undo/redo and direct-manipulation guidance.
3. **Camera** — framing controls.
4. **Light** — form-clarity controls.
5. **Project** — durable storage and export.

The project name and unsaved state remain visible in the top bar.

## Direct manipulation

### Select

A pointer down near a visible joint selects the nearest joint within the hit radius. Selected joints receive a visible halo. Hit targets are intentionally larger than rendered joint markers.

### Drag endpoints

Wrists and ankles use two-bone inverse kinematics. The endpoint follows the pointer in the camera plane while shoulder/elbow or hip/knee geometry resolves automatically.

### Drag other joints

The selected branch rotates/repositions around the parent while keeping parent-child bone length fixed. Descendants move with the joint so limb topology does not tear.

### Drag pelvis

Moves the full mannequin.

### Drag empty scene

Orbits the camera rather than creating a selection mode.

### Gesture lifecycle

A continuous pointer gesture is one undo operation. Pose changes must not restart the detector mid-drag.

## Pose accelerators

Presets are starting points, never destructive asset dependencies. v0.1 includes Neutral, Contrapposto, Reach and Run.

Roadmap accelerators, in priority order:

1. mirror left/right limb;
2. copy/paste limb pose;
3. saved local pose presets;
4. pose blending;
5. hand-shape presets;
6. foot grounding/balance assistance.

Each accelerator must be evaluated by whether it reduces time-to-pose.

## Camera

Artists need composition, not cinematography menus. Camera provides:

- front / three-quarter / side one-tap views;
- orbit;
- pitch/yaw;
- distance;
- field of view.

Future focal-length labels may be offered as artist-friendly presets, but the stored semantic value remains FOV.

## Light

v0.1 deliberately exposes a single directional light with azimuth, elevation and intensity. The objective is to clarify form and shadow direction, not simulate a complete renderer.

A future three-light setup should use named presets (Key / Fill / Rim) before exposing low-level light objects.

## Project ownership

Save/Open must never require sign-in. Exported JSON is human-inspectable and versioned. PNG export uses a user-selected document URI.

Commercial packaging must obey:

- losing Pro status cannot make an existing project unreadable;
- a project that used a paid bundled model remains openable after entitlement changes;
- no subscription is required merely to regain access to the user's own local project data.

## Phone and tablet

Phone:

- scene uses remaining height above a compact lower inspector;
- horizontal controls may scroll rather than shrink targets below comfortable size.

Large screen/tablet:

- scene and inspector coexist;
- right-side inspector target width is roughly 360 dp;
- the scene remains the dominant area.

## Accessibility

Before release:

- every non-canvas control has a clear content description/label;
- selected joint must have non-color feedback in accessibility semantics;
- controls support large font sizes without hiding Save/Open/Export;
- minimum touch targets are maintained for chips/buttons;
- motion-only feedback is not required to understand state.

Direct canvas posing is a specialized visual interaction. Keyboard/switch alternatives should at minimum allow selecting a joint and adjusting it through inspector controls before claiming broad accessibility support.

## Onboarding

The ideal first-run tutorial is interactive and under one minute:

1. drag a highlighted wrist;
2. drag empty space to orbit;
3. tap a preset;
4. export reference.

Do not front-load account, asset-store or renderer concepts.

## Kill tests

The product should not advance to a large asset/model investment unless user testing demonstrates:

- materially faster pose recreation than leading Android alternatives;
- artists understand the direct manipulation model without a long tutorial;
- project save/export is trusted;
- the procedural mannequin is sufficient to validate posing UX.

A rough target is 40–50% lower median time across a fixed ten-pose test set. The exact benchmark protocol must be frozen before results are used for a go/no-go decision.