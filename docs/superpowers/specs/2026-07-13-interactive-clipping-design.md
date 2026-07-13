# Interactive clipping design

## Goal

Make sectioning useful on dense indoor scans: create an outlier-resistant clipping box, move any of its six faces directly in the viewport, keep the existing lower/upper Z controls synchronized, and persist the result with the project.

## User interaction

- Pressing `CUT` enables clipping. The initial box contains the central 70% coordinate distribution on each world axis (15th through 85th percentiles), so isolated distant points do not enlarge it.
- A long press on a visible point recenters the current box on that point without changing its size.
- While clipping is unlocked, six arrows are rendered over the cloud at the face centers. Dragging an arrow changes only that face. A minimum box thickness prevents face inversion.
- Touches that do not hit a clipping handle retain the existing camera, measurement, and transform-gizmo behavior.
- The lower and upper Z sliders remain available and modify the same world-space box as the Z face handles.
- Locking keeps clipping active but hides the box and handles. Reset disables clipping and clears the saved bounds.

## Architecture

`ClipBoxMath` is a pure Java helper responsible for percentile bounds, recentering, clamping, and face updates. It has no Android or OpenGL dependency and receives sampled world-space XYZ values.

`PointCloudView.CloudRenderer` owns the live clipping box and performs handle projection, screen-space hit testing, and drag-to-world conversion. It reports bound changes to `MainActivity` only when a drag or recenter action commits, avoiding project writes on every move.

`MainActivity` coordinates CUT/lock/reset/Z-slider state and copies committed values into `ProjectModel`. Project persistence is upgraded with backward-compatible defaults: older projects load with clipping disabled.

## Data flow

1. Scene chunks are appended as today. The renderer maintains a bounded reservoir sample of visible world-space points for percentile calculation; it never duplicates the complete cloud.
2. CUT requests default bounds. The renderer calculates percentiles from the reservoir and activates the box.
3. Handle dragging updates GPU clipping uniforms immediately. On release, the final six bounds are sent to the activity and saved once.
4. Long press uses the existing point picker, recenters the box, then commits it.
5. Z slider changes are converted to world Z bounds and committed when tracking stops.

## Rendering and hit testing

The box and arrows are drawn with depth testing disabled so they remain visible over points. Each face center is projected with the current MVP matrix. A fixed density-independent hit radius selects the nearest handle. Drag distance is projected onto the selected world axis; near edge-on axes fall back to a stable screen vertical mapping. Bounds are clamped to a small scene-relative minimum thickness.

The transform gizmo keeps priority when it is active. Clipping handles have priority otherwise, followed by long-press recentering and normal camera gestures.

## Persistence

The project stores:

- clipping enabled;
- clipping locked;
- six world-space bounds.

The project format version increments. Missing fields or invalid/non-finite bounds disable clipping safely. Preview-only X7 windows may use clipping but do not persist it.

## Failure handling

- If no scene points are ready, CUT reports that clipping cannot be created and remains disabled.
- If percentile sampling produces degenerate bounds, scene bounds are used as a fallback.
- Invalid persisted bounds are ignored.
- Losing a touch gesture commits the last valid bounds on `ACTION_CANCEL`, matching transform-gizmo behavior.

## Verification

Unit tests cover percentile rejection of outliers, 70% quantiles, recentering, minimum thickness, and persistence defaults/round-trip. Manual verification covers all six face drags, Z synchronization, lock/reset, long-press recentering, camera gestures outside handles, project reopen, and multi-scan transformed scenes.

