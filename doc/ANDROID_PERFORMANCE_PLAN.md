# Android Brush Engine Performance Improvement Plan

This document outlines a practical, staged plan to significantly improve brush rendering performance on real Android devices for the libmypaint Android example, with minimal risk and measurable outcomes.

Scope:
- The example app in examples/android (Kotlin + JNI + libmypaint)
- Focus on interactive stroke latency and sustained frame rate while drawing
- Keep features and visual results unchanged unless explicitly noted

Targets:
- Median frame time while drawing: <= 8–12 ms on mid‑range ARM (60–120 FPS)
- Max frame time p95 while drawing: <= 16–25 ms
- End‑to‑end input‑to‑display latency: visibly smooth without dropped frames

Key principles:
- Measure first, then fix. Prove gains on device, not just in emulators.
- Avoid work on the UI thread; batch native updates; eliminate copies.
- Optimize the hot path before exploring larger redesigns.

---

## 1) Establish Baseline Metrics (Week 0)

Instrumentation and tools:
- Enable Android Studio profiler (CPU, Memory, GPU) on a real device.
- Add simple frame timing to the example app (Choreographer/FrameMetrics or a rolling average of onDraw durations).
- Add counters in JNI for:
  - stroke_to calls per second
  - tiles dirtied per stroke
  - mypaint_fixed_tiled_surface_read_rgba8 invocations per second and bytes copied
- Build two ABIs to test: arm64‑v8a and armeabi‑v7a.

Baseline scenarios to profile:
- Slow long stroke across the canvas with moderate brush size.
- Fast scribbles (high event rate).
- Large brush size (>40 px).
- Pressure variation if available.

Deliverables:
- A short report with per‑scenario median/p95 frame times, CPU usage, and memory bandwidth indicators.

---

## 2) Quick, Low‑Risk Wins (Week 1)

These items typically give immediate improvements without changing rendering algorithms.

A. Reduce UI thread work and allocations
- Avoid calling invalidate() more often than necessary; coalesce to ~16 ms (next vsync) using a posted Runnable if event rate is higher than display refresh.
- Reuse buffers: ByteArray and ByteBuffer are already reused, maintain this pattern and eliminate any hidden per‑frame allocation (verify with Allocation Tracker).
- Move rgbaToPremultipliedBGRA to native with NEON and write directly into a direct ByteBuffer to cut one copy and speed up the conversion. Keep Kotlin fallback for debug builds.

B. Trim JNI overhead
- Replace returning a new ByteArray per frame with a persistent direct ByteBuffer owned by Java/Kotlin and filled by native code; eliminate NewByteArray and GetByteArrayElements on the hot path.
- Batch stroke events in native: expose strokeToBatch(float[] xs, float[] ys, float[] pressures, float[] dts, ...), or accept a struct array; process in a single JNI call per frame.
- Use cached jclass/jmethodID references and avoid repeated lookups.

C. Minimize readbacks
- Only call mypaint_fixed_tiled_surface_read_rgba8 when something changed (already true), and no more than once per frame. If multiple motion events arrive in a frame, accumulate and read back once.

D. Atomic batching
- Ensure mypaint_surface_begin_atomic is called once at stroke begin and mypaint_surface_end_atomic once at stroke end (current code tracks g_in_atomic; verify behavior). This allows internal queueing and parallel tile processing.

---

## 3) Rendering Path Improvements (Week 2)

A. Dirty‑rect/dirty‑tile constrained updates
- Track the bounding box of modified tiles for each frame; optionally expose a native API that returns the minimal rect. Copy back only the dirty region(s) into the Bitmap using copyPixelsFromBuffer with offset/stride support, or drawBitmap with src rect.
- If Java Bitmap APIs limit partial upload performance, consider uploading into an OpenGL texture once and updating sub‑regions (GL) via a simple GLSurfaceView/SurfaceView renderer.

B. Switch to SurfaceView/GLSurfaceView (optional but recommended)
- Offload drawing to a separate surface; renders are not blocked by UI thread.
- Use a small OpenGL renderer that displays the RGBA8 buffer (or an external texture) and updates only dirty sub‑rects each frame using glTexSubImage2D.

