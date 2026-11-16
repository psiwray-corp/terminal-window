# 09 — Atlas baking (pure)

**Phase:** assets (pure Kotlin) · **Depends on:** 08 · **Blocks:** 15

## Goal

Turn a `BitmapFont` into one tightly packed `R8` pixel buffer plus grid metadata and a code-point→slot map. Deterministic, testable without a GL context.

## Deliverables

- `.../font/AtlasLayout.kt` (`data class AtlasLayout(columns, rows, pixelWidth, pixelHeight, slots: Map<Int, Int>)`)
- `.../font/Atlas.kt` (`class Atlas(val layout: AtlasLayout, val pixels: ByteArray)`)
- `.../font/AtlasBaker.kt` (`internal object AtlasBaker { fun bake(font: BitmapFont, maxTextureSize: Int): Atlas }`)
- `library/src/test/kotlin/.../font/AtlasBakerTest.kt`

## Steps

1. **Slot order.** Slots are assigned in ascending code-point order (stable across font edits). Slot `0xFFFF` is reserved for "no glyph".
2. **Grid search.** For `n` glyphs, choose integer `columns` in `1..n` minimizing texture area `(columns*cellW) * (ceil(n/columns)*cellH)`, tie-break on smaller width. Reject candidates exceeding `maxTextureSize`; if none fits, throw with required vs available size.
3. **Bake.** Width `columns*cellW`, height `rows*cellH`, row-major, glyph block at `(col*cellW, row*cellH)`. Copy the glyph mask row by row, mapping `1 → 0xFF.toByte()`. Unused slots stay `0`.
4. **Orientation.** Bake with **row 0 = top** of the glyph in memory (natural reading order). Document that GL uploads row 0 at `v=0`, and the vertex shader maps the top of each cell to `v=0` so no vertical flip is applied. Do not pre-flip in the baker.
5. **UV contract.** Document `u0 = slotX*cellW / pixelWidth`, `v0 = slotY*cellH / pixelHeight`; section 15 consumes it.
6. **Limits.** Validate texture dimensions against `maxTextureSize` (parameter; `4096` in tests). No GL query here; section 12 passes the real `GL_MAX_TEXTURE_SIZE`.
7. **No allocation in bake loops.** Indexed loops; `ByteArray` filled by index. `Map<Int,Int>` is built once, not per cell.
8. **Tests.** Known 4×6 glyph → exact bytes; asymmetric glyph proves row order; grid choice for `n = 1, 2, 95, 65535`; overflow error; padding-free adjacency; pixel count equals `glyphs*cellW*cellH` (no gutter by design).

## Acceptance

- [x] `Atlas` for the minimal fixture has exactly the expected dimensions and bytes.
- [x] Texture area is minimal for `n` in 1..512 (property-style check).
- [x] A font too large for `maxTextureSize=64` throws a message stating required dimensions.
- [x] No GL/LWJGL imports in `font`.
- [x] Byte output is deterministic across runs (same font → same bytes).
- [x] `pixels.size == layout.pixelWidth * layout.pixelHeight`.

## Pitfalls

- macOS texture limits are 16384; llvmpipe higher. Test with a small explicit limit, not the host limit.
- `ceil(n/columns)` must be integer math; a `Double` cast silently truncates.
- Keep the atlas gutter-free: `GL_NEAREST` plus exact cell-boundary UVs makes bleeding impossible. Adding padding later changes the UV contract.
- Do not iterate a `HashMap` when assigning slots; sort code points explicitly.
- `ByteArray` equality is reference-based: compare content in tests with `contentEquals`.

## Plan seed

> Plan section 09 only: implement deterministic atlas layout search and R8 baking from `BitmapFont` in Kotlin (no GL), with orientation and UV contracts documented, plus tests for exact bytes and overflow. No texture upload.