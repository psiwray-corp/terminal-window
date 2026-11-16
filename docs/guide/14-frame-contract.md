# 14 — Frame contract and update cadence

**Phase:** runtime · **Depends on:** 10, 12, 13 · **Blocks:** 16

## Goal

Define and test the exact per-tick ordering, so matrix edits caused by input are applied at most once per rendered frame, in one upload, with deterministic statistics. Repaint is demand-driven: a clean, unforced, unrequested window performs no GL work.

## The requirement

The brief says the matrix is updated once after all input events are handled and buffers are swapped. Literal post-swap upload means one frame of latency for that frame's edits. Default decision (record in ADR-0005): **upload before draw**, once per rendered frame, after event dispatch. If you insist on post-swap, the only observable difference is one frame of latency; the "once per rendered frame" guarantee is unchanged.

Repaint is not unconditional. A tick renders only when `TerminalMatrix.isDirty` is set, a resize was applied, `TerminalWindow.requestRepaint()` was called, or `TerminalWindow.forceRepaint` is enabled. `forceRepaint` is continuous; `requestRepaint()` forces one frame with the current bounds and coalesces with other requests.

## Deliverables

- `.../internal/FramePipeline.kt` (ordering + stats)
- `.../internal/FrameSink.kt` (`internal interface FrameSink { fun upload(); fun draw(); fun swap() }`; the upload is whole-buffer in 2.0)
- `.../RenderStats.kt` (`data class RenderStats(columns, rows, uploads, uploadBytes, drawCalls, cellsDrawn, droppedEvents)`)
- `library/src/test/kotlin/.../FramePipelineTest.kt`
- `docs/frame.md`: sequence diagram + rationale.

## Steps

1. **Ordering.** `tick()` executes exactly:
   1. drain input events → dispatch listeners → matrix mutations (dirty flag set);
   2. apply pending resize (matrix resize, backend reallocation) if the framebuffer size changed; resize marks the matrix dirty;
   3. consume a pending one-shot repaint request and decide `render = matrix.isDirty || resizeApplied || repaintRequested || forceRepaint`;
   4. if `render` is false, return zeroed upload/draw/swap stats and do not call the sink;
   5. `upload()` **only if** the matrix is dirty or resized; a rendered frame for a clean matrix forced by `requestRepaint()`/`forceRepaint` uploads 0 bytes and reuses the existing buffer;
   6. `draw()` once for a rendered frame;
   7. `swap()` once for a rendered frame; clear the one-shot request only after the rendered frame completes.
2. **Coalescing.** Any number of cell edits, resize applications, and one-shot repaint requests between ticks produce at most one rendered frame and one upload. No code path may call the backend upload outside `tick()`.
3. **Stats.** `RenderStats` per tick, cached on the window; `uploads ∈ {0,1}`, `drawCalls ∈ {0,1}`, `cellsDrawn ∈ {0, columns*rows}`, `uploadBytes == cellsDrawn*16` when uploaded, and all counters are zero for a skipped tick.
4. **Resize interplay.** Resize marks the matrix fully dirty and forces a buffer reallocation before upload; it counts as the single upload of that tick.
5. **Empty/failed frames.** If a listener throws, abort the tick before upload/draw/swap; propagate. The window stays open; the matrix keeps the user's edits (still dirty), and a pending one-shot request remains pending.
6. **No-op frames.** A clean, unforced, unrequested tick performs no instance packing, upload, draw, or swap. `forceRepaint` renders every tick; `requestRepaint()` renders exactly one tick.
7. **Tests.** Recording sink: 3 edits → 1 upload/1 draw/1 swap; clean tick → 0 uploads/0 draws/0 swaps; `forceRepaint` → every tick renders, with 0-byte uploads while clean; `requestRepaint()` → exactly one rendered tick; resize tick → 1 upload + reallocation; listener exception → no upload/draw/swap and the request remains; stats exact; clean ticks allocate nothing (see 20).
8. **Kotlin notes.** The sink is `internal`; tests implement it with a counting class (no mocking framework). Avoid lambda-capturing counters in the pipeline; use `Int` counters.

## Acceptance

- [x] A tick with 10,000 cell writes performs exactly one upload.
- [x] `RenderStats.uploadBytes` equals `columns*rows*16` on a dirty frame and 0 on a clean one.
- [x] A clean, unforced, unrequested tick performs zero uploads, zero draws, and zero swaps.
- [x] A one-shot `requestRepaint()` renders exactly one tick; `forceRepaint` renders every tick.
- [x] Resize produces exactly one upload and one draw, not two.
- [x] No backend call happens before input dispatch completes.
- [x] The sequence is documented with a mermaid sequence diagram and matches the test.
- [x] The pipeline does not import GL/GLFW types directly.

## Pitfalls

- Do not mark dirty on every `set` without comparing values (section 10 handles this); otherwise clean frames render.
- Do not call `glfwPollEvents` from a worker thread inside the pipeline; pumping is a service-thread responsibility (11).
- On a rendered frame, `swap()` must happen even when an upload is skipped because a forced/requested frame reuses the existing buffer. A clean, unforced, unrequested tick skips upload, draw, and swap entirely.
- Clear a one-shot `requestRepaint()` only after the rendered frame completes; keep it pending if a listener throws. Multiple requests before the next rendered tick coalesce.
- Keep `forceRepaint` continuous and `requestRepaint()` one-shot; do not let either turn into an unconditional per-tick upload.
- Keep the sink interface `internal`; leaking it publicly freezes a test seam into the API.
- Kotlin `inline` lambdas in the tick path can allocate if they capture; use direct calls.

## Plan seed

> Plan section 14 only: implement `FramePipeline` in Kotlin with demand-driven ordering (`TerminalMatrix.isDirty`, resize, one-shot `requestRepaint()`, continuous `forceRepaint`), one upload/draw/swap per rendered frame, skipped clean ticks, `RenderStats`, and an `internal FrameSink` seam; test with a recording sink. No GL calls, no real renderer.
