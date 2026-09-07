# Pose Studio quality contract

## Build gate

`poseStudioCheck` must run unit tests, debug/release lint, assemble the debug APK and compile Android instrumentation tests.

## Domain invariants

- Pose transforms must not produce NaN/Infinity coordinates.
- Endpoint IK preserves the two configured bone lengths within floating-point tolerance.
- Non-root joint dragging preserves its parent bone length.
- Undo/redo operates on complete joint snapshots.
- Project schema is explicitly versioned.
- Unknown/missing joints on import fall back safely instead of making the entire project unreadable.

## Privacy gate

The main manifest must not request `android.permission.INTERNET`.

## UX acceptance

- A joint can be selected directly on the mannequin.
- Dragging a wrist/ankle moves the limb through two-bone IK.
- Dragging empty scene space orbits the camera.
- A useful preset is reachable in one tap.
- Save/open/export are available without account setup.

## Performance targets for v0.1

These are engineering targets, not yet release gates:

- interactive scene manipulation target: 60 Hz on a representative mid-range device;
- no allocation-heavy project serialization inside pointer move events;
- project save/import should be effectively instantaneous for the procedural skeleton schema;
- 1440 px PNG export should complete without network or background service.