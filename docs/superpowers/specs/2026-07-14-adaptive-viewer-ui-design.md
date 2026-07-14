# Adaptive viewer UI design

## Goal

Use the available screen effectively on phones and tablets, make primary actions understandable, and preserve the full point-cloud viewport. Fix the issues verified on 1080×2400 phone and 2560×1600 tablet emulators without a high-risk Compose migration.

## Scope and approach

The implementation stays on Android Views and uses resource-qualified XML (`layout` and `layout-sw600dp`) plus small runtime behavior changes. The manifest no longer forces portrait orientation. A future Compose and Navigation 3 migration remains separate work.

## Projects screen

On phones, the app bar contains the title and settings. `Create project` becomes the clear primary action below the app bar and is repeated in the empty state. Existing projects remain vertical cards.

On tablets, the background fills the window and a centered content column has a bounded readable width. The project list and actions never stretch across the full landscape display. The same IDs and activity behavior are preserved in both layouts.

## Viewer on phones

The persistent rail contains only seven primary actions: X7, add TZF, save project, fit view, scans, clipping, and tools. Secondary actions move into the tools popup with explicit Russian labels: ASC export, measurement, projection, grid, point size, point budget, transform controls, and stitching actions.

The app bar uses the project title plus a compact `Tools` entry rather than a large `STITCHING` badge. The orientation cube is smaller and inset far enough to keep every face label visible. The R/M card is hidden until at least one scan exists and otherwise uses reduced height.

## Viewer on tablets

The `sw600dp` layout uses a wider labeled navigation rail at the left, a central viewport, and right-side supporting panels for the scan tree and transform controls. Supporting panels overlay only the right edge and never become full-width bottom sheets. The R/M card spans only the usable viewport area.

Touch targets remain at least 48dp. Text labels replace ambiguous glyph-only controls where space permits. Phone and tablet layouts expose the same view IDs so `MainActivity` shares behavior.

## Runtime adaptation

`MainActivity` detects the `sw600dp` resource boolean. Overlay sizing uses bottom sheets on compact width and right supporting panes on expanded width. Tool visibility and labels are configured once after view binding. Scan-count changes update the empty-scene state and R/M card visibility.

`ProjectsActivity` continues to build cards programmatically but applies a bounded column supplied by resources. Dialogs and data behavior do not change.

## Visual direction

The existing dark navy, cyan, amber, and white palette remains. Hierarchy comes from spacing, grouping, and text rather than extra decoration. Primary actions use cyan emphasis, destructive actions retain red, and secondary controls use the raised panel color. Empty states tell the user what action to take.

## Accessibility and failure handling

Every icon-only phone control keeps a meaningful content description. Labeled tablet controls use direct action names. Long labels ellipsize instead of overlapping. Small-window and landscape-phone configurations fall back to the compact layout rather than clipping.

## Verification

Build the APK offline, install it on the existing API 35 phone and tablet AVDs, and capture projects, empty viewer, populated viewer when a fixture is available, tools menu, scan panel, and transform panel. Verify no letterboxing, cropped cube labels, overlapping system bars, or sub-48dp targets. Push only after the second screenshot review.

