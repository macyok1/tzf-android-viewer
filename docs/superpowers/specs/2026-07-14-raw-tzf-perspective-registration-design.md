# Raw TZF Perspective-style registration design

## Goal

Register raw Trimble X7 stations on Android without depending on embedded XYZ
or yaw. Reproduce the observable BlackLion registration stages closely enough
to handle outdoor, repetitive, and partially overlapping scans while keeping
manual placement available as a reliable starting hypothesis.

The calibration object is:

`C:\Users\PC\Desktop\СКАНЫ ВВО\2025-08-04_13-47-28_Проект 054`

It contains nine raw stations. Every TZF registration block has zero XYZ and
zero yaw, while the orientation matrix contains only leveling information.
Those zero poses are therefore not a registration prior.

## Confirmed Perspective pipeline

Static inspection of `Trimble.BlackLion.Registration.dll` confirms separate
operations for TZF orientation, internal template matching, pre-registration,
fast pairwise refinement, full pairwise refinement, result metrics, and overall
group refinement. The relevant exported operations include:

- `TZFOrientation::Align`;
- `LaunchPreRegistration` and `PreRegistration::Compute`;
- `SetAnOtherResearchSpace`;
- `PairWiseFastRefine` and `PairWiseRefine`;
- `GetOverlap`, `GetRMS`, `GetConfidence`, and `GetConsistency`;
- `Overall::Refine`.

The Android implementation will reproduce these responsibilities with
independent native components. It will not call or redistribute proprietary
Windows binaries.

## Pose-prior validity

An embedded pose is useful only when it establishes a non-degenerate relative
placement. For two different scans, equal XYZ and yaw within small numerical
tolerances are treated as an unavailable prior. Leveling remains valid and is
applied independently.

The application must not jump directly to constrained ICP from a degenerate
prior. It proceeds through global pre-registration instead. Existing projects
and manual transforms remain unchanged until a candidate is accepted.

## Stage 1: structured spherical representation

TZF is a structured spherical raster, so pre-registration must preserve that
structure rather than immediately reducing the scan to an unordered point
sample. The decoder produces a compact multi-resolution representation with:

- range;
- intensity;
- validity mask;
- range-edge and intensity-gradient channels;
- a vertical structural profile for every azimuth column.

Sky and missing returns are masked. Broad illumination changes are removed
from intensity before matching. Representations are bounded in size and cached
per local TZF so repeated registration does not decode the scan again.

## Stage 2: yaw candidates

The first global search is cyclic because an X7 panorama wraps at 360 degrees.
Coarse circular correlation compares range edges, intensity gradients, and
vertical profiles. The best separated peaks are retained instead of choosing a
single yaw prematurely. Each peak is refined at one-degree resolution.

IMU leveling constrains roll and pitch but does not supply yaw. Repetitive
geometry is handled by preserving multiple yaw peaks and deferring the decision
to geometric verification.

## Stage 3: translation and Z

For each yaw candidate, XY is searched coarse-to-fine using structural cells
from the levelled point cloud. The search begins near 3 m and can expand to
12 m when no confident hypothesis is found. Cell scoring combines occupancy,
height range, vertical structure, and symmetric support so flat ground cannot
dominate the result.

Z is estimated separately from compatible horizontal surfaces and robust
height differences. If no stable Z estimate exists, that candidate is rejected
rather than allowing XY scoring to hide a vertical error.

## Stage 4: fast and full pairwise refinement

Candidates first receive a cheap fast refinement on a coarse sample. Only the
best geometrically distinct candidates continue to full point-to-plane
refinement. Full refinement progresses through coarse, medium, and fine voxel
levels and uses trimmed robust residuals with reciprocal support.

The solver remains four degrees of freedom after leveling: XYZ and yaw. Manual
placement follows the same refinement stages but uses a bounded search around
the user's transform.

## Quality decision

Acceptance is not a fixed `RMS <= 3 mm` decision. The final result combines:

- symmetric overlap;
- trimmed RMS and P95;
- forward/backward balance;
- stability between refinement levels;
- separation from the next-best hypothesis;
- correction magnitude relative to the selected prior.

The residual allowance is derived from scan sampling, range, and the final
voxel level, with explicit minimum and maximum bounds. Low-quality results are
reported as `CHECK REGISTRATION` candidates when the geometry is plausible but
confidence is insufficient. Clearly bad or ambiguous results remain rejected.
The exact pre-registration transform is preserved on rejection.

## Group registration

Acquisition order supplies candidate neighboring links. Each scan is matched
first against the preceding station and then against other stations whose
coarse representations suggest overlap. Only accepted pairwise links enter the
pose graph.

Overall refinement computes per-station overlap thresholds, retains sufficiently
supported links, fixes one station, and optimizes the registration set. A
disconnected graph is reported explicitly and no partial group transform is
committed.

## Android integration and performance

Native code owns structured decoding, descriptor generation, hypothesis search,
refinement, and diagnostics. Java owns cancellation, progress stages, candidate
preview, and persistence.

Work is bounded for a phone:

- compact structured representations;
- coarse search before dense decoding;
- capped point samples for fast refinement;
- denser data only for final candidates;
- descriptor caching;
- cancellation checks inside every large loop.

The existing progress UI reports panorama preparation, yaw search, translation
search, fast refinement, full refinement, and validation.

## Failure behavior

- zero/equal embedded poses: ignore the prior and run pre-registration;
- insufficient structured overlap: report that no panorama hypothesis exists;
- ambiguous repeated structures: retain manual placement and report ambiguity;
- missing stable Z: reject the candidate with a Z-specific reason;
- cancellation: return the exact input transforms;
- memory pressure: reduce representation/sample density before failing;
- group disconnection: do not apply a partial graph.

## Verification

The selected nine-scan object is used as the primary raw-data fixture without
copying or modifying its files. Initial calibration uses stations 1 and 2,
whose intensity panoramas show several common tanks, road surface, and terrain.
Additional adjacent and non-adjacent pairs measure success and false-positive
rejection.

Deterministic native tests cover cyclic yaw wraparound, repeated yaw peaks,
translation expansion, separate Z estimation, zero-prior rejection, ambiguous
geometry, multi-resolution convergence, and transform rollback. Offline NDK
syntax compilation is run locally. Gradle and the emulator are not run locally;
GitHub CI builds and publishes the updater-visible release.
