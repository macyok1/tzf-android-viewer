# Gizmo capture, two-finger pan, and robust registration design

## Goal

Make manual movement unambiguous: the selected M object moves against a
stationary viewport while its gizmo is held. Add two-finger panning without
changing the camera angle. Replace the provisional normal-vote auto alignment
with the feature/correspondence pipeline used by established point-cloud tools.

## Gizmo capture

On pointer down inside the gizmo's working envelope, the renderer enters an
explicit gizmo gesture mode. The envelope covers the centre plane, axis
handles, rotation ring, and a small forgiving radius around them. Until the
pointer is released or cancelled, that gesture cannot become a camera orbit.

The camera frame, grid, and all reference clouds remain fixed during this
mode. Only the M node's transform and the scans below it change. A drag that
starts outside the gizmo remains a camera orbit.

## Two-finger pan and zoom

The two-finger gesture tracks both pinch scale and the midpoint displacement.
Pinch scale changes only zoom. Midpoint displacement pans the camera target in
the camera's screen-horizontal and screen-vertical world directions, without
changing yaw or pitch. Beginning a two-finger gesture cancels a gizmo drag and
preserves the current frame and orientation. The first later single-finger
move establishes a new origin and cannot cause a rotation jump.

## Registration

Auto registration follows the proven structure documented by PCL-style
registration pipelines:

1. voxel downsampling and normal estimation;
2. compact FPFH-compatible local geometric descriptors;
3. mutual nearest descriptor matching and distance/geometry rejection;
4. RANSAC generation and scoring of several 2.5D (X, Y, Z, RZ) pose
   hypotheses;
5. robust point-to-plane ICP refinement and final overlap/RMS/P95 validation.

The top candidate remains an explicit preview. Ambiguous hypotheses and weak
correspondence support are rejected instead of being attached to an arbitrary
side of a scan. Refinement uses the existing manual pose as a local prior.

## Verification

* Tests prove that a gizmo drag never changes yaw, pitch, camera pan, grid, or
  reference transforms, and that M changes.
* Gesture tests cover combined pinch+pan and a single-finger continuation.
* Native tests cover partial-overlap registration, correspondence rejection,
  repeated geometry ambiguity, and local refinement containment.
* A real local TZF pair is exercised before publishing; the resulting pose is
  checked against the visibly overlapping objects.
