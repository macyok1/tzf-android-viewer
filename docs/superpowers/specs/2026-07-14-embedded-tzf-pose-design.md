# Embedded TZF pose registration design

## Goal

Prevent valid X7 scan pairs from failing automatic stitching with
`RMS выше допуска` when a reliable initial placement is already embedded in
the TZF. Use that placement as the starting hypothesis and keep the existing
strict local refinement and quality gates. Do not hide a bad result by merely
raising the RMS limit.

## Pose source and coordinate system

The TZF registration block at `scanInfoOffset + 0x44` contains a 3x3
orientation matrix followed by XYZ translation in millimetres. Native preview
sessions expose a validated four-degree-of-freedom pose `[x, y, z, yaw]`.

The preview decoder already levels points from the full orientation matrix.
Therefore the project transform uses only the translation and rotation around
Z. Values with a missing block, non-finite components, or an invalid
orientation remain unavailable and must never move a scan.

Large scanner coordinates are not copied directly into the scene. The first
new scan with a valid embedded pose becomes the local project origin. Every
later new scan is placed relative to that anchor, then composed with the
anchor's current world transform. This preserves the X7 arrangement while
keeping project coordinates small.

## Project model and compatibility

Project format advances from version 3 to version 4. Each scan can persist:

- whether an embedded pose is valid;
- whether automatic embedded placement has already been considered;
- the four pose values `[x, y, z, yaw]`.

All scans loaded from version 1-3 projects are marked as already considered.
Their stored transforms are never changed by migration. Existing version 4
scans and every scan the user has already moved are likewise not automatically
repositioned.

For a newly imported scan, pose metadata is read before the cloud is first
shown. If it is the first valid pose in a new compatible sequence, it stays at
the current project origin. Otherwise its relative pose is applied exactly
once. The project is marked changed so the metadata and resulting transform are
saved.

If a project already contains legacy scans without an embedded-pose anchor, a
new scan is not moved automatically: its pose is retained for diagnostics, but
placement is marked considered. This avoids mixing an unknown legacy frame with
the X7 frame.

## Stitching behavior

Automatic stitching starts from the current project placement, which now
usually reflects the embedded X7 pose. The existing bounded local refinement
may correct the placement only within its safety limits. RMS, P95, overlap,
consistency, ambiguity, and correction bounds remain hard acceptance gates.

On successful refinement the refined transform is committed. On rejection the
exact input transform remains unchanged and the UI reports the strongest
quality failure. A failed refinement can therefore no longer destroy a useful
embedded placement.

Manual alignment remains available. Once the user moves a scan, subsequent
refinement starts from that manual transform and embedded-pose initialization
does not run again.

## Interfaces

- `tzf_core` derives and validates the initial 4DOF pose from the already
  parsed registration information.
- JNI exposes the preview session pose as a four-float array or an empty result
  when unavailable.
- `NativePreviewSession` owns the Java-side access and session lifetime checks.
- `ProjectModel.Scan` owns persisted pose metadata and one-time application
  state.
- The scene-loading/import path performs initialization before first display
  and requests project persistence after a change.

These boundaries keep TZF parsing native, migration in the codec/model, and
scene placement in the existing import flow.

## Failure and rollback behavior

- Invalid or absent metadata: leave the transform unchanged.
- No compatible anchor: leave the transform unchanged and mark initialization
  considered.
- Native/JNI read error: continue normal import without automatic movement and
  surface the existing import error only if decoding itself fails.
- Save failure: retain the in-memory placement and report the existing project
  save error; reopening the last saved project cannot partially apply a pose.
- Refinement rejection: restore the exact pre-refinement transform.

## Verification

- Native tests cover pose extraction from the registration block and rejection
  of invalid/non-finite metadata.
- Model/codec tests cover version 3 migration without transform changes.
- Placement tests cover a zeroed first anchor, correct relative XYZ/yaw for the
  second scan, composition with an anchor transform, and no movement without a
  compatible anchor.
- Existing registration fixtures continue to enforce strict RMS/P95 behavior.
- Android native sources receive offline NDK syntax compilation only.
- GitHub CI produces the updater-visible build; no local Gradle download or
  emulator run is required.
