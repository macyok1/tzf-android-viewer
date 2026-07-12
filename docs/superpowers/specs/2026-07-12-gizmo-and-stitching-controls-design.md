# Gizmo and stitching controls design

## Goal

Make manual alignment behave visually as moving the orange `M` object against a
stationary reference scene. Reorganize controls so that stitching actions, M
transform actions, and viewport display settings are not mixed together.

## Gizmo behavior

`M` remains the only editable target. Its world transform is updated during a
gizmo drag and then propagated only to scans contained by the selected `M`
node. Reference scans and the grid must not receive a transform from that
interaction.

The viewport camera frame (centre, span, and grid) is kept stable for the
duration of a gizmo drag. It is recalculated only after the drag ends, an
explicit fit operation, visibility change, scan load, or other scene-changing
operation. This prevents the apparent motion of the whole environment while
the orange object is adjusted.

The gizmo remains centred on the moving node's world transform and follows it
as it moves. Candidate previews from automatic registration use the same
target and visual rule.

## Controls

### Stitching overflow

The bottom pair card uses its three-dot button as the stitching overflow.
It contains two named actions:

* **Авто** — global registration for scans that may begin far apart; it looks
  for a robust maximum of common geometry before proposing a result.
* **Уточнение** — local registration for an `M` already positioned near `R`;
  it refines the current alignment.

The existing global and local registration engines are retained. Their labels,
status messages, and progress text are made consistent with these two user
facing modes. The overflow is unavailable while a registration task or result
decision is active. Cancel, apply, and reject remain on the pair card so their
state is always visible.

### M transform panel

When `M` is selected, a compact transform panel provides reset, save current
M position, and restore saved M position. It is separate from both registration
and viewport preferences because these actions directly affect only manual
placement of `M`.

### Viewport controls

The upper-left vertical toolbar contains compact, labelled-symbol controls for:

* projection type (perspective / orthographic),
* grid visibility,
* point size, and
* point count budget (including automatic budget).

Each control has a Russian accessibility description and reflects its current
state in its compact text/symbol. These preferences no longer appear in the
stitching or M transform panel.

## Data flow and errors

Viewport buttons update the existing persisted project fields and redraw the
viewport. M transform buttons reuse the existing per-project M snapshot
storage. Registration validates that ready `R` and `M` targets exist before
starting, retains the existing candidate-approval flow for `Авто`, and reports
clear Russian status text for invalid inputs, no match, cancellation, and
failure.

## Verification

* Drag every gizmo handle and confirm only orange `M` scans move during the
  drag while R, grid, and camera frame remain visually stationary.
* Verify `Авто` calls global registration and `Уточнение` calls local
  refinement.
* Verify each control is in its intended location, toggles/cycles correctly,
  survives project reload, and has a content description.
* Build the debug APK and run relevant existing unit tests.
