# Bug: registration after tripod height changes

**Type:** Bug Fix  
**Date:** 2026-10-12  
**Status:** Implemented; native QA passed; device verification pending

## What / Why
Manual placement aligns XY and yaw, but registration must also recover a different scanner height. The local projection seed searches only X, Y and yaw; Z is left to nearest-neighbour ICP. Global Z initialization uses mean heights inside XY columns, mixing floors and walls. Existing tests cover only a 20 mm initial Z error.

## How
First reproduce failure with otherwise aligned scans and positive/negative height errors. Add a bounded vertical occupancy correlation seed after XY seeding, retaining XYZ+yaw transforms, cancellation, final geometric validation and correction limits. Keep the implementation in a separate module.

## Acceptance
- Height differences of both signs are recovered on synthetic scenes with overlapping floor and walls, in metre and millimetre units.
- Correct initial poses and rejection of wrong XY placement remain intact.
- Cancellation and configured correction limits remain effective.
- Existing native tests pass. Real TZF/phone verification is reported separately until performed.

## Tasks / Progress
- [x] TSK-001: Reproduce height-change failure with native regression tests.
- [x] TSK-002: Implement and wire bounded vertical seeding if reproduction confirms it.
- [x] TSK-003: Run native tests and review diff; record evidence and remaining limits.

## Evidence
Baseline accepted +/-0.8 m height errors at Z~0 in both metres and millimetres: wall correspondences dominated and the displaced floor was trimmed out. The fix adds vertical occupancy correlation with equal XY-column weight before ICP: 200 mm XY cells, 50 mm height bins, +/-2 m search, support/gain thresholds and ambiguity rejection. Android and host CMake targets include the module. Windows tests close fixtures before deleting them.

Final QA after the ambiguity guard: CTest 2/2 passed in 45 seconds, with assertions enabled. Core tests and height regressions (+/-0.8 m, -0.47 m, +0.83 m, zero, density mismatch, cancellation and correction limits) pass. `git diff --check` is clean.

## Limits
APK/device and real scan-pair validation are pending. A real TZF header was read, but pair refinement from an unknown zero pose was stopped without a result. Search is +/-2 m around supplied Z; configured local correction limits still apply. Geometry without distinctive vertical structure remains ambiguous. Roll/pitch and a new acceptance gate for such geometry are outside this fix.

## Nightly publication follow-up
User requested publishing through nightly. Run 38260674457 failed before compilation: setup-android v3 defaults to `tools platform-tools`, but sdkmanager no longer finds `tools`. Explicitly request `platform-tools`, keep setup-android license acceptance, and remove the redundant `yes | sdkmanager --licenses` command. Acceptance: Android/JVM/native CI tests, signed APK and nightly manifest publication succeed; verify manifest version and APK checksum.
- [ ] TSK-004: Repair SDK package setup and verify nightly publication.
