# Export ASC and TZF transfer design

Approved by the user on 2026-07-16.

## Goal

Make exported point clouds practical to move from the Android application to a
computer while preserving stitched station coordinates.  Consolidate the two
existing export-related toolbar actions into one explicit export flow.  Large
ASC files must remain importable by the application, and count-based thinning
must meet the requested output size for both 5,000 and 5,000,000 points.

## Scope

- Replace the standalone `ASC` and `Save project` toolbar actions with one
  `Export` action.
- Export the currently stitched project as ASC or TZF.
- Keep `.tzfp` as automatic internal workspace persistence only.  It keeps
  scan links, registration links, and transforms for reopening the workspace
  in this application; it is not a cross-device or PC-transfer workflow.
- Preserve the app-root world coordinate system in exported TZF so the scans
  open already stitched in RealWorks.

## Export user flow

### Top-level choice

`Export` opens a two-item dialog:

1. `ASC` — export the stitched point cloud.
2. `TZF` — export the source TZF scans with stitched coordinates.

The old individual ASC and project-save buttons are removed.  Project state
continues to be autosaved after changes and on lifecycle transitions.

### ASC

1. The user chooses `Without thinning` or `Thin`.
2. `Thin` offers:
   - `By point count` (exact count target),
   - `By percentage` (converted to an exact count target),
   - `By spacing, mm` (one representative per world-space voxel).
3. The user chooses a destination **folder** through the Android document-tree
   picker; no save-file picker and no editable file-name field are shown.
4. The app creates `ProjectName_YYYYMMDD-HHMMSS.asc`.  Invalid filename
   characters are replaced, `.asc` is always appended by the app, and a
   numerical suffix is added if the name already exists.

`Without thinning` remains available, but the confirmation includes the
estimated output size so a full export that may be hundreds of megabytes is
intentional.

### TZF

The TZF flow explains both modes before the user chooses one.

- `Overwrite source TZF`: creates a sibling backup before any original is
  changed.  The first backup is `scan.tzf.bak`; later backups receive a
  numerical suffix and are never overwritten.  The final stitched pose is
  written into the TZF registration metadata.
- `Save copies to folder`: the user selects a document-tree destination.
  Patched copies retain their source file names; collisions receive a suffix.
  Original TZFs are not changed.

The overwrite mode requests write access to each source directory as required
by the Storage Access Framework.  If access or backup creation cannot be
completed for every selected TZF, no original is overwritten.  ASC source
items cannot be represented as TZF and are reported as skipped rather than
silently converted.

## ASC import and large-file handling

The existing ASC import path copies the raw ASC into app cache and then builds
another cache.  A 500 MB ASC therefore consumes needless temporary storage and
appears unresponsive while it is parsed.

The replacement path streams the selected URI directly into one compact binary
XYZ cache:

- It does not keep a duplicate raw ASC in app cache.
- It uses a buffered token parser instead of allocating an array with
  `String.split()` for every line.
- It reports bytes read and valid point count, supports cancellation, and
  reports insufficient free space clearly.
- A completed cache is reusable on later opens.  Viewer LOD still limits the
  first GPU upload; the complete cloud does not need to be resident in memory.
- Cache construction is transactional, so a cancelled or failed index does
  not replace a valid cache.

## ASC export and thinning

All ASC export operates on source points transformed into project world
coordinates.  A locked CUT is applied before both counting and output; an
unlocked CUT does not affect export.

For `By point count`, the exporter makes two streaming passes:

1. Count eligible points after the world transform and optional locked CUT.
2. Make a seeded sequential random selection with `remainingTarget /
   remainingPoints` probability.  The algorithm selects exactly the requested
   number (or all eligible points if the request exceeds availability) without
   retaining the cloud in memory.

`By percentage` first rounds the percentage to its target count and uses the
same algorithm.  These modes therefore satisfy the ±5% count requirement for
5,000 and 5,000,000 targets; normal output is exact.  `By spacing, mm` is a
separate geometry-driven option: it uses a world-space voxel filter and does
not promise a requested count.

The destination is written as `*.partial` and published as the final ASC only
after a successful flush.  Cancellation or failure removes the partial file.

## TZF coordinate writer

The reader already identifies TZF registration information at the scan-info
registration block: a little-endian 3×3 orientation matrix followed by a
three-value translation.  The native writer will create this representation
from each scan's final app-root world pose (Z rotation plus XYZ translation).
It changes registration metadata only; point tiles and other TZF payloads are
not recompressed or rebuilt.

For every TZF output the writer:

1. Stages a complete patched file in private temporary storage.
2. Reopens it with the native reader and verifies the written pose, finite
   matrix values, and TZF block-directory bounds.
3. Creates the backup (for overwrite mode) or destination copy.
4. Publishes the verified staged file.

If a stage, verification, backup, or publish operation fails, the original
file remains unchanged.  A successful output will use the app-root coordinate
system, so reopening the set in RealWorks presents its stations in their
stitched common coordinates.

## `outside bounds` diagnosis and failure handling

`outside bounds` during thinned ASC export is treated as a separate regression
from large-ASC importing.  The source stream is reopened or safely rewound for
each pass; session state from the count pass cannot be reused as tile state for
the write pass.  Native errors include the scan name and, when applicable, the
TZF component, tile/block index, offset, size, and file size.  Export failures
delete their partial destination and never leave a successful-looking but
incomplete ASC.

The fix is validated against repeated count/output passes and the original
reported repro scan when it is available.  A genuinely truncated or corrupt
TZF is reported precisely rather than being read past its bounds.

## Tests and acceptance criteria

### JVM tests

- Count-based ASC selection produces 5,000 and 5,000,000 points, within ±5%
  (expected to be exact), including a locked CUT.
- Percentage conversion, target-above-source behavior, partial-file cleanup,
  generated ASC names, and noneditable `.asc` suffix.
- ASC cache creation uses a single compact cache, supports a large streamed
  fixture, preserves point count/bounds, reopens it, and cancels atomically.
- `.tzfp` round-trips scan URIs, world transforms, registration sets, and
  registration links.

### Native and integration tests

- Patch a controlled TZF registration block, reopen it, and assert its
  orientation and translation match the requested pose.
- Reject invalid registration offsets, non-finite matrix values, and invalid
  block ranges without changing the source file.
- Verify overwrite creates a non-overwritten backup; verify copy mode leaves
  the source bytes unchanged.
- Run repeated ASC analysis/output passes to cover the `outside bounds`
  regression path.
- Build the app and manually open exported TZFs in RealWorks to confirm that
  all stations are already in the same stitched coordinates.
