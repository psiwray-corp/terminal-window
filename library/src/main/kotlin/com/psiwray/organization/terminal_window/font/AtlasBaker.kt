package com.psiwray.organization.terminal_window.font

/**
 * Packs a [BitmapFont] into a gutter-free R8 atlas.
 *
 * No GL calls or GL queries are made here. The maximum texture size is supplied
 * by the caller; section 12 passes the driver's `GL_MAX_TEXTURE_SIZE`.
 */
internal object AtlasBaker {
    /**
     * Bakes [font] into a deterministic atlas no larger than [maxTextureSize]
     * in either dimension.
     *
     * Slots are ordered by ascending code point. The grid minimizes texture
     * area, with smaller width as the tie-break, among candidates that fit.
     * Glyph row 0 remains the top row in memory; no vertical flip is applied.
     *
     * @throws IllegalArgumentException if no grid fits [maxTextureSize] or the
     *   limit is not positive.
     */
    internal fun bake(
        font: BitmapFont,
        maxTextureSize: Int,
    ): Atlas {
        require(maxTextureSize > 0) { "maxTextureSize must be positive: $maxTextureSize" }

        val glyphCount = font.glyphCount
        if (glyphCount == 0) {
            return Atlas(AtlasLayout(0, 0, 0, 0, emptyMap()), ByteArray(0))
        }

        val codePoints = font.codePoints.toIntArray()
        codePoints.sort()

        val grid = chooseGrid(glyphCount, font.cellWidth, font.cellHeight, maxTextureSize)
        val slots = LinkedHashMap<Int, Int>(glyphCount)
        var slot = 0
        while (slot < glyphCount) {
            slots[codePoints[slot]] = slot
            slot++
        }

        val pixels = ByteArray(grid.pixelWidth * grid.pixelHeight)
        val cellWidth = font.cellWidth
        val cellHeight = font.cellHeight
        slot = 0
        while (slot < glyphCount) {
            val glyph = font.glyphs.getValue(codePoints[slot])
            val mask = glyph.mask
            val cellX = (slot % grid.columns) * cellWidth
            val cellY = (slot / grid.columns) * cellHeight
            var y = 0
            while (y < cellHeight) {
                val sourceRow = y * cellWidth
                val targetRow = (cellY + y) * grid.pixelWidth + cellX
                var x = 0
                while (x < cellWidth) {
                    if (mask[sourceRow + x].toInt() != 0) {
                        pixels[targetRow + x] = 0xFF.toByte()
                    }
                    x++
                }
                y++
            }
            slot++
        }

        return Atlas(
            AtlasLayout(
                columns = grid.columns,
                rows = grid.rows,
                pixelWidth = grid.pixelWidth,
                pixelHeight = grid.pixelHeight,
                slots = slots,
            ),
            pixels,
        )
    }

    /**
     * Chooses the grid that packs [glyphCount] cells into the smallest texture
     * area that fits [maxTextureSize] on both axes.
     *
     * A candidate is tried for every column count from `1` through
     * [glyphCount]. Its row count is integer-ceiled as
     * `(glyphCount + columns - 1) / columns`; its dimensions are
     * `columns * cellWidth` by `rows * cellHeight`. Candidates that exceed
     * [maxTextureSize] are skipped. [maxTextureSize] is the driver's
     * `GL_MAX_TEXTURE_SIZE` at runtime (or an explicit test limit), and it
     * constrains width and height independently, not total area. Area
     * comparison uses `Long` so the multiply cannot overflow before the
     * dimensions are validated.
     *
     * `best*` records the winning candidate that fits; `any*` records the
     * smallest-area candidate regardless of the limit, solely to report what
     * the font would require when nothing fits. Equal areas prefer the
     * narrower texture, matching the atlas contract.
     */
    private fun chooseGrid(
        glyphCount: Int,
        cellWidth: Int,
        cellHeight: Int,
        maxTextureSize: Int,
    ): Grid {
        // Best candidate that satisfies maxTextureSize.
        var bestColumns = 0
        var bestRows = 0
        var bestWidth = 0
        var bestHeight = 0
        var bestArea = Long.MAX_VALUE

        // Best candidate ignoring maxTextureSize, used for the overflow message.
        var anyWidth = 0
        var anyHeight = 0
        var anyArea = Long.MAX_VALUE

        var columns = 1
        while (columns <= glyphCount) {
            val rows = (glyphCount + columns - 1) / columns
            val width = columns * cellWidth
            val height = rows * cellHeight
            val area = width.toLong() * height.toLong()

            // Track the unconstrained result even when this candidate is rejected.
            if (area < anyArea || (area == anyArea && width < anyWidth)) {
                anyWidth = width
                anyHeight = height
                anyArea = area
            }

            if (width <= maxTextureSize && height <= maxTextureSize) {
                if (area < bestArea || (area == bestArea && width < bestWidth)) {
                    bestColumns = columns
                    bestRows = rows
                    bestWidth = width
                    bestHeight = height
                    bestArea = area
                }
            }
            columns++
        }

        // Zero is the sentinel for "no candidate fit".
        if (bestColumns == 0) {
            throw IllegalArgumentException(
                "atlas requires ${anyWidth}x$anyHeight pixels, " +
                    "but maxTextureSize=$maxTextureSize",
            )
        }

        return Grid(bestColumns, bestRows, bestWidth, bestHeight)
    }

    private class Grid(
        val columns: Int,
        val rows: Int,
        val pixelWidth: Int,
        val pixelHeight: Int,
    )
}
