package com.psiwray.organization.terminal_window.font

/**
 * Placement of a baked font atlas.
 *
 * [slots] maps a code point to its row-major slot index. Slots are assigned in
 * ascending code-point order; slot `0xFFFF` is reserved for "no glyph" and is
 * never assigned. A slot's cell origin is
 * `(slot % columns * cellWidth, slot / columns * cellHeight)`.
 *
 * Atlas row 0 is the top row of the glyph in memory. The GL upload therefore
 * places row 0 at `v = 0`; the vertex shader flips `v` globally. The baker does
 * not pre-flip. For a slot `(slotX, slotY)`, the cell's top-left UV is
 * `u0 = slotX * cellWidth / pixelWidth` and
 * `v0 = slotY * cellHeight / pixelHeight`.
 */
internal data class AtlasLayout(
    /** Number of cell columns in the atlas. */
    val columns: Int,
    /** Number of cell rows in the atlas. */
    val rows: Int,
    /** Atlas width in pixels. */
    val pixelWidth: Int,
    /** Atlas height in pixels. */
    val pixelHeight: Int,
    /** Code point to row-major slot index. Slot `0xFFFF` is never assigned. */
    val slots: Map<Int, Int>,
)
