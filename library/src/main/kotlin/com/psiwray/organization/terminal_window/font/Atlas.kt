package com.psiwray.organization.terminal_window.font

/**
 * A baked R8 glyph mask.
 *
 * [pixels] is row-major, top row first, with one byte per texel: `0` for
 * background and `0xFF` for foreground. Its size is exactly
 * `layout.pixelWidth * layout.pixelHeight`.
 */
internal class Atlas(
    /** Grid and slot metadata. */
    val layout: AtlasLayout,
    /** R8 pixel bytes; row 0 is the top of the atlas. */
    val pixels: ByteArray,
)
