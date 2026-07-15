# Point-cloud renderer rewrite

Approved by the user on 2026-07-16.

## Goals

- Make orthographic rendering the default for new projects.
- Keep one-finger PAN aligned with screen pixels in every camera orientation and projection.
- Import, display, transform, clip, register, and export ASC point clouds alongside TZF scans.
- Isolate automatic and manual registration from progressive display-LOD updates.
- Replace the tall phone toolbar and repeated-click point controls with a compact instrument rail and two-click flyouts.
- Preserve existing projects, Trimble X7 capture, the proven native registration algorithm, and the centroid-pivot correction.

## Scope and compatibility

The rewrite replaces the scene, camera, source-loading, and GPU-streaming pipeline. It does not replace the registration solver or change accepted registration transforms.

Existing project files remain readable. A project codec migration adds explicit point-source metadata while inferring `TZF` for legacy scan records. Existing saved camera projection values are respected; only newly created projects default to orthographic mode.

The renderer selects an OpenGL ES 3 backend when available and retains an OpenGL ES 2 compatibility backend. Both backends consume the same immutable render-scene model and camera state.

## Point sources

Introduce a `PointCloudSource` boundary with operations for:

- metadata and source point count;
- local bounds;
- deterministic sampled points for registration;
- sequential point chunks for rendering and export;
- cancellation and lifecycle management.

Implementations:

1. `TzfPointCloudSource` wraps the existing native TZF preview session.
2. `AscPointCloudSource` parses text ASC and creates an app-private indexed binary cache during first import.

ASC parsing accepts UTF-8 text with blank lines and comment lines. It auto-detects whitespace, comma, or semicolon delimiters and these common layouts:

- `X Y Z`;
- `X Y Z intensity`;
- `X Y Z R G B`;
- `X Y Z intensity R G B`.

Coordinates are mandatory finite numbers. Optional attributes are retained in the cache where present; rendering initially keeps the existing station-color behavior so registration-set colors remain clear. Malformed lines are counted and skipped. Import fails with a specific message if no valid XYZ points are found.

The original content URI remains the durable project source. The binary cache is rebuildable and is not part of project serialization.

## Render scene and LOD

The UI thread owns project intent; the renderer owns immutable `RenderSceneSnapshot` instances. A snapshot contains stable station IDs, world poses, visibility, local bounds, render chunks, colors, and active-tool state. Rendering never reads mutable `ProjectModel` nodes directly.

A source scheduler performs decoding and sampling away from the GL thread. Completed CPU chunks enter a bounded upload queue. The GL thread uploads only a small time-budgeted batch per frame, preventing long VBO uploads and frame stalls.

Progressive LOD follows these rules:

- bounds and scene normalization are established once from source metadata, not recomputed for every chunk;
- replacing a station's LOD is atomic at a frame boundary;
- old GPU chunks remain visible until enough replacement chunks are ready;
- old buffers are released only after their replacement snapshot is active;
- obsolete requests are cancelled by generation IDs;
- memory pressure evicts inactive higher-detail chunks before visible base detail.

Changing point budget no longer tears down the whole scene. It schedules a replacement LOD for each station and cross-swaps it when ready.

## Registration isolation

Starting registration creates a `RegistrationSnapshot` containing source identities, deterministic sampled point arrays, world poses, station membership, and pivots. Native registration consumes only this snapshot.

While automatic registration, refinement, or the manual manipulator is active:

- display LOD requests may decode in the background but cannot change active station geometry, scene bounds, pivots, or normalization;
- automatic point-budget changes are deferred;
- interaction transforms update only station world matrices;
- candidate previews use the frozen registration poses and pivots.

When the operation finishes or is cancelled, queued display updates are applied at frame boundaries without changing the accepted registration transform. This removes the race where increasing displayed point count changes scene framing during matching or manipulation.

The native Perspective-style matching pipeline, validation thresholds, and centroid-pivot conversion remain unchanged.

## Camera and gestures

Create one authoritative `CameraState` shared by UI controls and render snapshots. Projection is explicitly assigned instead of toggled independently on the GL thread.

New projects start in orthographic projection. Existing projects restore their saved projection. Entering the manual manipulator temporarily stores the complete camera state, switches to top-down orthographic mode, and restores the exact saved state on exit.

PAN uses inverse view-projection unprojection:

1. unproject the previous and current touch positions onto a plane through the camera target;
2. subtract the resulting world positions;
3. move the camera target by the opposite delta.

Therefore finger movement maps directly to rendered screen movement for orthographic and perspective projections at every yaw and pitch. Pinch remains zoom. One-finger orbit remains controlled by the floating orbit toggle.

## ASC export

The document intent uses the `.asc` filename and an extension-neutral binary MIME type so document providers do not append `.txt`. Export output remains UTF-8 text with `X Y Z` records and an `.asc` display name.

Export streams from every `PointCloudSource`; therefore mixed TZF/ASC projects work without converting all points into memory. Existing random and spatial thinning remain. A locked CUT filters transformed world coordinates before thinning. An unlocked CUT does not affect export.

## Toolbar and visual system

The phone tool rail becomes a rounded floating instrument card with `wrap_content` height. It never paints a continuous strip down the viewport. Expanded layouts use the same visual hierarchy with text labels.

Primary phone rail order:

1. X7;
2. add (`+`) for TZF and ASC;
3. scans and registration sets;
4. point size;
5. point budget;
6. CUT;
7. ASC;
8. more.

Secondary actions such as fit, measure, projection, and grid remain in `more`, preventing rail overflow.

Point size and budget use anchored flyouts extending right from the source button. A first tap opens all values; a second tap chooses one:

- size: `1`, `2`, `3`, `5`;
- budget: `AUTO`, `150K`, `1M`, `10M`, `ALL`.

Only one flyout is visible. Tapping the viewport, another tool, or Back closes it. The chosen value is highlighted and accessibility labels include the current value.

Visual direction is a restrained surveying-instrument interface:

- near-black viewport and graphite raised surfaces;
- thin cool-gray borders to define cards without long opaque bars;
- cyan reserved for active camera/tools and R;
- amber reserved for moving station M and editable transforms;
- compact labels, consistent 44--48 dp touch targets, and short 120--160 ms flyout motion;
- status and registration controls use floating cards instead of full-width decoration unless the workflow requires the width.

## Failure handling

- Source errors affect only the failed station and provide retry/remove actions.
- ASC import reports valid points, skipped lines, and cache-build progress.
- Context loss rebuilds GPU chunks from source caches without changing project poses.
- A failed LOD upgrade leaves the previous visible LOD active.
- Registration cancellation discards its snapshot and then safely applies deferred display updates.
- Missing original ASC/TZF permissions are reported as a source-access error without deleting project metadata.

## Verification

Unit tests cover:

- camera unprojection in orthographic and perspective modes across yaw/pitch combinations;
- explicit projection-state synchronization and manipulator restoration;
- ASC delimiter/layout detection, malformed-line handling, bounds, and deterministic sampling;
- project migration and mixed TZF/ASC serialization;
- LOD generation cancellation, atomic replacement, and registration freeze/defer behavior;
- strict `.asc` destination naming and locked-CUT export filtering;
- flyout state and selected-value behavior.

Instrumentation and emulator smoke cover:

- import and reopen of large ASC files;
- mixed ASC/TZF display and R/M registration selection;
- one-finger PAN before and after repeated projection changes;
- manual and automatic registration while higher LOD is decoding;
- point-size and budget flyouts on phone and expanded layouts;
- context loss and project reopen;
- exported file extension and content.

Release validation keeps the existing JVM, portable native, signed APK, updater SHA, and Android smoke checks.
