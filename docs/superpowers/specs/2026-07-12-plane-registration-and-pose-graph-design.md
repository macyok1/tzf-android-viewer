# Plane registration and pose graph design

## Goal

Implement an independent scan-registration pipeline inspired by the observable behavior of Trimble RealWorks without linking, copying, or redistributing Trimble binaries. The implementation must register levelled TZF scans using `X/Y/Z + RZ`, preserve the existing UI contract, and reject unreliable or ambiguous results.

Development is split into two verifiable stages. Stage one makes pairwise registration reliable on real TZF scans. Stage two uses validated pairwise results to optimize groups of three or more stations.

## Constraints

- Keep the existing `registerConstrained` and `registerGlobalConstrained` entry points compatible.
- Never introduce `RX/RY`; scanner levelling remains authoritative.
- Remain portable C++20 with no mandatory PCL, Eigen, or platform-specific dependency.
- Do not mutate accepted project poses while computing or previewing a candidate.
- Support cancellation at every expensive phase.
- Bound memory and runtime for Android devices.

## Pairwise pipeline

### Spatial preparation

Build deterministic voxel samples from decoded XYZ points. Each occupied cell contributes a centroid and population count. Sampling must be independent of TZF point order and retain spatial coverage. Three resolutions derived from cloud extent provide coarse, medium, and fine representations.

### Plane extraction

Estimate local covariance from spatial neighbours and solve the symmetric 3x3 eigensystem. The eigenvector with the smallest eigenvalue is the normal. Reject neighbourhoods with too few points or weak planarity.

Grow plane regions through adjacent voxels when normal angle and point-to-plane distance are compatible. Refit each accepted plane from all member centroids. A plane descriptor contains normalized normal, signed offset, centroid, support count, area proxy, extent, and planarity score. Canonicalize normal direction so descriptors are deterministic.

### Plane matching and coarse hypotheses

Generate candidate plane matches using normal compatibility, support/area ratio, and relative spacing. For levelled scans, enumerate yaw candidates derived from dominant horizontal plane normals; horizontal planes constrain vertical translation. Construct `XYZ + RZ` hypotheses from compatible plane pairs or triples.

Cluster similar hypotheses in translation/yaw bins and retain a bounded number of distinct high-vote candidates. Score them cheaply on coarse point samples using symmetric truncated nearest-neighbour overlap. Repeated-wall or corridor solutions remain separate candidates rather than being merged.

If plane extraction cannot produce a usable hypothesis, retain the current normal-feature global search as a bounded fallback.

### Robust refinement

Refine each surviving candidate with coarse-to-fine point-to-plane ICP. Correspondences must be reciprocal where possible, have compatible normals, and satisfy a resolution-dependent distance gate. Trim the largest residuals and use a robust loss for the remaining equations.

Solve only four increments: `tx`, `ty`, `tz`, and `yaw`. Detect singular or ill-conditioned normal equations and return `degenerate geometry`. Stop on small pose increments, a stable objective, cancellation, or the iteration limit.

### Independent validation

Validate on deterministic samples not used to generate coarse hypotheses. Compute nearest-neighbour distances in both directions and report:

- symmetric overlap;
- RMS;
- P95;
- inlier count;
- conditioning;
- score separation from the second distinct hypothesis.

Insufficient overlap is a hard rejection. Poor RMS/P95 may remain an explicit warning under the existing UI contract. A second geometrically distinct candidate within the configured ambiguity ratio is a hard `ambiguous global hypotheses` rejection.

## Component boundaries

- `tzf_spatial_index`: voxel grid, bounded neighbour queries, deterministic sampling.
- `tzf_plane_extractor`: PCA normals, region growth, plane refitting and descriptors.
- `tzf_plane_matcher`: descriptor matching, pose voting, hypothesis clustering and coarse scoring.
- `tzf_registration`: public API orchestration, robust ICP, validation and fallback.
- `tzf_pose_graph`: stage-two graph model and optimizer.

Internal components expose small value types and have no Android/JNI dependency. `tzf_registration.h` remains the stable public pairwise interface; additional diagnostics are appended without changing existing fields.

## Multi-station pose graph

Each station is a vertex with an `XYZ + RZ` pose. Each accepted pairwise registration is an edge with relative transform, information weights derived from overlap/residual/conditioning, and diagnostics. One reference vertex is fixed.

Optimize the robust weighted residual between predicted and measured relative poses using iterative Gauss-Newton on four parameters per free vertex. Normalize yaw residuals to `[-180, 180)`. Apply a robust edge loss, identify persistently inconsistent edges, remove only edges whose removal does not disconnect a required station, and re-optimize once.

Return candidate world poses, per-edge residuals, connected components, and an overall confidence result. Do not apply poses automatically.

## Error handling

Every failure has a stable reason: insufficient data, insufficient spatial coverage, no plane hypothesis, insufficient overlap, degenerate geometry, divergence, ambiguity, disconnected graph, or cancellation. Allocation remains bounded by point, plane, hypothesis, and correspondence limits. Non-finite coordinates and transforms are rejected before optimization.

The existing normal-feature search is used only when plane-based coarse registration has no viable hypothesis; it does not override a detected ambiguous plane solution.

## Testing

Portable C++ tests cover:

- exact and noisy `XYZ + RZ` recovery;
- deterministic results under reordered and unequal-density inputs;
- partial overlap and outliers;
- floor/wall plane extraction;
- corridors and symmetric rooms producing ambiguity;
- degenerate single-plane geometry;
- fallback when planes are unavailable;
- cancellation in extraction, matching, refinement, and graph optimization;
- three-station chains and loops;
- a bad graph edge rejected without disconnecting valid stations.

Existing registration tests must continue to pass. The Android native build must compile all supported ABIs. Real TZF validation compares visual alignment, overlap, RMS/P95, runtime, and memory, but no claim of millimetre accuracy is made until TZF units and ground truth are independently confirmed.

## Delivery order

1. Spatial index and deterministic sampling.
2. PCA normal and plane extraction tests.
3. Plane matching and bounded hypothesis generation.
4. Reciprocal trimmed robust ICP and symmetric validation.
5. Integration behind the existing pairwise API and real-TZF diagnostics.
6. Pose graph model and optimizer.
7. Group-registration integration, Android build, and regression verification.

Each step leaves the repository buildable and preserves the current feature-search fallback until its replacement proves reliable.
