# Perspective-style scan registration design

## Goal

Replace the current permissive global registration flow with a staged,
confidence-gated workflow derived from observable Trimble Perspective behavior.
Automatic registration must never silently move a scan when the match is weak.
When automatic registration is uncertain, the user keeps full manual control and
can run a bounded precision refinement from the manually supplied pose.

The implementation remains independent. Trimble binaries, code, and proprietary
assets are used only as behavioral references and are not linked, copied, or
redistributed with the Android application.

## Evidence and behavioral reference

Static inspection of the locally supplied Perspective installation shows a
multi-stage registration system rather than a single ICP pass:

1. TZF orientation and available IMU knowledge are prepared.
2. Coarse and fine template matching estimate horizontal translation and yaw.
3. Height/Z is estimated separately.
4. A fast pairwise refinement validates the preliminary result.
5. A full pairwise refinement produces the final transform.
6. Confidence, consistency, overlap, and error are evaluated independently.
7. Accepted pairwise links feed a separate overall/group refinement.

Observable modes include IMU, expert/local, full translation, full rotation,
Z-only, and adaptive rotation. Extracted behavioral constants include a strict
tilt-difference limit below 5 degrees, confidence boundaries at 73 and 91, a
one-degree fine angular step, and an IMU search range that expands with station
travel distance.

Two local TDX projects provide reference station transforms and link metrics for
eight real scans. Their accepted links have confidence around 99, consistency
100, and overlap between roughly 73 and 88 percent. They are the initial
real-data regression corpus.

## User workflow

### Automatic registration

The existing automatic registration command starts a staged coarse-to-fine
search. It does not change the stored transform while calculating. A result is
offered as a preview only when it clears every hard validation gate.

- High confidence: show the candidate preview and `Apply` / `Reject` actions.
- Insufficient confidence: reject the automatic candidate, restore the exact
  original pose, and instruct the user to align the moving scan manually.
- Cancellation or an error: restore the original pose and report the stage that
  stopped.

The first production gate is conservative: only high-confidence results,
equivalent to the observed upper confidence band of 91 or more, may be offered
as automatic candidates. The lower confidence band is diagnostic only and is
not automatically applied.

### Manual alignment and precise refinement

The user selects reference `R` and moving `M`, then positions `M` approximately
with the existing visible transform gizmo. Other scans and the camera frame
remain static while `M` moves.

Pressing `Refine` uses the current manual pose as a hard prior. The solver may
make only a bounded local correction. It must not jump to another similar room,
floor, facade, or repeated structural bay. A failed refinement leaves the
manual pose unchanged and explains whether the failure was caused by overlap,
geometry, excessive correction, or residual error.

A successful refinement is still a preview. `Apply` commits it; `Reject`
returns to the exact manual pose.

## Registration pipeline

### 1. Input preparation

Each scan contributes a spatially distributed registration sample independent
of the display point budget. Registration samples are bounded in memory and are
generated from the TZF stream. Invalid points and extreme isolated outliers are
discarded before matching.

The solver works in levelled four-degree-of-freedom space: X, Y, Z, and yaw.
Roll and pitch remain fixed unless a future TZF metadata parser proves that an
explicit correction is required. A detected tilt disagreement of 5 degrees or
more is a hard failure rather than an invitation to distort the scan.

### 2. Multi-resolution projection matching

Automatic registration builds deterministic top-down projection pyramids for
both clouds. Each cell records occupancy and robust height information. Empty
space, occupied structure, and height discontinuities contribute separately to
the score so that dense floors do not dominate walls and openings.

The coarse pass searches yaw and XY broadly. The fine pass searches around the
best distinct coarse hypotheses with a one-degree final angular step and a
smaller translation cell. Z is estimated after horizontal alignment from robust
floor/ceiling and matched-height statistics.

Search extent is adaptive:

- a valid manual pose uses a tight local search;
- a future valid TZF/IMU prior uses a distance-dependent search range;
- no prior uses the bounded global projection search.

