# Resize and DPI

This document fixes the resize contract. The normative trigger order is in
`docs/frame.md`; this page covers the geometry and the state that survives a
resize.

## Formulas

```text
cellPxW = max(1, round(font.cellWidth  * scale * contentScaleX))
cellPxH = max(1, round(font.cellHeight * scale * contentScaleY))
columns = max(1, framebufferWidth  / cellPxW)
rows    = max(1, framebufferHeight / cellPxH)
```

- `scale` is the integer configuration zoom, at least 1.
- `contentScale` is the latest `glfwGetWindowContentScale` value, captured on
  the service thread.
- Cell pixels round to whole device pixels. The grid floors the remaining
  framebuffer pixels to whole cells.
- Leftover pixels are covered by the next clear and use
  `TerminalMatrix.defaultBackground`.
- `fbW == 0 || fbH == 0` is minimized, not a 1x1 grid. The frame path skips
  upload, draw, and swap and keeps the previous matrix dimensions.

`ResizeCalculator` is pure and free of GL, GLFW, and window types. It uses
`roundToInt` for the cell size and integer division for the grid.

## Initial size

`glfwCreateWindow` receives screen coordinates:

```text
screenW = requestedColumns * font.cellWidth  * scale
screenH = requestedRows    * font.cellHeight * scale
```

The framebuffer is then read back from GLFW and converted with the formulas
above. For the normal fixture (`8x16`, integer scale, content scale 1), an
80x24 request becomes a 640x384 framebuffer and an 80x24 matrix.

## Applying a resize

GLFW callbacks only publish the latest snapshots:

- `onFramebufferSize` stores `FramebufferSize(width, height)`.
- `onContentScale` stores `WindowContentScale(x, y)`.
- Both enqueue the corresponding input event for listeners. Neither touches the
  matrix, renderer, or GL.

At the start of the next tick, after input dispatch:

1. Read the latest framebuffer and content-scale snapshots.
2. If either differs from the applied snapshot and the framebuffer is
   non-zero, recompute `CellMetrics`.
3. `matrix.resize(columns, rows, PRESERVE_TOP_LEFT)`.
4. Mark the matrix dirty even when the dimensions are unchanged. A viewport or
   cell-size change still needs a full upload.
5. `Renderer.resize` reallocates the direct staging buffer to exactly
   `columns * rows * 16` bytes and updates the viewport, framebuffer size, and
   cell pixel size.
6. The normal frame path performs one `glBufferData` and one draw.

Multiple callbacks therefore coalesce into one apply and one upload per tick.
The VBO storage is replaced with the exact byte count by the upload; no old
capacity is retained.

## Preservation

`PRESERVE_TOP_LEFT` is a crop/expand, not a scrollback anchor. The overlapping
block starting at `(0, 0)` is copied. New cells are space (`U+0020`) with the
matrix default foreground and background. Shrinking crops cells beyond the new
right and bottom edges; it never wraps.

The font atlas is not rebuilt. Only the framebuffer geometry, staging buffer,
and VBO storage change. The atlas remains at font resolution and is sampled by
`uAtlasCellPx`, independent of the display cell size.

## DPI behavior

Cell size rounds once, in device pixels, after multiplying both `scale` and the
fractional content scale. Examples with an `8x16` font and `scale = 1`:

| Framebuffer | Content scale | Cell pixels | Grid |
|---|---|---|---|
| 640x384 | 1.0 | 8x16 | 80x24 |
| 641x385 | 1.0 | 8x16 | 80x24 |
| 1600x1200 | 2.0 | 16x32 | 100x37 |
| 1200x900 | 1.5 | 12x24 | 100x37 |
| 1000x700 | 1.25 | 10x20 | 100x35 |
| 640x384 | 2.0 | 16x32 | 40x12 |

Glyphs use nearest filtering; non-100% scaling magnifies the bitmap mask and
does not re-rasterize the font.

## Related

[Frame contract](frame.md) · [Threading](threading.md) ·
[Performance](performance.md) · [Public API](public-api.md)