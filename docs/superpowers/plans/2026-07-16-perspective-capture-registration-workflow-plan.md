# Perspective capture and registration workflow — implementation plan

Design: `docs/superpowers/specs/2026-07-15-perspective-capture-registration-workflow-design.md`

## Working rules

- Preserve the working native registration and centroid-pivot fix.
- Implement in small commits; every phase must leave the APK buildable.
- Write focused JVM tests before each domain change.
- Never commit `app/.cxx/`, generated APKs, test fixtures, credentials, or visual-companion files.
- Do not push/nightly until the complete real-data regression passes.

## Phase 0 — baseline

1. Confirm `main` starts at the approved design commit and only `app/.cxx/` is untracked.
2. Record baseline results:

   ```powershell
   $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
   ```

3. Run the existing portable/native registration test binary on the x86_64 AVD and retain the known TDX `2→3` result for later comparison.

## Phase 1 — PAN/ORBIT camera interaction

### Tests first

Files:

- create `app/src/main/java/ru/tzfviewer/CameraGesturePolicy.java`
- create `app/src/test/java/ru/tzfviewer/CameraGesturePolicyTest.java`

Add pure-Java tests for:

- default mode is `PAN`;
- a free one-pointer drag maps to `PAN` or `ORBIT` according to the active mode;
- gizmo, clip, measure, and manual-registration gestures override camera actions;
- entering/leaving manual mode does not persist ORBIT;
- a two-pointer gesture maps only to zoom.

Run the targeted failing test before implementation.

### Implementation

Files:

- modify `app/src/main/java/ru/tzfviewer/PointCloudView.java`
- modify `app/src/main/java/ru/tzfviewer/MainActivity.java`
- modify `app/src/main/res/layout/activity_main.xml`
- modify `app/src/main/res/values/strings.xml`
- modify `app/src/main/res/values/colors.xml` or `styles.xml` only if the existing palette cannot express active/inactive tint

Steps:

1. Add explicit `PAN`/`ORBIT` state to `PointCloudView`; initialize it to PAN.
2. Preserve current `beginGesture` priority for gizmo and clipping.
3. In a free one-pointer move, call `panByPixels(dx,dy)` in PAN and the existing yaw/pitch update in ORBIT.
4. Remove the current two-finger midpoint call to `panByPixels`; retain `ScaleGestureDetector` zoom.
5. Add a 48 dp right-side `↻` button, bind it in `MainActivity`, update tint/description, and hide it while top-down manual mode is active.
6. Add a small package-private state accessor only if needed by tests; do not expose renderer internals publicly.
7. Run camera policy tests, the full JVM suite, and build debug APK.

Commit checkpoint: `Add pan and orbit camera modes`.

## Phase 2 — project v5 registration records

### Tests first

Files:

- extend `app/src/test/java/ru/tzfviewer/ProjectModelTest.java`
- create `app/src/test/java/ru/tzfviewer/RegistrationGraphTest.java`

Add failing cases for:

- every new scan receives exactly one registration set;
- v5 codec round-trips sets, links, station state, attempted reference, direct CHECK candidate, metrics, and acquisition time;
- v1–v4 projects preserve every world transform and migrate each scan to a deterministic separate `LEGACY_UNLINKED` set;
- malformed duplicate membership, missing station IDs, invalid transforms, and non-finite metrics are rejected;
- deleted stations cannot remain in set membership or links.

### Model and codec

Files:

- modify `app/src/main/java/ru/tzfviewer/ProjectModel.java`
- modify `app/src/main/java/ru/tzfviewer/ProjectCodec.java`
- create `app/src/main/java/ru/tzfviewer/RegistrationGraph.java`
- create `app/src/main/java/ru/tzfviewer/RegistrationPoseMath.java`

Steps:

1. Raise `ProjectModel.FORMAT_VERSION` to 5.
2. Add explicit `RegistrationSet`, `RegistrationLink`, `RegistrationState`, persisted attempt metrics, `pendingCandidateWorld[4]`, and `acquiredAt`.
3. Store sets and links in deterministic insertion order. The member list in `RegistrationSet` is the only persisted source of membership; do not duplicate a mutable set ID on `Scan`.
4. Add codec records for sets and links and v5 scan fields. Continue reading versions 1–4.
5. Migrate legacy scans with UUIDs deterministically derived from project ID + scan ID; never synthesize links. Derive missing `acquiredAt` values from legacy tree order so LAST remains deterministic.
6. Validate one-set-per-scan and referential integrity after decoding before returning the project.
7. Make `rememberScan` create a set and acquisition timestamp through `RegistrationGraph`, rather than directly appending an untracked scan.
8. Keep old persisted R/M fields readable during migration, but v5 writes them empty; manual-session roles will be runtime-only.

Run model/graph tests and full JVM suite.

Commit checkpoint: `Add registration sets and links to project v5`.