The matcher keeps several geometrically distinct hypotheses. A result is
ambiguous when the runner-up is too close to the best score.

### 3. Local point-to-plane refinement

Each surviving preliminary hypothesis is refined from coarse to fine using
reciprocal point-to-plane correspondences, robust residual weighting, and
outlier trimming. The manual `Refine` command enters this stage directly using
the current pose as its only initial hypothesis.

Local correction limits are evaluated against the starting pose throughout the
solve, not only after convergence. A candidate exceeding translation or yaw
bounds is stopped and rejected immediately.

### 4. Independent quality metrics

Validation produces the following independent values:

- symmetric overlap from reference-to-moving and moving-to-reference matches;
- trimmed RMS and a high-percentile residual;
- consistency from reciprocal correspondences and agreement across pyramid
  levels;
- confidence from geometric support, score separation, overlap, consistency,
  and residual quality;
- correction magnitude relative to the supplied prior.

RMS or percentile thresholds are hard gates. The current behavior that marks a
threshold violation as `accepted=true` with only a warning is removed.

The UI reports the metrics but does not expose implementation-specific tuning
knobs in the normal workflow.

### 5. Group registration

Group registration uses only pairwise links that passed hard validation and
were accepted by the user. Edge weights derive from confidence, consistency,
overlap, and residual error. Weak, ambiguous, or failed pairs are not inserted
into the graph.

Before applying an optimized graph, cycle residuals and per-station motion are
validated. If the graph is disconnected or inconsistent, no station transforms
are changed. The entire group result is previewed and committed atomically.

## Architecture

The native registration code is split into focused units:

- `RegistrationProjection` builds projection pyramids and scores poses.
- `RegistrationSearch` owns coarse/fine hypothesis generation and ambiguity
  detection.
- `RegistrationRefiner` performs bounded point-to-plane refinement.
- `RegistrationQuality` computes metrics and the final decision.
- the existing pose-graph module consumes validated weighted edges only.

JNI returns a richer result containing the transform, decision, metrics,
correction magnitude, and a stable failure reason. Android orchestration maps
those reasons to concise Russian status messages and preserves the original
transform until explicit application.

TZF IMU/orientation extraction is a separate follow-up boundary. The new search
accepts an optional prior now, but the first implementation remains correct when
metadata is unavailable.

## Failure handling and invariants

- No solver path mutates persisted transforms directly.
- Reject, cancel, exception, Activity recreation, and low confidence restore the
  exact input pose.
- Non-finite coordinates or transforms fail safely.
- Automatic registration never degrades into an unbounded local ICP.
- Manual refinement never substitutes a different global hypothesis.
- A quality warning is not an accepted result.
- Group optimization is atomic and cannot partially move a project.

## Verification

Native deterministic tests cover projection scoring, distinct hypotheses,
confidence bands, reciprocal consistency, strict quality rejection, bounded
manual refinement, repeated geometry, low overlap, cancellation, and graph
inconsistency.

Real-data evaluation uses the two discovered TDX projects:

- extract their station transforms and link metrics from SQLite;
- run registration on the corresponding TZF pairs;
- compare relative X/Y/Z/yaw against the Perspective result;
- verify that accepted links are stable across repeated runs;
- include deliberately wrong and non-overlapping pairs to measure false
  acceptance.

Success requires zero transform changes on rejected cases, no false automatic
acceptance in the reference corpus, and a useful bounded refinement after a
reasonable manual placement. The final Android APK is visually exercised on the
phone workflow only, per the current device-testing requirement.

## Delivery stages

1. Make quality violations hard failures and guarantee transform rollback.
2. Add bounded manual point-to-plane refinement with richer metrics.
3. Add multi-resolution projection search and confidence gating.
4. Feed only validated links into group refinement and validate graph cycles.
5. Add optional TZF IMU/orientation metadata when its layout is independently
   confirmed.

Each stage is a separate commit and preserves the updater-visible nightly build
flow after the complete feature is ready.
