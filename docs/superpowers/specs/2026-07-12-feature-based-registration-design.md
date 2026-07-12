# Feature-based registration design

## Goal

Replace the current centre-and-yaw scan heuristic with a reliable offline
alignment pipeline. It must find a useful initial pose for scans that start
apart, then refine it, without letting local refinement jump from a manually
aligned object to unrelated repeated geometry.

## Scope and coordinate model

The viewer's transform model remains translation plus Z-axis rotation. The
pipeline is therefore a 2.5D registration process: it estimates X, Y, Z, and
RZ in the existing project coordinate system. No cloud service or external
runtime dependency is introduced.

## Auto mode

1. Build a bounded, voxel-downsampled representation of R and M.
2. Compute local geometric descriptors from neighbourhood shape and normal
   orientation.
3. Match mutually compatible descriptors between clouds.
4. Form several RANSAC-style transform hypotheses from compatible pairs,
   deduplicate them, and score each against the actual point clouds using
   overlap, RMS, P95, and inlier count.
5. Refine the best hypotheses with robust ICP and present the best valid
   candidate for an explicit apply/reject decision.

The UI reports the stages as: **признаки**, **гипотезы**, **проверка**, and
**уточнение**. It remains cancellable and may run for 10–60 seconds on a pair.

## Refinement mode

Refinement starts from the current M world pose. Correspondence search is
gated around that pose, and each iteration is guarded against a large
translation or RZ jump from the starting position. A candidate outside the
local correction envelope is rejected as a lost local alignment, rather than
being shown as a misleading result.

## Candidate selection and errors

Automatic mode can retain up to three distinct high-scoring hypotheses
internally, but presents the top one first. It is rejected when inlier support
or overlap is insufficient, the top poses are materially ambiguous, or no
candidate survives local refinement. Warning-quality candidates remain
explicit previews and never update the project until applied.

## Verification

* Add deterministic native tests for a translated/rotated partial-overlap
  pair, a repeated-geometry ambiguity, and a refinement case that must reject
  a far jump.
* Exercise at least one local TZF pair that currently reproduces the reported
  failure, recording candidate metrics and confirming the applied pose stays
  at the visible overlap.
* Build the Android APK and run Java plus native regression tests.