---

## 4) Native Compute Optimizations (Week 2–3)

A. Enable ARM NEON and fast math where safe
- Build flags for Android: -O3 -ffast-math -fstrict-aliasing -fno-math-errno -fomit-frame-pointer (validate numerics and visuals).
- Ensure NEON is allowed (arm64‑v8a default; for armeabi‑v7a use -mfpu=neon -D__ARM_NEON if supported by your minSdk/ABI split).
- Audit hot loops in rgba conversion and tile compositing for vectorization; use C99 restrict and alignment hints already documented in PERFORMANCE.

B. Threading parallelism
- libmypaint already supports deferred processing and multithreading. Verify thread pool size equals or is slightly below number of big.LITTLE performance cores. Expose a setter if needed for Android.
- Consider raising tile queue capacity to reduce producer stalls during fast strokes.

C. Tile size experimentation
- Run microbenchmarks with different tile sizes as suggested in PERFORMANCE. Add a debug build switch to set tile size at MyPaintFixedTiledSurface creation and compare memory bandwidth and cache behavior on device.

---

## 5) Algorithmic and Data‑flow Improvements (Week 3–4)

A. Event resampling
- Resample MotionEvent sequences to a fixed frequency (e.g., 120–240 Hz) in native before integrating into the brush engine to avoid bursts and uneven spacing that cause heavy tile thrash. Maintain visual parity.

B. Velocity‑aware batching
- Accumulate sub‑strokes based on distance/velocity thresholds so that very dense points on slow movement are merged; keep the brush dynamics intact by integrating along the path.

C. Optional dab mask cache (advanced)
- Prototype a small MRU cache for dab masks as suggested in PERFORMANCE. Measure hit rate with typical Android brushes. If hit rate > ~25–30% under common settings, integrate; otherwise drop to keep memory down.

---

## 6) API Changes to the Android Example (Proposed)

New/changed JNI functions:
- initCanvas(width, height, options?): accepts tile size and thread count (optional)
- beginStroke(), endStroke(): unchanged but ensure atomic scope correctness
- strokeToBatch(float[] xs, float[] ys, float[] pressures, float[] dts, float[] xtilts, float[] ytilts)
- getDirtyRect(out int[4] xywh): returns the bounding box modified since the last readback
- setOutputBuffer(ByteBuffer direct, int stride): register a direct buffer the native code writes premultiplied BGRA into; avoids allocations and copies
- readRgbaRegion(int x, int y, int w, int h): for partial updates when not using a registered buffer

Kotlin side:
- Use a Choreographer callback to render at most once per frame while accumulating input events
- Replace View invalidation loop with SurfaceView/GLSurfaceView or at minimum, a frame‑coalesced invalidate
- Maintain a persistent DirectByteBuffer for the canvas pixels

---

## 7) Validation and Guardrails

- Provide a debug toggle to visualize dirty rects and per‑frame timing overlays.
- Add unit tests for JNI helpers (byte order conversion correctness, partial update bounds).
- Run golden‑image comparisons to ensure visual parity of strokes before/after changes at several brush sizes and speeds.

---

## 8) Rollout Checklist

- [ ] Baseline numbers collected on at least two devices (mid‑range, high‑end)
- [ ] Quick wins merged; verify 1.2–2× frame time reduction
- [ ] Rendering path updated to coalesced frame updates; verify GPU frame pacing stable
- [ ] NEON/flags validated; no regressions in image tests
- [ ] Optional GL path integrated (if chosen); battery impact assessed
- [ ] Documentation updated; flags and knobs exposed in ANDROID.md

---

## Known Hotspots in Current Example (as of this plan)

- Java/Kotlin code performs per‑event readback of the full RGBA buffer and a per‑pixel RGBA→premultiplied BGRA conversion on the UI thread.
- JNI allocates and returns a fresh ByteArray for each readback (renderDemo path), which is expensive; interactive path should avoid this too.
- Potentially multiple invalidates per frame causing redundant redraws.
- Atomic batching is present but must be audited to ensure single begin/end per stroke.

Addressing these items with the steps above should yield the most substantial user‑visible gains on Android.
