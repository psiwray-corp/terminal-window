package com.psiwray.organization.terminal_window

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalMatrixTest {
    private val foreground = Colors.rgb(0, 255, 0)
    private val background = Colors.rgb(0, 0, 255)

    @Test
    fun `new matrix is clean and contains default cells`() {
        val matrix = TerminalMatrix(4, 3)
        assertEquals(4, matrix.columns)
        assertEquals(3, matrix.rows)
        assertFalse(matrix.isDirty)

        var row = 0
        while (row < matrix.rows) {
            var col = 0
            while (col < matrix.columns) {
                assertEquals(' '.code, matrix.codePointAt(col, row))
                assertEquals(matrix.defaultForeground, matrix.foregroundAt(col, row))
                assertEquals(matrix.defaultBackground, matrix.backgroundAt(col, row))
                col++
            }
            row++
        }
    }

    @Test
    fun `dirty tracks actual cell changes`() {
        val matrix = TerminalMatrix(3, 2)

        repeat(3) {
            matrix.set(1, 1, ' '.code, matrix.defaultForeground, matrix.defaultBackground)
        }
        assertFalse(matrix.isDirty)

        matrix.set(1, 1, 'x'.code, matrix.defaultForeground, matrix.defaultBackground)
        assertTrue(matrix.isDirty)

        matrix.clearDirty()
        repeat(3) {
            matrix.set(1, 1, 'x'.code, matrix.defaultForeground, matrix.defaultBackground)
        }
        assertFalse(matrix.isDirty)

        matrix.set(1, 1, 'x'.code, foreground, matrix.defaultBackground)
        assertTrue(matrix.isDirty)

        matrix.clearDirty()
        matrix.set(1, 1, 'x'.code, foreground, background)
        assertTrue(matrix.isDirty)
    }

    @Test
    fun `write iterates code points and returns the next column`() {
        val matrix = TerminalMatrix(5, 2)
        val end = matrix.write(1, 0, "a\uD83D\uDE00b", foreground, background)

        assertEquals(4, end)
        assertEquals(' '.code, matrix.codePointAt(0, 0))
        assertEquals('a'.code, matrix.codePointAt(1, 0))
        assertEquals(0x1F600, matrix.codePointAt(2, 0))
        assertEquals('b'.code, matrix.codePointAt(3, 0))
        assertEquals(' '.code, matrix.codePointAt(4, 0))
        assertEquals(foreground, matrix.foregroundAt(2, 0))
        assertEquals(background, matrix.backgroundAt(2, 0))
    }

    @Test
    fun `write stops at the right edge without wrapping`() {
        val matrix = TerminalMatrix(4, 1)
        val end = matrix.write(2, 0, "abcd", foreground, background)

        assertEquals(4, end)
        assertEquals('a'.code, matrix.codePointAt(2, 0))
        assertEquals('b'.code, matrix.codePointAt(3, 0))
    }

    @Test
    fun `write rejects unpaired surrogates`() {
        val matrix = TerminalMatrix(2, 1)
        assertFailsWith<IllegalArgumentException> {
            matrix.write(0, 0, "\uD83D", foreground, background)
        }
        assertFailsWith<IllegalArgumentException> {
            matrix.write(0, 0, "\uDE00", foreground, background)
        }
    }

    @Test
    fun `empty write returns the start column without dirtying`() {
        val matrix = TerminalMatrix(2, 1)
        assertEquals(1, matrix.write(1, 0, "", foreground, background))
        assertFalse(matrix.isDirty)
    }

    @Test
    fun `all accessors validate bounds`() {
        val matrix = TerminalMatrix(3, 2)

        val columnError =
            assertFailsWith<IllegalArgumentException> {
                matrix.codePointAt(-1, 0)
            }
        assertTrue(columnError.message.orEmpty().contains("-1"))
        assertTrue(columnError.message.orEmpty().contains("0 until 3"))

        assertFailsWith<IllegalArgumentException> { matrix.foregroundAt(0, 2) }
        assertFailsWith<IllegalArgumentException> { matrix.backgroundAt(3, 0) }
        assertFailsWith<IllegalArgumentException> { matrix.set(0, -1, 'x'.code, foreground, background) }
        assertFailsWith<IllegalArgumentException> {
            matrix.write(3, 0, "x", foreground, background)
        }
    }

    @Test
    fun `clear restores defaults and only dirties on change`() {
        val matrix = TerminalMatrix(3, 2)
        matrix.set(1, 1, 'x'.code, foreground, background)
        matrix.clear()

        var row = 0
        while (row < matrix.rows) {
            var col = 0
            while (col < matrix.columns) {
                assertEquals(' '.code, matrix.codePointAt(col, row))
                assertEquals(matrix.defaultForeground, matrix.foregroundAt(col, row))
                assertEquals(matrix.defaultBackground, matrix.backgroundAt(col, row))
                col++
            }
            row++
        }
        assertTrue(matrix.isDirty)

        matrix.clearDirty()
        matrix.clear()
        assertFalse(matrix.isDirty)
    }

    @Test
    fun `fill changes colors but preserves code points`() {
        val matrix = TerminalMatrix(2, 2)
        matrix.set(1, 1, 'x'.code, matrix.defaultForeground, matrix.defaultBackground)
        matrix.clearDirty()

        matrix.fill(foreground, background)

        assertEquals('x'.code, matrix.codePointAt(1, 1))
        assertEquals(foreground, matrix.foregroundAt(0, 0))
        assertEquals(background, matrix.backgroundAt(0, 0))
        assertTrue(matrix.isDirty)

        matrix.clearDirty()
        matrix.fill(foreground, background)
        assertFalse(matrix.isDirty)
    }

    @Test
    fun `resize preserve copies the top-left block`() {
        val matrix = TerminalMatrix(80, 24)
        var row = 0
        while (row < matrix.rows) {
            var col = 0
            while (col < matrix.columns) {
                val codePoint = 'A'.code + (row * matrix.columns + col) % 26
                matrix.set(col, row, codePoint, foreground + col, background + row)
                col++
            }
            row++
        }
        matrix.clearDirty()

        matrix.resize(100, 30, ResizePolicy.PRESERVE_TOP_LEFT)

        assertEquals(100, matrix.columns)
        assertEquals(30, matrix.rows)
        assertTrue(matrix.isDirty)
        row = 0
        while (row < 24) {
            var col = 0
            while (col < 80) {
                assertEquals('A'.code + (row * 80 + col) % 26, matrix.codePointAt(col, row))
                assertEquals(foreground + col, matrix.foregroundAt(col, row))
                assertEquals(background + row, matrix.backgroundAt(col, row))
                col++
            }
            row++
        }
        row = 0
        while (row < 30) {
            var col = 0
            while (col < 100) {
                if (col >= 80 || row >= 24) {
                    assertEquals(' '.code, matrix.codePointAt(col, row))
                    assertEquals(matrix.defaultForeground, matrix.foregroundAt(col, row))
                    assertEquals(matrix.defaultBackground, matrix.backgroundAt(col, row))
                }
                col++
            }
            row++
        }
    }

    @Test
    fun `resize clear discards contents`() {
        val matrix = TerminalMatrix(4, 3)
        matrix.set(1, 1, 'x'.code, foreground, background)

        matrix.resize(6, 5, ResizePolicy.CLEAR)

        assertEquals(6, matrix.columns)
        assertEquals(5, matrix.rows)
        assertTrue(matrix.isDirty)
        assertEquals(' '.code, matrix.codePointAt(1, 1))
        assertEquals(matrix.defaultForeground, matrix.foregroundAt(1, 1))
        assertEquals(matrix.defaultBackground, matrix.backgroundAt(1, 1))
    }

    @Test
    fun `same-size preserve resize is a no-op`() {
        val matrix = TerminalMatrix(4, 3)
        matrix.set(1, 1, 'x'.code, foreground, background)
        matrix.clearDirty()

        matrix.resize(4, 3, ResizePolicy.PRESERVE_TOP_LEFT)

        assertFalse(matrix.isDirty)
        assertEquals('x'.code, matrix.codePointAt(1, 1))
    }

    @Test
    fun `resize rejects invalid dimensions without mutation`() {
        val matrix = TerminalMatrix(4, 3)
        assertFailsWith<IllegalArgumentException> {
            matrix.resize(0, 3, ResizePolicy.PRESERVE_TOP_LEFT)
        }
        assertFailsWith<IllegalArgumentException> {
            matrix.resize(Int.MAX_VALUE, 2, ResizePolicy.PRESERVE_TOP_LEFT)
        }
        assertEquals(4, matrix.columns)
        assertEquals(3, matrix.rows)

        assertFailsWith<IllegalArgumentException> { TerminalMatrix(0, 1) }
        assertFailsWith<IllegalArgumentException> { TerminalMatrix(Int.MAX_VALUE, 2) }
    }

    @Test
    fun `large fill smoke`() {
        val matrix = TerminalMatrix(200, 60)
        matrix.fill(foreground, background)
        assertTrue(matrix.isDirty)

        matrix.clearDirty()
        matrix.fill(foreground, background)
        assertFalse(matrix.isDirty)
    }

    @Test
    fun `set and write do not allocate per call`() {
        val matrix = TerminalMatrix(80, 24)
        var index = 0
        while (index < 10_000) {
            matrix.set(index % 80, (index / 80) % 24, 'A'.code, foreground, background)
            index++
        }

        matrix.clearDirty()
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = bean.getCurrentThreadAllocatedBytes()

        index = 0
        while (index < 10_000) {
            matrix.set(index % 80, (index / 80) % 24, 'B'.code, foreground, background)
            matrix.write(0, 0, "hello", foreground, background)
            index++
        }

        val allocated = bean.getCurrentThreadAllocatedBytes() - before
        assertTrue(allocated < 1_048_576, "allocated $allocated bytes")
    }
}
