# 16 — Renderer draw path

**Phase:** rendering · **Depends on:** 14, 15 · **Blocks:** 17, 18, 20

## Goal

One staging buffer, one upload, one draw call per dirty frame; exact colors; wire the real `FrameSink` into `TerminalWindow` and expose `frame()`.

## Deliverables

- `.../internal/Renderer.kt` (`internal class Renderer : FrameSink`; owns staging buffer + draw state)
- `.../TerminalWindow.kt` updated: `frame()`, `renderStats()`, renderer lifecycle
- `library/src/integrationTest/kotlin/.../RendererPixelTest.kt`
- `docs/frame.md` updated with the GL call sequence.

## Steps

1. **Staging buffer.** One direct `ByteBuffer` with `ByteOrder.nativeOrder()`, sized `columns*rows*16`, reused. Grow only on resize; never allocate in `upload()`.
   ```kotlin
   private val staging: ByteBuffer = ByteBuffer.allocateDirect(capacity)
       .order(ByteOrder.nativeOrder())
   ```
2. **Pack a cell** (row-major, index `row*columns + col`):
   - `u16 col`, `u16 row`, `u16 slot` (`0xFFFF` if absent), `u16 0`
   - fg bytes in `R,G,B,A` order from the ARGB `Int`; then bg bytes.
   Convert once: `r = (argb ushr 16) and 0xFF`, `g = (argb ushr 8) and 0xFF`, `b = argb and 0xFF`, `a = argb ushr 24`.
3. **Upload.** If dirty: `glBindBuffer`, `glBufferData(GL_ARRAY_BUFFER, staging, GL_STREAM_DRAW)` (one call; revisit only if 20 shows a win from orphan + `glBufferSubData`). Count 1 upload, `columns*rows*16` bytes.
4. **Draw.** `glViewport(0,0,fbW,fbH)`; `glClearColor` from the matrix default background (normalized bytes); `glClear(GL_COLOR_BUFFER_BIT)`; `glUseProgram`; set `uViewportPx`, `uCellPx`, `uAtlasCellPx`, `uAtlasGrid`, `uAtlasTexels`; bind texture unit 0; bind VAO; `glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, columns*rows)`; unbind. Count 1 draw.
5. **Wire the frame.** `TerminalWindow.frame()`: if on the service thread, `service.pump()`; then `pipeline.tick()` with the renderer as sink; `glfwSwapBuffers` happens inside the sink's `swap()`.
6. **Close.** Renderer deletes GL objects before the context is released; the window then destroys the GLFW window (12).
7. **Pixel tests.** Render one `A` at cell (0,0) with fg `0xFFFF0000.toInt()`, bg `0xFF0000FF.toInt()` into a 64×64 FBO at 2× scale: assert a pixel inside a filled stroke is exactly red, a pixel in the glyph background is blue, and the area beyond the matrix is the clear color. Assert the glyph is not vertically mirrored (asymmetric fixture).
8. **Stats.** `window.renderStats()` returns the last tick; tests assert `drawCalls == 1`, `uploads in 0..1`, `uploadBytes` matches.
9. **Kotlin hot path.** No `forEach`, no destructuring, no `Pair`; use indexed loops and primitive locals. `staging.put(index, byte)` / `putShort(index, value)` to avoid per-cell bound churn where it matters.

## Acceptance

- [x] 1000 cell edits in one tick → exactly 1 `glBufferData` and 1 `glDrawArraysInstanced`.
- [x] A clean tick uploads 0 bytes but still draws and swaps.
- [x] Pixel-exact colors on the FBO; no blending/dithering artifacts.
- [x] Renderer close is idempotent and leaves `glGetError() == GL_NO_ERROR`.
- [x] `frame()` works from a non-service owner thread only when the service thread pumps; misuse produces a clear exception.
- [x] No allocation per frame after the first (measured over 200 frames).

## Pitfalls

- Attribute 0 must be an integer attribute (`glVertexAttribIPointer`); mixing float and integer pointers silently corrupts cell coordinates.
- Do not call `glFinish` or `glReadPixels` in the frame path; they serialize the GPU.
- The clear color must match the matrix background or resize leftovers flicker.
- Instance count 0 (minimized window) skips the draw but still swaps according to 17's minimized rule.
- Removing the context before deleting textures leaks GPU memory until process exit.
- `ByteBuffer` mutation advances `position`; use absolute `put(index, value)` or `rewind()`/`clear()` deliberately.

## Plan seed

> Plan section 16 only: implement `Renderer` in Kotlin (staging buffer, ARGB→RGBA packing, one upload, one instanced draw, clear/viewport), wire `TerminalWindow.frame()` and `renderStats()`, and add FBO pixel tests including orientation. No resize formulas, no API polish.