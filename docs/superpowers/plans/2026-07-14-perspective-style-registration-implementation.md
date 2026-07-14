# Perspective-style registration implementation plan

Design: `docs/superpowers/specs/2026-07-14-perspective-style-registration-design.md`

## Phase 1 — strict decisions and rollback

1. Extend the native result with consistency, confidence, translation correction,
   and yaw correction.
2. Centralize quality evaluation and make overlap/RMS/P95 hard acceptance gates.
3. Keep automatic and local refinement decisions separate.
4. Preserve the exact input transform until the user applies a preview.
5. Add deterministic native tests for low overlap, excessive correction, and
   quality rejection.

## Phase 2 — bounded manual refinement

1. Add an explicit local-refinement configuration with fixed translation and yaw
   bounds around the user pose.
2. Stop refinement as soon as a bound is exceeded.
3. Compute reciprocal consistency and confidence after refinement.
4. Surface the richer metrics and stable Russian failure reasons in Android.

## Phase 3 — projection search

1. Add robust top-down occupancy/height projection grids.
2. Build coarse and fine grid levels from deterministic spatial samples.
3. Search broad yaw/XY candidates on the coarse grid.
4. Refine distinct candidates at one-degree/fine-cell resolution.
5. Estimate Z robustly and pass several candidates to local refinement.
6. Reject ambiguous or low-confidence candidates instead of returning a warning.

## Phase 4 — validated group graph

1. Insert only accepted high-confidence pairwise results as graph edges.
2. Derive edge weights from confidence, consistency, overlap, and residuals.
3. Validate connectivity, cycle residuals, and station displacement.
4. Preview and apply group transforms atomically.

## Phase 5 — real-data evaluation and delivery

1. Add a read-only reference-data extractor for the local TDX SQLite files.
2. Compare relative X/Y/Z/yaw against corresponding TZF pairs.
3. Exercise valid adjacent pairs and deliberately invalid non-neighbour pairs.
4. Build through CI, inspect the phone workflow, publish nightly, and verify the
   updater metadata.

TZF IMU metadata parsing remains a separate follow-up after its binary layout is
independently confirmed.
