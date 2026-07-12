# Registration preview, pinch stability, and scan colors design

## Goal

Make registration useful for partially aligned scans without silently applying
weak results, preserve the selected camera view through a pinch gesture, and
give separately loaded scans clearly different base colors.

## Registration outcome

Both **Авто** and **Уточнение** continue to require enough points, finite
geometry, and the existing minimum overlap. A solution that meets those
geometric prerequisites is returned as a preview candidate even when its RMS
or P95 exceeds the preferred limit.

The pair card distinguishes two preview qualities:

* accepted quality: normal candidate wording;
* warning quality: amber warning including RMS, P95, and overlap, and explicit
  **Применить** and **Отклонить** actions.

Neither quality is written to the project until the user applies it. A result
with no viable geometric candidate still reports the specific failure reason.
The current numeric limits remain preferred-quality thresholds, rather than
hard rejection thresholds.

## Pinch gesture

Starting a two-finger gesture ends an active gizmo drag without fitting or
recentring the scene. During and immediately after pinch, one-finger move
events re-establish their touch origin before they may rotate the camera. This
prevents the stale pointer delta after a lifted finger from changing the
previously selected yaw and pitch. Pinch changes zoom only.

## Scan colors

New scans receive a randomly selected color from a fixed high-contrast base
palette. Colors already assigned to scans in the project are excluded until
the palette is exhausted; only then may colors repeat. The chosen ARGB color
is persisted in the existing scan model, so opening the project again keeps
the same visual identity.

## Verification

* Test a candidate whose overlap is adequate but RMS/P95 is above the
  preferred threshold: it must show an amber preview and require a decision.
* Test a candidate with insufficient overlap: it must remain rejected.
* Rotate the scene, pinch in and out, lift a finger, and continue rotating:
  yaw and pitch must not jump or reset.
* Add four scans and confirm that all four receive distinct colors from the
  base palette; reload the project and confirm the colors persist.
* Build the debug APK and run unit and native registration tests.
