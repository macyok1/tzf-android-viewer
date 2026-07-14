# Embedded TZF pose implementation plan

1. Add a native `PreviewSession` accessor that converts validated registration
   metadata to `[x, y, z, yaw]`, and expose it through JNI and
   `NativePreviewSession`.
2. Extend `ProjectModel.Scan` with persisted embedded-pose validity,
   one-time-application state, and four pose values. Add isolated helpers for
   relative-pose calculation and safe new-scan placement.
3. Upgrade `ProjectCodec` to format version 4. Read versions 1-3 without moving
   existing scans, and round-trip the new fields for version 4.
4. In the existing asynchronous scan decode/import path, read metadata and
   initialize a newly added scan before it is first displayed. Never overwrite
   an existing/manual transform and persist any initialization change.
5. Add focused native and JVM-side regression coverage where the repository's
   current test structure permits it.
6. Run `git diff --check` and offline NDK syntax compilation only. Do not run
   Gradle or the emulator.
7. Increment the updater-visible app version, commit, push `main`, wait for
   GitHub Actions, inspect failures if any, and verify `update.json` points to
   the new build.
