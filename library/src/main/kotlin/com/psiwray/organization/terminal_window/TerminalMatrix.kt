package com.psiwray.organization.terminal_window

import java.util.Arrays
import kotlin.math.min

/**
 * A mutable, row-major character matrix with ARGB foreground and background
 * colors.
 *
 * A fresh matrix contains spaces with [defaultForeground] and
 * [defaultBackground]. An empty cell is U+0020 SPACE with those defaults; there
 * is no separate empty-cell flag. The object has no GL or thread dependencies
 * and is not thread-safe: it is confined to the window owner thread. No
 * synchronization or volatile state is used.
 *
 * Mutations set [isDirty] only when the stored cell triple actually changes.
 * The frame scheduler consumes the flag with [clearDirty] after uploading the
 * matrix.
 *
 * @throws IllegalArgumentException if either dimension is not positive or the
 *   cell count exceeds `Int.MAX_VALUE`.
 * @since 2.0.0
 */
public class TerminalMatrix(
    columns: Int,
    rows: Int,
    /** ARGB foreground color used by fresh cells and [clear]. @since 2.0.0 */
    public val defaultForeground: Int = Colors.WHITE,
    /** ARGB background color used by fresh cells and [clear]. @since 2.0.0 */
    public val defaultBackground: Int = Colors.BLACK,
) {
    private var columnCount: Int = columns
    private var rowCount: Int = rows
    private var codePoints: IntArray = IntArray(0)
    private var foregrounds: IntArray = IntArray(0)
    private var backgrounds: IntArray = IntArray(0)

    /** Number of columns in the matrix. @since 2.0.0 */
    public val columns: Int
        get() = columnCount

    /** Number of rows in the matrix. @since 2.0.0 */
    public val rows: Int
        get() = rowCount

    /**
     * Whether cell contents changed since the last [clearDirty] call.
     *
     * Setting a cell to its current value does not set this flag.
     * @since 2.0.0
     */
    public var isDirty: Boolean = false
        private set

    init {
        validateDimensions(columns, rows)
        val count = columns * rows
        codePoints = IntArray(count)
        foregrounds = IntArray(count)
        backgrounds = IntArray(count)
        Arrays.fill(codePoints, EMPTY_CELL_CODE_POINT)
        Arrays.fill(foregrounds, defaultForeground)
        Arrays.fill(backgrounds, defaultBackground)
    }

    /** Returns the code point at ([col], [row]). @since 2.0.0 */
    public fun codePointAt(
        col: Int,
        row: Int,
    ): Int {
        requireCell(col, row)
        return codePoints[row * columns + col]
    }

    /** Returns the ARGB foreground color at ([col], [row]). @since 2.0.0 */
    public fun foregroundAt(
        col: Int,
        row: Int,
    ): Int {
        requireCell(col, row)
        return foregrounds[row * columns + col]
    }

    /** Returns the ARGB background color at ([col], [row]). @since 2.0.0 */
    public fun backgroundAt(
        col: Int,
        row: Int,
    ): Int {
        requireCell(col, row)
        return backgrounds[row * columns + col]
    }

    /**
     * Stores [codePoint], [fg], and [bg] at ([col], [row]).
     *
     * @throws IllegalArgumentException if the column or row is outside the
     *   matrix bounds.
     * @since 2.0.0
     */
    public fun set(
        col: Int,
        row: Int,
        codePoint: Int,
        fg: Int,
        bg: Int,
    ) {
        requireCell(col, row)
        setAt(row * columns + col, codePoint, fg, bg)
    }

    /**
     * Recolors every cell, leaving each cell's code point unchanged.
     *
     * The text/glyph content is therefore preserved; use [clear] to reset every
     * code point to the empty-cell space and restore the default colors.
     * @since 2.0.0
     */
    public fun fill(
        fg: Int,
        bg: Int,
    ) {
        var changed = false
        var index = 0
        val count = columns * rows
        while (index < count) {
            if (foregrounds[index] != fg) {
                foregrounds[index] = fg
                changed = true
            }
            if (backgrounds[index] != bg) {
                backgrounds[index] = bg
                changed = true
            }
            index++
        }
        if (changed) {
            isDirty = true
        }
    }

    /** Replaces every cell with a space and the default foreground and background. @since 2.0.0 */
    public fun clear() {
        var changed = false
        var index = 0
        val count = columns * rows
        while (index < count) {
            if (codePoints[index] != EMPTY_CELL_CODE_POINT) {
                codePoints[index] = EMPTY_CELL_CODE_POINT
                changed = true
            }
            if (foregrounds[index] != defaultForeground) {
                foregrounds[index] = defaultForeground
                changed = true
            }
            if (backgrounds[index] != defaultBackground) {
                backgrounds[index] = defaultBackground
                changed = true
            }
            index++
        }
        if (changed) {
            isDirty = true
        }
    }

    /**
     * Writes [text] as code points starting at ([col], [row]).
     *
     * Surrogate pairs occupy one cell. Writing stops at the right edge and
     * does not wrap; the return value is the column after the last written
     * cell, or [col] when [text] is empty.
     *
     * @throws IllegalArgumentException if the start column or row is outside
     *   the matrix bounds, or [text] contains an unpaired surrogate.
     * @since 2.0.0
     */
    public fun write(
        col: Int,
        row: Int,
        text: CharSequence,
        fg: Int,
        bg: Int,
    ): Int {
        requireCell(col, row)

        var charIndex = 0
        var targetColumn = col
        var targetIndex = row * columns + col
        while (charIndex < text.length && targetColumn < columns) {
            val codePoint = Character.codePointAt(text, charIndex)
            require(codePoint < MIN_SURROGATE || codePoint > MAX_SURROGATE) {
                "unpaired surrogate at index $charIndex"
            }
            setAt(targetIndex, codePoint, fg, bg)
            charIndex += Character.charCount(codePoint)
            targetColumn++
            targetIndex++
        }
        return targetColumn
    }

    /**
     * Resizes the matrix in place, preserving object identity.
     *
     * [ResizePolicy.PRESERVE_TOP_LEFT] copies the overlapping top-left block;
     * [ResizePolicy.CLEAR] discards all cells. Newly exposed cells contain a
     * space with the default colors. The dirty flag is set only when the
     * dimensions or cell contents change.
     *
     * @throws IllegalArgumentException if either dimension is not positive or
     *   the cell count exceeds `Int.MAX_VALUE`.
     * @since 2.0.0
     */
    public fun resize(
        columns: Int,
        rows: Int,
        policy: ResizePolicy,
    ) {
        validateDimensions(columns, rows)

        if (columns == columnCount && rows == rowCount) {
            if (policy == ResizePolicy.CLEAR) {
                clear()
            }
            return
        }

        val oldColumns = columnCount
        val oldRows = rowCount
        val count = columns * rows
        val newCodePoints = IntArray(count)
        val newForegrounds = IntArray(count)
        val newBackgrounds = IntArray(count)
        Arrays.fill(newCodePoints, EMPTY_CELL_CODE_POINT)
        Arrays.fill(newForegrounds, defaultForeground)
        Arrays.fill(newBackgrounds, defaultBackground)

        if (policy == ResizePolicy.PRESERVE_TOP_LEFT) {
            val copyColumns = min(oldColumns, columns)
            val copyRows = min(oldRows, rows)
            var row = 0
            while (row < copyRows) {
                val oldOffset = row * oldColumns
                val newOffset = row * columns
                System.arraycopy(codePoints, oldOffset, newCodePoints, newOffset, copyColumns)
                System.arraycopy(foregrounds, oldOffset, newForegrounds, newOffset, copyColumns)
                System.arraycopy(backgrounds, oldOffset, newBackgrounds, newOffset, copyColumns)
                row++
            }
        }

        codePoints = newCodePoints
        foregrounds = newForegrounds
        backgrounds = newBackgrounds
        columnCount = columns
        rowCount = rows
        isDirty = true
    }

    /**
     * Clears [isDirty] after the current contents have been uploaded.
     *
     * This is a frame-scheduler seam, not public API.
     */
    internal fun clearDirty() {
        isDirty = false
    }

    /**
     * Marks every cell for the next full upload.
     *
     * This is a resize seam: a viewport or cell-size change can require a
     * full re-upload even when the matrix dimensions and contents are unchanged.
     */
    internal fun markDirty() {
        isDirty = true
    }

    private fun setAt(
        index: Int,
        codePoint: Int,
        fg: Int,
        bg: Int,
    ) {
        if (codePoints[index] != codePoint || foregrounds[index] != fg || backgrounds[index] != bg) {
            codePoints[index] = codePoint
            foregrounds[index] = fg
            backgrounds[index] = bg
            isDirty = true
        }
    }

    private fun requireCell(
        col: Int,
        row: Int,
    ) {
        require(col >= 0 && col < columnCount) {
            "col $col is outside 0 until $columnCount"
        }
        require(row >= 0 && row < rowCount) {
            "row $row is outside 0 until $rowCount"
        }
    }

    private fun validateDimensions(
        columns: Int,
        rows: Int,
    ) {
        require(columns > 0) { "columns must be positive: $columns" }
        require(rows > 0) { "rows must be positive: $rows" }
        require(columns.toLong() * rows.toLong() <= Int.MAX_VALUE.toLong()) {
            "cell count ${columns}x$rows exceeds Int range"
        }
    }

    private companion object {
        private const val EMPTY_CELL_CODE_POINT: Int = 0x20
        private const val MIN_SURROGATE: Int = 0xD800
        private const val MAX_SURROGATE: Int = 0xDFFF
    }
}
