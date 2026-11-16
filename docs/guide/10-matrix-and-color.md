# 10 — Matrix and color model

**Phase:** core · **Depends on:** 01 · **Blocks:** 14

## Goal

A cheap, mutable character matrix with foreground/background colors, change tracking, and resize semantics. No GL, no threads.

## Deliverables

- `.../TerminalMatrix.kt`
- `.../Colors.kt` (`object`)
- `.../ResizePolicy.kt` (`enum class ResizePolicy { PRESERVE_TOP_LEFT, CLEAR }`)
- `library/src/test/kotlin/.../TerminalMatrixTest.kt`, `ColorsTest.kt`

## Steps

1. **Storage.** Three `IntArray`s: `codePoints`, `fg`, `bg`, row-major. 12 B/cell. A single `dirty` flag (`var isDirty: Boolean private set`).
2. **Color contract.** Public colors are `Int` in **ARGB** (`0xAARRGGBB`). `Colors.rgb(r,g,b)` sets alpha `0xFF`; `Colors.rgba(r,g,b,a)` validates `0..255`. The renderer converts to RGBA byte order; document that conversion once, in section 16. JOML is not used for colors.
3. **API.**
   ```kotlin
   class TerminalMatrix(
       val columns: Int,
       val rows: Int,
       val defaultForeground: Int = 0xFFFFFFFF.toInt(),
       val defaultBackground: Int = 0xFF000000.toInt(),
   ) {
       fun codePointAt(col: Int, row: Int): Int
       fun foregroundAt(col: Int, row: Int): Int
       fun backgroundAt(col: Int, row: Int): Int
       fun set(col: Int, row: Int, codePoint: Int, fg: Int, bg: Int)
       fun fill(fg: Int, bg: Int)
       fun clear()
       fun write(col: Int, row: Int, text: CharSequence, fg: Int, bg: Int): Int
       fun resize(columns: Int, rows: Int, policy: ResizePolicy)
   }
   ```
   `write` iterates **code points** (not chars), stops at the right edge, returns the column after the last cell.
4. **Dirty semantics.** Set `dirty = true` only when the stored triple actually changes. `clear()`/`resize()` dirty if anything changed. No per-cell tracking in 2.0; the upload is whole-buffer. A clean matrix is not repainted by itself; the section 14 frame scheduler consumes the flag. Continuous `TerminalWindow.forceRepaint` and one-shot `TerminalWindow.requestRepaint()` are window controls, not matrix state.
5. **Resize.** `PRESERVE_TOP_LEFT` copies the overlap and fills new cells with space/default colors; `CLEAR` allocates fresh. The matrix object identity is preserved.
6. **Bounds.** `require(col in 0 until columns)` with a message naming the value, dimension, and range. Never clip silently.
7. **Defaults.** Constructor params; `clear()` and resize padding use them.
8. **Threading.** Not thread-safe; KDoc states "confined to the window owner thread". No synchronization, no `@Volatile`.
9. **Hot path.** No boxing: primitives only, indexed loops in `write`/`clear`/`resize`. Avoid `forEach`, `map`, `Pair`.
10. **Tests.** Bounds, write across edge, code-point iteration (`"a😀b"`), dirty false on same-value set, dirty true on color-only change, resize preserve/clear, default fill, large fill performance smoke.

## Acceptance

- [x] `write` handles surrogate pairs as single cells (or rejects unpaired surrogates — decide and test).
- [x] Setting the same value three times does not dirty the matrix; changing only background does.
- [x] Resize from 80×24 to 100×30 preserves the top-left 80×24 block exactly.
- [x] No allocation per `set`/`write` call (verify with a 10k-write loop and `ThreadMXBean`, loosely).
- [x] No LWJGL imports; no JOML types in the matrix API.

## Pitfalls

- ARGB vs ABGR: pick ARGB for the API and convert once at the GPU boundary; never let both conventions exist.
- `String[i]` splits emoji; use `Character.codePointAt` + `Character.charCount`.
- `resize` while iterating is user error; do not attempt defensive copying.
- Do not add a cursor field; the cursor is a user-land concern (example 19 inverts one cell).
- Kotlin `Int` overflow in `row * columns + col` for huge matrices; validate dimensions so the product fits in `Int`.

## Plan seed

> Plan section 10 only: implement `TerminalMatrix` (ARGB `Int` colors, dirty flag, resize policies, code-point-aware writes) and `Colors`, with bounds-checked APIs, allocation-free hot paths, and unit tests. No OpenGL, no window.
