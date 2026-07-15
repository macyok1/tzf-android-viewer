# Point-cloud renderer rewrite implementation plan

1. **Lock behavioral contracts with tests.** Add projection-state, matrix-unprojection PAN, ASC parsing, source-type migration, LOD replacement/freeze, and flyout-state unit tests before changing runtime behavior.
2. **Introduce the source abstraction.** Add `PointCloudSource`, `PointCloudChunk`, `TzfPointCloudSource`, and a factory. Move TZF preview, registration sampling, metadata, and ASC export reads behind the abstraction without changing native matching.
3. **Implement ASC import and cache.** Add a streaming ASC parser, delimiter/layout detection, validation statistics, deterministic sampling, an app-private binary cache, source metadata persistence, and `+` picker support for `.tzf` and `.asc`.
4. **Replace implicit camera toggles.** Add authoritative `CameraState`, explicit projection assignment, orthographic defaults for new projects, exact manipulator save/restore, and inverse-view-projection screen PAN.
5. **Replace mutable renderer scene updates.** Introduce immutable station/snapshot descriptions, generation-based requests, stable metadata bounds, and frame-boundary scene swaps while preserving current draw/tool behavior.
6. **Add bounded progressive GPU uploads and atomic LOD.** Time-slice uploads, keep old LOD visible while replacement loads, avoid per-chunk reframing, cancel obsolete generations, and retain an ES2-compatible backend while enabling ES3 when available.
7. **Isolate registration from display LOD.** Freeze active scene geometry, frame, pivot, automatic budget changes, and candidate inputs for automatic/manual/refine operations; apply deferred visual updates only after completion or cancellation.
8. **Correct ASC export contract.** Use an extension-neutral MIME type, guarantee the `.asc` display name, stream mixed TZF/ASC sources, and preserve thinning plus locked-CUT world filtering.
9. **Rebuild the tool rail.** Use a wrap-content floating rail, move secondary controls to `⋯`, add anchored point-size and point-budget flyouts, selected states, dismissal behavior, and adaptive expanded-layout styling.
10. **Verify and release.** Run focused tests after each layer, then the complete JVM/native/debug/release suites, mixed-source emulator smoke, projection/PAN and registration-under-LOD smoke, push `main`, watch Actions, and verify nightly `update.json` plus APK SHA-256.

Implementation is staged so each source/camera/renderer layer remains buildable. The native registration solver and centroid-pivot conversion are protected from algorithmic changes throughout.
