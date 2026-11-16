package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.font.BitmapFont

/** Cell pixel size and the grid derived from a framebuffer size. */
internal data class CellMetrics(
    val cellPxW: Int,
    val cellPxH: Int,
    val columns: Int,
    val rows: Int,
)

/**
 * Pure resize and DPI geometry.
 *
 * Cell sizes round to whole device pixels; the grid then floors the usable
 * framebuffer to whole cells. Callers must skip zero-sized framebuffers before
 * applying the result; the one-cell minimum below only keeps the result valid
 * for callers that use it without applying.
 */
internal object ResizeCalculator {
    internal fun compute(
        font: BitmapFont,
        scale: Int,
        contentScaleX: Float,
        contentScaleY: Float,
        fbW: Int,
        fbH: Int,
    ): CellMetrics {
        require(fbW >= 0) {
            "fbW must be non-negative: $fbW"
        }
        require(fbH >= 0) {
            "fbH must be non-negative: $fbH"
        }

        val cellPxW = WindowGeometry.cellPixels(font.cellWidth, scale, contentScaleX)
        val cellPxH = WindowGeometry.cellPixels(font.cellHeight, scale, contentScaleY)
        return CellMetrics(
            cellPxW = cellPxW,
            cellPxH = cellPxH,
            columns = WindowGeometry.dimension(fbW, cellPxW),
            rows = WindowGeometry.dimension(fbH, cellPxH),
        )
    }
}
