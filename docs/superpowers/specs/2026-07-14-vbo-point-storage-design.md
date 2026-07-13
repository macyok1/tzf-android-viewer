# GPU VBO point storage design

## Goal

Display every requested point in `ALL` while preventing the complete cloud from being retained in Android process RAM. Keep bounded CPU data for picking, measurement, clipping statistics, and recovery diagnostics.

## Chosen approach

Each native decoder chunk is transferred to the GL thread and uploaded into an OpenGL ES 2.0 vertex buffer object (VBO). After upload, the Java float array and temporary direct buffer become collectible. A scene cloud retains only VBO identifiers, point counts, transforms, colors, bounds, and a bounded CPU sample.

The CPU sample contains at most 200,000 spatially distributed points per scan. Measurement, long-press clipping recentering, and percentile clipping use this sample. Rendering uses every point stored in the VBOs.

## Components

`GpuPointChunk` owns one GL buffer identifier and point count. It creates and deletes the buffer only on the GL thread.

`SceneCloud` owns its GPU chunks and a bounded reservoir sample. Reset, removal, project reload, and view destruction delete all corresponding buffers. Bounds are calculated incrementally while chunks arrive.

`PointCloudView` accepts ownership of decoder chunks without cloning them. The queued GL operation uploads the chunk immediately and then releases the Java reference. A small in-flight limit applies backpressure so decoding cannot enqueue an unbounded number of 100,000-point arrays faster than GL can upload them.

## Data flow

1. Native decoding produces one bounded chunk.
2. The activity hands ownership to `PointCloudView` and waits when the configured in-flight queue is full.
3. The GL thread uploads the chunk using `glBufferData`, updates bounds and the CPU reservoir, and releases one queue permit.
4. Drawing binds each VBO and calls `glDrawArrays` with an offset of zero.
5. Removing or resetting a scan deletes obsolete VBOs before discarding metadata.

## Context loss

VBO identifiers are invalid after GL context loss. The renderer reports this to the activity, which increments scene generations and reloads visible project scans from their local TZF sources. No stale buffer identifier is drawn. Reload requests are coalesced so one context-loss event causes one project reload.

## Memory and limits

- Decoder/native memory remains tile-bounded by the existing streaming cursor.
- Java transient memory is bounded by the chunk size multiplied by the small in-flight limit.
- Persistent CPU point memory is capped at 200,000 points per scan.
- GPU memory remains proportional to the selected point budget; `ALL` can still exceed a device's GPU capacity. OpenGL allocation errors stop that scan cleanly and recommend `10M` or `AUTO`.

## Failure handling

An upload checks `glGetError`. On out-of-memory or invalid buffer state, the new buffer is deleted, the permit is released, and the activity receives a readable scan error. Partial chunks for that scan are removed. Cancellation and project removal always release blocked producers.

## Verification

Pure Java tests cover reservoir limits and distribution. Offline Java compilation verifies API integration. Manual device checks cover `150k`, `10M`, and `ALL`, measurement and clipping against the sample, scan removal/reload, Activity recreation, and a forced background/foreground GL context recreation.

