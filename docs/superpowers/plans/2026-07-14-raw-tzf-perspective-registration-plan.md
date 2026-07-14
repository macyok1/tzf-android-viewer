# Raw TZF Perspective-style registration implementation plan

1. Reject equal zero embedded poses as registration priors while retaining
   their leveling matrices.
2. Add native compact cylindrical structural descriptors derived from sampled
   levelled TZF points, with cyclic yaw scoring and separated peak selection.
3. Feed yaw peaks into an adaptive 3–12 m coarse translation search that scores
   occupancy, height structure, and symmetric support; estimate Z separately.
4. Split candidate refinement into fast coarse and full multi-resolution
   point-to-plane stages. Preserve the input transform on every rejection.
5. Replace the unconditional 3 mm decision with bounded resolution-aware
   residual limits and explicit complete/check/reject confidence bands.
6. Use embedded/manual placement only when it is non-degenerate; otherwise run
   the structured global pipeline. Apply the same candidate generation to
   group links before pose-graph refinement.
7. Add deterministic native regression cases for zero priors, cyclic yaw,
   repeated geometry, adaptive translation, Z, and rollback.
8. Calibrate against stations 1 and 2 in the selected nine-station raw X7
   object, then inspect adjacent and non-adjacent pairs for false positives.
9. Run offline native compilation only, push `main`, and require successful
   GitHub tests, signed APK publication, and updater manifest verification.
