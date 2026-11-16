package com.psiwray.organization.terminal_window.input

/**
 * Cursor position relative to the top-left corner of the window content area.
 *
 * [screenX] and [screenY] use GLFW screen coordinates. On HiDPI displays they
 * differ from framebuffer pixels by the window content scale. [column] and
 * [row] are derived cell coordinates; either may be negative when the cursor
 * is outside the content area.
 *
 * @property screenX horizontal screen coordinate.
 * @property screenY vertical screen coordinate.
 * @property column cell column containing the cursor.
 * @property row cell row containing the cursor.
 * @throws IllegalArgumentException if a screen coordinate is not finite.
 * @since 2.0.0
 */
public data class CursorPosition(
    public val screenX: Double,
    public val screenY: Double,
    public val column: Int,
    public val row: Int,
) {
    init {
        require(screenX.isFinite()) { "screenX must be finite: $screenX" }
        require(screenY.isFinite()) { "screenY must be finite: $screenY" }
    }
}