## Phase 3 — rigid set operations

### Tests first

Extend `RegistrationGraphTest` with:

- `1+2`, `12+3`, `4+5`, and `123+45` merges;
- reference set remains fixed;
- correction is `candidateMovingWorld ∘ inverse(oldMovingWorld)`;
- every moving member receives the same rigid correction;
- relative transforms inside the moving set remain unchanged;
- scans inside unrelated user folders keep their parent and correct local transform;
- merge is rejected when R/M are in the same set;
- rejected/CHECK results do not mutate transforms, sets, or links;
- accepted result creates one directed link with exact metrics/source.

### Implementation

Files:

- modify `RegistrationGraph.java`
- modify `RegistrationPoseMath.java`
- extend `ProjectModel.java` only for narrow invariant helpers

Steps:

1. Implement world-pose compose, inverse, relative, and correction helpers using the existing XYZ+RZ convention.
2. Implement atomic `acceptRegistration(referenceScan,movingScan,candidateWorld,metrics,source)`.
3. Calculate all new world transforms before mutating the model; validate finiteness and membership first.
4. Convert each calculated world transform back to the scan's existing user-folder parent.
5. Move members into the reference registration set, remove the empty set, append the link, and update station statuses only after all validation succeeds.
6. Implement station deletion cleanup and dynamic `lastAcquiredScan`/eligible-target queries.

Run targeted graph tests, full JVM suite, and debug APK.

Commit checkpoint: `Implement rigid registration set merging`.

## Phase 4 — registration state machine

### Tests first

Files:

- create `app/src/main/java/ru/tzfviewer/RegistrationWorkflow.java`
- create `app/src/main/java/ru/tzfviewer/RegistrationEngine.java`
- create `app/src/test/java/ru/tzfviewer/RegistrationWorkflowTest.java`

Use a fake `RegistrationEngine` and test:

- first captured scan skips registration;
- dynamic `LAST` resolves once at capture start and remains fixed;
- explicit older target remains fixed;
- download-ready transition creates `PENDING` before native work;
- accepted result invokes one graph merge;
- `check registration` stores direct candidate + metrics without applying it;
- confirm CHECK applies exactly that stored candidate; reject CHECK becomes FAILED;
- native rejection becomes FAILED and does not enter manual mode;
- stale generation/cancelled completion cannot mutate the project;
- persisted runtime REGISTERING restores as PENDING;
- missing reference produces actionable FAILED state;
- retry is explicit and bounded.

### Implementation

Files:

- implement `RegistrationWorkflow.java`
- implement `RegistrationEngine.java`
- create `app/src/main/java/ru/tzfviewer/NativeRegistrationEngine.java`
- modify `MainActivity.java` only to bridge worker/UI callbacks

Steps:

1. Model immutable capture tickets containing resolved target ID and generation.
2. Keep orchestration free of Android views and networking.
3. Wrap `TzfNative.registerPointClouds*` behind `RegistrationEngine` and centralize centroid-pivot conversion there.
4. Publish immutable workflow UI states for PENDING, RUNNING, CHECK, FAILED, and COMPLETE.
5. Persist project state before starting native work and after a complete transition.

Run workflow tests and full JVM suite.

Commit checkpoint: `Add registration workflow state machine`.

## Phase 5 — station panel and registration-set view

### Tests first

Files:

- create `app/src/main/java/ru/tzfviewer/RegistrationTreeModel.java`
- create `app/src/test/java/ru/tzfviewer/RegistrationTreeModelTest.java`

Test pure row generation for:

- existing folder hierarchy unchanged;
- set order and member acquisition order;
- green REGISTERED, orange CHECK, red FAILED, gray UNLINKED/LEGACY states;
- FAILED exposes `MANUAL_REGISTER`; CHECK exposes accept/reject;
- registration view never exposes persistent R/M role buttons.

### UI implementation

Files:

- modify `app/src/main/java/ru/tzfviewer/ScanTreePanel.java`
- modify `app/src/main/java/ru/tzfviewer/MainActivity.java`
- modify `app/src/main/res/layout/activity_main.xml`
- modify strings/colors as required

Steps:

1. Add `Папки | Наборы сшивки` selector to the scan panel.
2. Render folder rows through existing behavior and set rows through `RegistrationTreeModel`.
3. Remove ordinary R/M buttons from station rows.
4. Add status suffix/color and contextual actions `Проверить`, `Повторить`, and `Сшить вручную`.
5. Hide the persistent pair card and ordinary manipulator actions outside a manual session.
6. Keep rename/move/delete for user folders; route scan/group deletion through `RegistrationGraph` so membership and links are cleaned atomically. Registration-set rows are read-only in this release.

Run tree-model tests, full JVM suite, and inspect phone/tablet layouts.

Commit checkpoint: `Show Perspective-style registration sets`.

