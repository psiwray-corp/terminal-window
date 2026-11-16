# 17 — Resize and DPI

**Phase:** rendering · **Depends on:** 16 · **Blocks:** 19

## Goal

The matrix always matches the framebuffer: no stretched glyphs, no partial cells, no divide-by-zero when minimized, and content survives resizes.

## Deliverables

- `.../internal/ResizeCalculator.kt` (`internal object`; pure)
- `TerminalWindow` resize wiring (framebuffer + content-scale callbacks, pending state)
- `.../internal/Renderer.kt`: buffer reallocation on dimension change
- `library/src/integrationTest/kotlin/.../ResizeTest.kt`
- `docs/resize.md`: formulas and decisions.

## Formulas (decide here, once)

```text
cellPxW = max(1, round(font.cellWidth  * scale * contentScaleX))
cellPxH = max(1, round(font.cellHeight * scale * contentScaleY))
columns = max(1, framebufferWidth  / cellPxW)
rows    = max(1, framebufferHeight / cellPxH)
```

`scale` is the user's integer zoom (default 1). `contentScale` comes from `glfwGetWindowContentScale` on the service thread and keeps physical text size stable on HiDPI. Rounding the **cell pixel size** (not the scale) keeps glyph pixels aligned to device pixels. Leftover pixels (framebuffer % cellPx) are cleared to the default background.

## Steps

1. **Calculator.** `internal object ResizeCalculator { fun compute(font, scale, contentScaleX, contentScaleY, fbW, fbH): CellMetrics }` with `data class CellMetrics(cellPxW, cellPxH, columns, rows)`; use `kotlin.math.roundToInt`.
2. **Callbacks.** Framebuffer-size and content-scale callbacks (service thread) store the latest values in `@Volatile` slots; they never touch the matrix or GL.
3. **Apply point.** At the start of a tick (step 2 of 14), compare applied size with pending size. If changed: recompute columns/rows, `matrix.resize(cols, rows, PRESERVE_TOP_LEFT)`, reallocate the staging buffer and VBO capacity, `glViewport`, mark full dirty.
4. **Preserve.** Top-left anchored; new cells are space with default colors. Not a scrollback anchor.
5. **Minimized.** `fbW == 0 || fbH == 0`: skip upload/draw/swap for that tick, keep dims unchanged, report zero stats. Never divide by zero.
6. **Buffer growth.** Reallocate to the exact new size (resizes are rare); do not keep the old VBO.
7. **Scale changes.** Moving between monitors can change `contentScale` and framebuffer size in the same tick; apply both before recomputing.
8. **Initial size.** `glfwCreateWindow` takes screen coordinates: `screenW = columns*font.cellWidth*scale`, `screenH = rows*font.cellHeight*scale`; the framebuffer is `screen*contentScale`, and the derived matrix returns the requested columns/rows. Clamp to at least 1×1.
9. **Tests.** 640×384 @ 8×16/scale 1 → 80×24; 641×385 → 80×24 (1 px leftover); content scale 2 @ 800×600 points → framebuffer 1600×1200, cellPx 16×32 → 100×37 (floor); 0×0 → skipped; resize preserves a marker cell; content-scale change recomputes; no GL errors.

## Acceptance

- [x] Matrix dimensions equal the formula for at least 6 size/scale combinations, including fractional content scale 1.5.
- [x] After growing from 80×24 to 100×30, cells (0,0) and (79,23) are unchanged; new cells are blanks with default colors.
- [x] Minimized window: no GL calls that divide by zero, no crash, no stats growth.
- [x] One resize = one upload (not one per callback).
- [x] Glyphs remain pixel-crisp at 1×, 2×, and 3× (FBO pixel test at 2×).

## Pitfalls

- `glfwSetWindowSize` is in screen coordinates; never use it to derive cells.
- Content scale can be fractional (1.25/1.5); round the cell size, not the matrix size.
- The resize callback fires before the framebuffer is actually usable on some platforms; apply at tick start, not inside the callback.
- Do not recreate the font atlas on resize; only the instance buffer changes.
- Preserving content on shrink must crop, not wrap.
- Keep `ResizeCalculator` free of GL and window types so it is testable without a display.

## Plan seed

> Plan section 17 only: implement resize/DPI math (pure `ResizeCalculator`), pending-size application at tick start, matrix preservation, VBO reallocation, minimized handling, and integration tests across sizes/scales. No new public API beyond what 18 will audit.