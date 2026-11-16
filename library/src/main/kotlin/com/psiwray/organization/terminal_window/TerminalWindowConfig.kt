package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.BitmapFont

/**
 * Immutable configuration for one [TerminalWindow].
 *
 * The font is shared, immutable data; the window owns its own
 * [TerminalMatrix]. [columns] and [rows] describe the requested character
 * grid, not the actual framebuffer size. The framebuffer may produce a
 * different grid after content-scale and DPI clamping.
 *
 * @property font bitmap font used for cell dimensions and glyph masks.
 * @property title initial window title.
 * @property columns requested character columns.
 * @property rows requested character rows.
 * @property resizable whether the user may resize the window.
 * @property visible whether the window is shown after creation.
 * @property vsync whether the context swaps with vertical sync.
 * @property scale integer cell scale applied to the requested screen size.
 * @property defaultForeground ARGB color for fresh matrix cells.
 * @property defaultBackground ARGB color for fresh matrix cells.
 * @property ownership whether this window's service owns GLFW initialization.
 * @throws IllegalArgumentException if a dimension, scale, or requested screen
 *   size is outside the supported range.
 * @since 2.0.0
 */
public data class TerminalWindowConfig(
    public val font: BitmapFont,
    public val title: String = "terminal-window",
    public val columns: Int = 80,
    public val rows: Int = 24,
    public val resizable: Boolean = true,
    public val visible: Boolean = true,
    public val vsync: Boolean = true,
    public val scale: Int = 1,
    public val defaultForeground: Int = Colors.WHITE,
    public val defaultBackground: Int = Colors.BLACK,
    public val ownership: GlfwOwnership = GlfwOwnership.OWNED,
) {
    init {
        require(columns >= 1) { "columns must be positive: $columns" }
        require(rows >= 1) { "rows must be positive: $rows" }
        require(scale >= 1) { "scale must be positive: $scale" }
        require(font.cellWidth >= 1) { "font cellWidth must be positive: ${font.cellWidth}" }
        require(font.cellHeight >= 1) { "font cellHeight must be positive: ${font.cellHeight}" }

        val screenWidth = columns.toLong() * font.cellWidth * scale
        val screenHeight = rows.toLong() * font.cellHeight * scale
        require(screenWidth <= Int.MAX_VALUE.toLong()) {
            "requested screen width $screenWidth exceeds Int range"
        }
        require(screenHeight <= Int.MAX_VALUE.toLong()) {
            "requested screen height $screenHeight exceeds Int range"
        }
    }
}