## Phase 6 — X7 target selector and post-download registration

### Tests first

Files:

- create `app/src/main/java/ru/tzfviewer/X7CaptureRegistrationCoordinator.java`
- create `app/src/test/java/ru/tzfviewer/X7CaptureRegistrationCoordinatorTest.java`

With fake capture/download and registration callbacks, test:

- no selector target for the first scan;
- default label tracks the latest downloaded station;
- capture snapshots the target before scanner work starts;
- only `Новый скан` capture triggers auto-registration;
- manual `Загрузить скан` and local file import only create UNLINKED stations;
- decode-ready event starts registration once;
- scan/download failure never creates a station or registration attempt;
- registration failure keeps the downloaded scan and permits the next capture.

### Integration

Files:

- modify `MainActivity.java`
- modify `TrimbleX7Client.java` only if a narrow injectable gateway boundary is required
- modify `activity_main.xml`, strings, and adaptive dimensions

Steps:

1. Add `Сшить с` control next to the X7 start action and refresh it whenever scans are added/deleted.
2. Resolve LAST/explicit target immediately before `runX7Scan` and create a capture ticket.
3. Pass the ticket only through the new-scan download path; keep bulk download/preview paths ticket-free.
4. Add a scan-ready callback to the asynchronous `decodeScene` path.
5. On ready, feed the capture coordinator result into `RegistrationWorkflow` and schedule it on the existing single worker without blocking UI or duplicating decode.
6. Display success, CHECK, and FAILED independently from X7 scan/download success.
7. Ensure a FAILED scan becomes the next dynamic LAST target, matching Perspective chain behavior.

Run coordinator tests, full JVM suite, and debug APK.

Commit checkpoint: `Auto-register newly captured X7 scans`.

## Phase 7 — transactional manual registration

### Tests first

Files:

- create `app/src/main/java/ru/tzfviewer/ManualRegistrationSession.java`
- create `app/src/test/java/ru/tzfviewer/ManualRegistrationSessionTest.java`

Test:

- R defaults to attempted reference or previous station in another set;
- M represents the complete registration set;
- same-set R/M is rejected;
- preview drag applies rigid correction to temporary poses only;
- switching R/M resets/snapshots predictably;
- Cancel restores every original world pose;
- failed local refinement retains the user's manual preview but no persisted mutation;
- Apply commits one graph merge/link and closes the session;
- stale callback cannot commit after Cancel.

### Implementation

Files:

- implement `ManualRegistrationSession.java`
- modify `MainActivity.java`
- modify `PointCloudView.java`
- modify `ScanTreePanel.java`
- modify `RegistrationUiState.java`
- modify `activity_main.xml`

Steps:

1. Enter manual mode only from the contextual action.
2. Build temporary R/M dropdowns; initialize M to the selected station's set.
3. Snapshot all M world poses and render temporary corrections without writing `ProjectModel` during drag.
4. Fix the camera to orthographic top view, hide ORBIT, keep pinch zoom, and use only XY marker + RZ ring.
5. Run constrained local registration from the temporary manual pose through `RegistrationEngine`.
6. On accepted candidate, show metrics and explicit Apply/Cancel.
7. Apply through `RegistrationGraph.acceptRegistration`; Cancel/exit restores the snapshot.
8. Remove obsolete persistent-role/manipulator entry points and unreachable old group-registration UI, while retaining native pose-graph code for future work.

Run manual-session tests, full JVM suite, and debug APK.

Commit checkpoint: `Make manual registration transactional`.

## Phase 8 — regression and real-device verification

1. Run all JVM tests and assemble debug:

   ```powershell
   $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
   ```

2. Build/push portable native tests with NDK 26.3 and run on the x86_64 AVD.
3. Re-run real registration fixtures:

   - TDX `2→3` exact and manually offset;
   - TDX `3→4` and `4→5` diagnostics;
   - deliberately wrong manual pose must reject and roll back;
   - raw identical-reservoir pair must never receive a hidden post-native pivot shift.

4. Exercise the UI on phone and tablet:

   - PAN default, ORBIT tint, pinch-only zoom;
   - first/second/failed/continued X7 scan states;
   - folder/set tree switch;
   - CHECK accept/reject;
   - manual Cancel and Apply for a multi-scan moving set.

5. On a real X7, verify scan → task completion → FTP download → decode → auto registration, then repeat with an intentionally bad-overlap station and continue scanning.
6. Run `git diff --check`, inspect tracked files, and ensure no fixture/build/cache files are staged.

## Phase 9 — publication

1. Commit any final verification-only fixes in focused commits.
2. Push `main` only after Phase 8 passes.
3. Watch GitHub Actions through JVM tests, portable C++, signed release, and nightly upload.
4. Download published `update.json`, verify new version, APK URL, SHA-256, and release asset digest.
5. Report the exact nightly version and the real X7/manual scenarios that passed.
