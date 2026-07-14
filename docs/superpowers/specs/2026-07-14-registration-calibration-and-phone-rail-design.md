# Registration calibration and phone rail design

## Goal

Stop valid scan pairs from collapsing into the generic `no global hypothesis`
failure while preserving strict rejection of genuinely ambiguous alignment.
At the same time, make the phone rail prioritize the two display controls used
during dense-cloud inspection.

## Phone rail

The compact phone rail contains, in order: X7, add TZF, scans/groups, clipping,
point size, point budget, and tools. Quick save and fit/home are removed from the
rail. Save remains available through the project workflow and fit remains
available from the tools menu if needed.

Point budget continues to cycle through `AUTO`, `150K`, `1M`, `10M`, and `ALL`.
New projects start at `AUTO`. A value explicitly chosen by the user is persisted
with that project and is restored when the project is reopened. Existing
projects keep their stored value.

## Registration consistency

Reciprocal point-to-plane correspondences remain an input to refinement because
they reduce unstable many-to-one equations. The final `consistency` decision is
no longer the raw percentage of exact reciprocal sample indexes. Independent
spatial sampling, occlusion, and density differences make that percentage low
even for a valid pair.

Final consistency combines:

- agreement between coarse and fine transforms;
- symmetric forward/backward overlap balance;
- stability of trimmed residual statistics;
- the reciprocal correspondence ratio as a diagnostic component, not a lone
  hard gate.

RMS, P95, minimum symmetric overlap, correction bounds, and ambiguity remain
hard gates. Confidence is calibrated from the independent values and score
separation. It must not be increased merely by lowering a threshold.

## Failure reporting

Global search retains the best rejected candidate and its metrics. If no
candidate passes, JNI returns the strongest concrete reason and diagnostic
values instead of replacing everything with `no global hypothesis`. Android
shows the reason in Russian and tells the user when manual placement followed by
`Refine` is the appropriate path.

Rejected automatic and local results always return the exact input transform.

## Verification

- deterministic native cases cover density mismatch, partial overlap, repeated
  geometry, strict RMS/P95 rejection, and ambiguity;
- the known TDX pair is used to calibrate overlap and consistency behavior;
- Android C++ and JNI sources receive offline syntax compilation;
- GitHub CI builds the updater-visible release;
- no emulator run is required because the local AVD is unstable and unusably
  slow on this machine.
