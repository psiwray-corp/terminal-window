# Performance budget and measurements

The renderer is intentionally small: one instanced draw, one 16-byte instance
per cell, one whole-matrix upload when the matrix is dirty, and no per-cell GL
traffic. These are the budgets the implementation is checked against. They are
not CI thresholds; the benchmark is manual and its absolute timing numbers
depend on the host.

## Budget

| Quantity | Budget | Basis |
|---|---:|---|
| GPU instance data | 16 B/cell | `u16,u16,u16,u16,u32,u32` |
| CPU matrix | 12 B/cell | 3 × `IntArray` |
| Atlas | 1 B/px = `glyphs * cellW * cellH` | 95 × 8 × 16 = 12,160 B for the shipped font |
| Per dirty frame upload | `cols * rows * 16` B | 240 × 67 = 257,280 B |
| Draw calls per frame | 1 | `glDrawArraysInstanced`, 4 vertices |
| GL objects | 1 program, 1 VAO, 1 VBO, 1 texture; FBO only in tests/benchmarks | |
| Heap allocation per frame | 0 after warm-up, measured and failed above 1 KiB in the benchmark | |

The atlas is gutter-free and row-major; slot `0xFFFF` is reserved for “no
glyph.” The shipped ASCII font has 95 glyphs in 8×16 cells, so its atlas is
12,160 bytes. `AtlasBakerTest` covers the packing invariant; the benchmark
reports the shipped-font budget next to the measured frame data.

## Harness

`benchmarks/src/main/kotlin/.../FrameBench.kt` is a plain Kotlin `main`; it
does not use JMH.

- Each scenario opens a hidden GLFW window with `vsync = false`.
- An RGBA8 FBO is bound for the run; its size matches the window framebuffer.
- The matrix is filled with random defined code points and random opaque
  foreground/background colors.
- 2,000 warm-up frames run before 10,000 measured frames.
- `RenderStats` is checked on every measured frame: exactly one upload, one
  draw, `cellsDrawn = columns * rows`, and `uploadBytes = cells * 16`.
- Allocation is read with
  `com.sun.management.ThreadMXBean#getThreadAllocatedBytes` around the measured
  loop on the rendering thread.
- The benchmark process fails locally if allocation exceeds 1,024 B/frame or
  if any upload/draw/cell counter is wrong. CI only compiles the module.

The three scenarios are:

| Scenario | Grid | Framebuffer | Dirty content |
|---|---:|---:|---|
| `full-80x24` | 80×24 | 640×384 | all cells rewritten every frame |
| `full-240x67` | 240×67 | 1920×1072 | all cells rewritten every frame |
| `one-cell-240x67` | 240×67 | 1920×1072 | one rotating cell rewritten every frame |

The one-cell case intentionally still uploads the whole matrix: it measures the
cost of the 257 KB upload rather than a partial-update optimization.

## Reproduce

Run from the repository root:

```bash
LIBGL_ALWAYS_SOFTWARE=1 ./gradlew :benchmarks:run
```

Drop `LIBGL_ALWAYS_SOFTWARE=1` on a host where GLFW selects a hardware driver.
The command prints the context, font/atlas budget, and the result table. The
defaults are the 2,000/10,000 frame protocol above; `--warmup=N` and
`--frames=N` are available for local investigation only.

## Measurements

Measured on the host described below with llvmpipe; absolute times are
CPU-bound. Replace these numbers when running on a different host. They are
reproducible with the command above.

| Scenario | Cells | Framebuffer | Upload B/frame | ns/frame | FPS | Alloc B/frame | Draw/frame | Upload MB/s | MPix/s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `full-80x24` | 1,920 | 640×384 | 30,720 | 2,172,712 | 460.3 | 138.1 | 1.00 | 14.14 | 113.11 |
| `full-240x67` | 16,080 | 1920×1072 | 257,280 | 20,722,261 | 48.3 | 12.4 | 1.00 | 12.42 | 99.33 |
| `one-cell-240x67` | 16,080 | 1920×1072 | 257,280 | 20,802,594 | 48.1 | 0.0 | 1.00 | 12.37 | 98.94 |

Measured 2026-11-16. The first scenario includes residual lazy class
initialization after warm-up; every value is still below the 1 KiB/frame
budget.

The two 240×67 rows describe the same end-to-end frame work with different
matrix mutation costs. Both clear the same 1920×1072 FBO, rasterize 2,059,200
pixels, draw 16,080 instanced glyph quads, and—because even one changed cell
sets the matrix dirty—upload the entire 257,280-byte instance buffer. The
full rewrite additionally performs 16,080 matrix writes and packs 16,080
instances; the one-cell case performs one write and packs the same full buffer.

Despite that difference, the measured frame times are 20.72 ms for the full
rewrite and 20.80 ms for the one-cell update: a 0.08 ms (under 0.4%)
difference, i.e. noise at this resolution. That does **not** mean mutating one
cell is as expensive as mutating 16,080 cells; it means the whole-buffer upload
and per-pixel/per-instance rendering dominate the frame. The one-cell scenario
therefore exposes the cost the renderer actually pays without a partial-update
path; it is not evidence that a one-cell CPU-side edit is expensive.

The benchmark sets `forceRepaint = true`, but that flag is not why the
one-cell case uploads the full buffer. `forceRepaint` only makes a tick
renderable; upload is still gated by `matrix.isDirty || resized`. The one-cell
scenario changes a cell every iteration, so it would upload the whole buffer
with or without `forceRepaint`; disabling the flag would only remove the
redundant forced-render trigger, not the upload. The flag keeps the harness
from skipping a frame if an update failed to mark the matrix dirty, and the
per-frame `RenderStats` check would fail in that case.

The table's FPS and MPix/s are derived from the measured elapsed time; the
benchmark itself prints ns/frame, allocation/frame, upload MB/s, and MPix/s.
The measured loop contains no Kotlin collection operators, `Pair`, or boxing;
it uses indexed `while` loops and primitive matrix writes.

## JOML

The library's cursor-to-cell mapping (`DeviceState.cursorPosition`) uses
primitive `Double` arithmetic; it does not construct a `Vector2f` per cell.
`HelloTerminal` stores one `Vector2f` for the latest mouse position, but it is
not in the per-cell frame path. The renderer's instance packing is entirely
primitive. No JOML operation allocates in the measured loop.

## Host and driver

| Item | Value |
|---|---|
| CPU | AMD Ryzen 5 5500U (6C/12T) |
| RAM | 7.1 GiB |
| OS | Arch Linux, kernel 7.2.14-arch1-1 |
| JDK | Temurin 25.0.4+7 |
| GL vendor | Mesa |
| GL renderer | llvmpipe (LLVM 23.1.4, 256 bits) |
| GL version | 4.6 (Core Profile) Mesa 26.2.7-arch1.1 |
| Display | X11 (`DISPLAY=:1`), software rasterizer |

llvmpipe executes the vertex and fragment work on the CPU. These numbers are
useful for comparing scenarios and catching allocations or extra uploads on the
same host; they are not a hardware-GPU frame-rate claim.

## Cross-check

`RenderStats` reports scheduler-visible counters. The full-matrix measured loop
requires `uploads == 1`, `drawCalls == 1`, and the exact upload byte count.
A one-off `apitrace` capture was not available on the measurement host; the
counter checks and the specified one-upload/one-draw frame contract are the
repeatable check. The renderer's `glDrawArraysInstanced` call is the only draw
submission in `Renderer.draw`.

## Related

[Frame contract](frame.md) · [Threading](threading.md) ·
[Dependencies](dependencies.md) · [Public API](public-api.md)