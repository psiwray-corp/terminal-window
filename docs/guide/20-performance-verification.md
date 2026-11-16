# 20 — Performance verification

**Phase:** quality · **Depends on:** 16 · **Blocks:** —

## Goal

Prove the memory/speed claims with numbers: bytes per cell, bytes per frame, one draw call, constant heap, and a repeatable local benchmark. No CI thresholds that flake.

## Budget (decide once)

| Quantity | Budget | Note |
|---|---|---|
| GPU instance data | 16 B/cell | `u16,u16,u16,u16,u32,u32` |
| CPU matrix | 12 B/cell | 3 × `IntArray` |
| Atlas | 1 B/px = `glyphs*cellW*cellH` | 95 × 8×16 = 12,160 B |
| Per dirty frame upload | `cols*rows*16` B | 240×67 = 257,280 B |
| Draw calls per frame | 1 | `glDrawArraysInstanced`, 4 vertices |
| GL objects | 1 program, 1 VAO, 1 VBO, 1 texture, 1 FBO (tests only) | |
| Heap allocation per frame | 0 after warm-up | measured, not asserted in CI |

## Deliverables

- `:benchmarks` module (Kotlin `application`, no JMH dependency) with `FrameBench.kt`; add `include(":benchmarks")` to `settings.gradle.kts`.
- `docs/performance.md`: budget table, measured numbers, methodology, hardware/software notes.
- Optional FPS/title readout in the demo (already in 19).

## Steps

1. **Harness.** Kotlin `fun main()`. Open a hidden 1280×720 window, render a full matrix of random glyphs/colors, run 2,000 warm-up frames + 10,000 measured frames; bind an FBO so no vsync pacing is involved.
2. **Time.** Report ns/frame, derived MPix/s and upload MB/s. Do not assert absolute times.
3. **Allocation.** `com.sun.management.ThreadMXBean#getThreadAllocatedBytes` before/after 10,000 frames on the rendering thread; divide; fail the benchmark run (not CI) if > 1 KiB/frame. Avoid lambdas/`Pair`/`forEach` in the measured loop.
4. **Draw-call accounting.** Use `RenderStats` (`drawCalls`, `uploads`, `uploadBytes`) and print per-frame values; cross-check with `apitrace` once, manually.
5. **Scenarios.** 80×24, 240×67, and "one cell changes per frame" (proves whole-buffer upload cost is acceptable: 257 KB/frame at 240×67).
6. **JOML.** Measure with and without JOML `Vector2f` in the mouse/cell mapping if it is used per cell; if it allocates or boxes, keep it out of the hot loop and note it.
7. **Report.** Store results in `docs/performance.md` with GPU/driver and CPU; note llvmpipe numbers are CPU-bound and only meaningful relative to each other.
8. **No regression gate.** Optionally compare against a checked-in baseline with a 20% tolerance in a manual `bench` task; keep it out of `check`.

## Acceptance

- [x] `./gradlew :benchmarks:run` prints a table with cells, upload bytes, ns/frame, and allocation/frame.
- [x] Allocation measurement reports 0 B/frame (or < 1 KiB) over 10k frames.
- [x] `RenderStats` shows exactly 1 draw per frame in the full-matrix case.
- [x] Atlas size matches `glyphs*cellW*cellH` for the shipped font.
- [x] Numbers in `docs/performance.md` are reproducible with the documented command.
- [x] The measured loop contains no Kotlin collection operators or boxing (review the bytecode or the source).

## Pitfalls

- `System.gc()`/JIT warm-up skews allocation numbers; measure on a warmed thread and exclude class loading.
- `glReadPixels`/`glFinish` in the loop measures synchronization, not rendering.
- Vsync must be off and an FBO bound, otherwise you measure the compositor.
- Kotlin `forEach`/`map`/`Pair` in the loop allocate or box; use `while`/`for` with primitives.
- Do not add JMH "because benchmark"; a 60-line `main` is enough and keeps the dependency graph clean.

## Plan seed

> Plan section 20 only: add a Kotlin `:benchmarks` module that measures frame time, upload bytes, draw calls, and per-frame allocation for 80×24 and 240×67 matrices, and document the budget/report. No thresholds in CI.