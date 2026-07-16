# ASC and TZF export implementation plan

## Baseline and boundaries

1. Keep the existing user work in `MainActivity.java` untouched unless a
   change in the export flow needs the same line.  Do not stage `app/.cxx/`.
2. Verify the current JVM and native test commands before the change, then add
   focused regression tests beside the existing `AscExportMathTest`,
   `ProjectModelTest`, and `tests/tzf_core_tests.cpp` suites.

## 1. Large ASC import cache

1. Replace `AscPointCache.ensure(File source, File cache)` with an API that
   can stream a `content://` URI directly into an atomic compact XYZ cache.
   Store a stable source identity/metadata sufficient to reuse or invalidate
   the cache; never first duplicate the raw ASC in private cache.
2. Rework `AscPointCache.build` to write a placeholder header plus float
   triples into one temporary cache file, backfill its count/bounds header, and
   atomically publish it.  Delete the temporary cache on error or cancel.
3. Replace `AscLineParser`'s `String.split()` parsing with a buffered,
   allocation-light XYZ token parser.  Preserve comments and skipped-line
   accounting.
4. Give cache building a progress/cancellation callback.  Surface bytes read,
   valid points, cancellation, and inadequate-space errors through the existing
   asynchronous `MainActivity.decodeScene` status path.
5. Refactor `AscPointCloudSource`, `PointCloudSources`, and
   `MainActivity.openPointSource/decodeScene/localForAsc` so TZF still uses a
   local source file while ASC is opened from its direct compact cache.  Ensure
   only the display quota is uploaded to `PointCloudView`.

## 2. ASC export folder flow and exact count thinning

1. Add export request/domain types for output format, ASC thinning mode,
   destination tree, generated output name, and job status.  Keep UI code in
   `MainActivity` limited to dialogs, document-tree results, and status.
2. Replace the standalone `saveProject` and `exportAsc` bindings/layout IDs
   with one `Export` toolbar action.  Its first dialog chooses ASC or TZF.
3. For ASC, present `Without thinning` and `Thin`; then present exact count,
   percentage, or spacing-mm options.  Use `ACTION_OPEN_DOCUMENT_TREE`, create
   a generated `ProjectName_YYYYMMDD-HHMMSS.asc` name through `DocumentFile`,
   and add a numerical suffix on collisions.  No editable filename input is
   introduced.
4. Extract the streaming ASC scan traversal from `MainActivity.exportAsc`.
   It must apply each scan's world transform and a valid locked CUT in exactly
   the same order in every pass.
5. Implement an `ExactCountSelector`: pass one counts eligible points; pass
   two uses seeded sequential selection based on remaining target/remaining
   candidates.  It returns exactly `min(target, eligible)` points without a
   full-cloud allocation.  Percentage maps to an integer target and uses the
   same selector.  The spacing-mm voxel filter remains a separate
   geometry-driven mode.
6. Write export output to a unique `*.partial` document in the selected tree,
   then rename/publish only after flush.  Remove it on error or cancellation.
   For full export, display the estimated output size and require confirmation.
7. Diagnose the reported `outside bounds` separately: create fresh validated
   TZF sessions for every analysis/write pass (or explicitly rewind a session),
   add contextual scan/pass/tile information to errors, and add repeated-pass
   regression coverage.  Never suppress a true invalid TZF block range.

## 3. TZF pose writer and TZF export

1. In `tzf_core.h/.cpp`, add a native staged-file writer that copies a TZF,
   validates its headers/directory, and writes the registration record at the
   verified scan-info registration offset.  Convert app `[x, y, z, yaw]` world
   pose to finite little-endian 3x3 Z-rotation plus XYZ translation doubles.
2. Reopen the staged TZF with the existing parser, validate block ranges, and
   verify the pose read back from registration metadata before reporting
   success.  Do not recompress point tiles or otherwise alter TZF payloads.
3. Expose the writer through `tzf_reader.cpp`, `TzfNative`, and a focused Java
   `TzfExportCoordinator`.  The coordinator stages input/output in private
   storage, calls the native writer, and performs SAF copy/publish operations.
4. Build the TZF export dialog with two documented modes:
   - overwrite source: request necessary source-directory tree permissions,
     create a non-clobbering sibling `.tzf.bak`, then publish the verified
     patched source;
   - copy to folder: request one destination tree and create unique patched
     copies without modifying source bytes.
5. Filter export candidates to TZF sources.  Report any ASC project nodes as
   skipped, and fail safely if a selected TZF is missing, inaccessible, or
   lacks the permissions needed by the selected mode.

## 4. Project persistence and regression tests

1. Preserve the current `.tzfp` autosave behavior in `ProjectStore` and
   `ProjectCodec`; it remains an internal workspace record, not an exported
   transfer format.  Add/retain round-trip tests for scan URIs, node/world
   transforms, registration sets, and links.
2. Add JVM tests for generated ASC names, extension invariance, exact
   selectors at 5,000 and 5,000,000 targets, percentage conversion, locked
   CUT, target-above-source behavior, partial-output cleanup, and compact ASC
   cache reuse/cancellation with a streamed large fixture.
3. Add native tests for valid TZF registration patching and rereading,
   non-finite pose rejection, invalid registration offsets/block ranges, and
   source-byte preservation on a failed stage.  Add repeated session tests for
   the `outside bounds` path.
4. Add coordinator-level tests with temporary documents/files for copy versus
   overwrite semantics, including non-overwritten backups.

## 5. Verification and delivery

1. Run `git diff --check`, the JVM unit tests, CMake/CTest native tests, and
   the Android debug build.
2. On device, import a large ASC comparable to the reported 500 MB file;
   observe progress, cancel once, reopen after completion, and verify the
   cached reopen path.
3. Export 5,000 and 5,000,000 count targets and verify line counts.  Exercise
   the original thinning repro input until the contextual `outside bounds`
   failure is eliminated or the source is identified as corrupt.
4. Exercise ASC folder output, TZF copy output, and TZF overwrite/backup.
   Open the resulting TZFs in RealWorks and confirm all stations share the
   app-root stitched coordinates.
5. Review the final diff, commit only intended source/tests/docs, push the
   branch if requested, and report test evidence plus any RealWorks result.
