# Registration calibration and phone rail implementation

Design: `docs/superpowers/specs/2026-07-14-registration-calibration-and-phone-rail-design.md`

1. Default new projects to AUTO while preserving decoded project budgets.
2. Replace save/fit rail actions with point size and point budget; retain removed
   actions in the tools popup.
3. Split reciprocal ratio from final consistency and calculate overlap balance,
   residual stability, and refinement stability.
4. Calibrate confidence without weakening RMS, P95, overlap, correction, or
   ambiguity gates.
5. Preserve the best rejected global candidate and return its concrete reason
   and metrics.
6. Extend native regression cases for density mismatch and strict rejection.
7. Run NDK syntax compilation without Gradle, push main, and verify nightly.
