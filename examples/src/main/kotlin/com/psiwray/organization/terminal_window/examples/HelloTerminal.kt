package com.psiwray.organization.terminal_window.examples

import com.psiwray.organization.terminal_window.Colors
import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.CharEvent
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.Key
import com.psiwray.organization.terminal_window.input.KeyAction
import com.psiwray.organization.terminal_window.input.KeyEvent
import com.psiwray.organization.terminal_window.input.MouseMoveEvent
import com.psiwray.organization.terminal_window.input.ResizeEvent
import org.joml.Vector2f
import org.joml.Vector2i
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val EDITOR_TOP: Int = 3

/**
 * Interactive 80x24 terminal demo.
 *
 * Type into the editor, move the insertion point with the arrow keys, and use
 * Backspace or Enter. F1 cycles themes. Ctrl+Q and Cmd+Q both request a clean
 * close. The cursor is one matrix cell with foreground and background swapped.
 */
public fun main(args: Array<String>) {
    val autoClose = autoCloseMillis(args)
    val font = BitmapFont.parse(resolveFontPath(args))
    val service = initializeExampleService()
    service.use {
        val window =
            it.openWindow(
                TerminalWindowConfig(
                    font = font,
                    title = "terminal-window | starting",
                    columns = 80,
                    rows = 24,
                    defaultForeground = EXAMPLE_THEMES[0].foreground,
                    defaultBackground = EXAMPLE_THEMES[0].background,
                ),
            )
        window.use { HelloDemo(it, autoClose, screenshotPath(args)).run() }
    }
}

private class HelloDemo(
    private val window: TerminalWindow,
    private val autoCloseMillis: Long?,
    private val screenshot: Path?,
) {
    private val matrix: TerminalMatrix = window.matrix
    private var themeIndex: Int = 0
    private var theme: ExampleTheme = EXAMPLE_THEMES[themeIndex]
    private val cursor: Vector2i = Vector2i(0, editorTop())
    private val mouse: Vector2f = Vector2f()
    private var cursorPainted: Boolean = false
    private var savedColumn: Int = 0
    private var savedRow: Int = 0
    private var savedCodePoint: Int = ' '.code
    private var savedForeground: Int = Colors.WHITE
    private var savedBackground: Int = Colors.BLACK
    private var staleStatusRow: Int = -1
    private var chromeDirty: Boolean = false

    internal fun run() {
        window.forceRepaint = true
        window.addInputListener(EditorInput())
        paintChrome()
        seedEditor()
        paintStatus()
        paintCursor()

        val started = System.nanoTime()
        var frames = 0
        var captured = false
        var titleNanos = started
        while (!window.shouldClose && !deadlineReached(started, autoCloseMillis)) {
            window.frame()
            if (screenshot != null && !captured && System.nanoTime() - started >= 1_000_000_000L) {
                captureFrontBuffer(screenshot)
                captured = true
            }
            if (window.renderStats().drawCalls > 0) {
                frames++
            }
            if (!cursorPainted || chromeDirty) {
                if (chromeDirty) {
                    paintChrome()
                    paintStatus()
                    chromeDirty = false
                }
                clampCursor()
                paintCursor()
            }
            val now = System.nanoTime()
            if (now - titleNanos >= 1_000_000_000L) {
                val fps = frames * 1_000_000_000.0 / (now - titleNanos)
                updateTitle(fps)
                frames = 0
                titleNanos = now
            }
        }
    }

    private fun seedEditor() {
        if (matrix.rows <= 4) {
            return
        }
        matrix.write(
            0,
            4,
            "Hello, terminal-window!",
            theme.foreground,
            theme.background,
        )
        if (matrix.rows > 5) {
            matrix.write(
                0,
                5,
                "One matrix upload becomes one instanced draw call.",
                theme.secondary,
                theme.background,
            )
        }
        if (matrix.rows > 6) {
            matrix.write(
                0,
                6,
                "Type here, use arrows, Backspace, Enter, or F1.",
                theme.foreground,
                theme.background,
            )
        }
        cursor.set(0, min(7, editorBottom()))
    }

    private fun updateTitle(fps: Double) {
        val label = String.format(Locale.ROOT, "%.1f", fps)
        window.setTitle(
            "${matrix.columns}×${matrix.rows} | $label FPS | ${quitChordLabel()}",
        )
    }

    private fun paintChrome() {
        val background = theme.background
        val columns = matrix.columns
        val rows = matrix.rows
        if (rows <= 0) {
            return
        }
        val blank = " ".repeat(columns)
        var clearRow = 0
        while (clearRow < min(3, rows)) {
            matrix.write(0, clearRow, blank, theme.foreground, background)
            clearRow++
        }
        val banner = "terminal-window 2.0 - ${quitChordLabel()} quits"
        matrix.write(centered(banner), 0, banner, theme.accent, background)
        if (rows > 1) {
            val help = "Type | arrows | Backspace | Enter | F1 palette"
            matrix.write(0, 1, help.padEnd(columns), theme.foreground, background)
        }
        if (rows > 2) {
            matrix.write(0, 2, "-".repeat(columns), theme.secondary, background)
        }
    }

    private fun paintStatus() {
        if (matrix.rows <= 0) {
            return
        }
        val statusRow = matrix.rows - 1
        if (staleStatusRow in 0 until matrix.rows && staleStatusRow != statusRow) {
            matrix.write(
                0,
                staleStatusRow,
                " ".repeat(matrix.columns),
                theme.foreground,
                theme.background,
            )
        }
        staleStatusRow = statusRow
        val text =
            "theme=${theme.name}  cursor=${cursor.x},${cursor.y}  " +
                "mouse=${mouse.x.toInt()},${mouse.y.toInt()}  " +
                "forceRepaint=true"
        matrix.write(
            0,
            statusRow,
            text.padEnd(matrix.columns),
            theme.secondary,
            theme.background,
        )
    }

    private fun centered(text: String): Int = max(0, (matrix.columns - text.length) / 2)

    private fun editorTop(): Int = min(EDITOR_TOP, max(0, matrix.rows - 1))

    private fun editorBottom(): Int = max(editorTop(), matrix.rows - 2)

    private fun clampCursor() {
        if (matrix.columns <= 0 || matrix.rows <= 0) {
            return
        }
        cursor.x = cursor.x.coerceIn(0, matrix.columns - 1)
        cursor.y = cursor.y.coerceIn(editorTop(), editorBottom())
    }

    private fun paintCursor() {
        clearCursorHighlight()
        clampCursor()
        if (matrix.columns <= 0 || matrix.rows <= 0 || cursor.y > editorBottom()) {
            return
        }
        savedColumn = cursor.x
        savedRow = cursor.y
        savedCodePoint = matrix.codePointAt(savedColumn, savedRow)
        savedForeground = matrix.foregroundAt(savedColumn, savedRow)
        savedBackground = matrix.backgroundAt(savedColumn, savedRow)
        matrix.set(
            savedColumn,
            savedRow,
            savedCodePoint,
            savedBackground,
            savedForeground,
        )
        cursorPainted = true
    }

    private fun clearCursorHighlight() {
        if (!cursorPainted) {
            return
        }
        if (savedColumn in 0 until matrix.columns && savedRow in 0 until matrix.rows) {
            matrix.set(
                savedColumn,
                savedRow,
                savedCodePoint,
                savedForeground,
                savedBackground,
            )
        }
        cursorPainted = false
    }

    private fun cycleTheme() {
        clearCursorHighlight()
        themeIndex = (themeIndex + 1) % EXAMPLE_THEMES.size
        theme = EXAMPLE_THEMES[themeIndex]
        matrix.fill(theme.foreground, theme.background)
        paintChrome()
        paintStatus()
        paintCursor()
    }

    private fun insert(codePoint: Int) {
        clearCursorHighlight()
        clampCursor()
        matrix.write(
            cursor.x,
            cursor.y,
            String(Character.toChars(codePoint)),
            theme.foreground,
            theme.background,
        )
        cursor.x++
        if (cursor.x >= matrix.columns) {
            cursor.x = 0
            cursor.y = min(cursor.y + 1, editorBottom())
        }
        paintCursor()
        paintStatus()
    }

    private fun backspace() {
        clearCursorHighlight()
        if (cursor.x > 0) {
            cursor.x--
        } else if (cursor.y > editorTop()) {
            cursor.y--
            cursor.x = matrix.columns - 1
        } else {
            paintCursor()
            paintStatus()
            return
        }
        matrix.set(cursor.x, cursor.y, ' '.code, theme.foreground, theme.background)
        paintCursor()
        paintStatus()
    }

    private fun newline() {
        clearCursorHighlight()
        cursor.x = 0
        cursor.y = min(cursor.y + 1, editorBottom())
        paintCursor()
        paintStatus()
    }

    private fun moveCursor(
        dx: Int,
        dy: Int,
    ) {
        clearCursorHighlight()
        cursor.x = (cursor.x + dx).coerceIn(0, matrix.columns - 1)
        cursor.y = (cursor.y + dy).coerceIn(editorTop(), editorBottom())
        paintCursor()
        paintStatus()
    }

    private inner class EditorInput : InputListener {
        override fun onKey(event: KeyEvent) {
            if (event.action == KeyAction.RELEASE) {
                return
            }
            if (event.isQuitChord()) {
                window.requestClose()
                return
            }
            when (event.key) {
                Key.F1 -> if (event.action == KeyAction.PRESS) cycleTheme()
                Key.BACKSPACE -> backspace()
                Key.ENTER -> newline()
                Key.LEFT -> moveCursor(-1, 0)
                Key.RIGHT -> moveCursor(1, 0)
                Key.UP -> moveCursor(0, -1)
                Key.DOWN -> moveCursor(0, 1)
                else -> Unit
            }
        }

        override fun onChar(event: CharEvent) {
            if (event.isPrintable()) {
                insert(event.codePoint)
            }
        }

        override fun onMouseMove(event: MouseMoveEvent) {
            mouse.set(event.pixelX.toFloat(), event.pixelY.toFloat())
            paintStatus()
        }

        override fun onResize(event: ResizeEvent) {
            clearCursorHighlight()
            staleStatusRow = matrix.rows - 1
            chromeDirty = true
            clampCursor()
        }
    }
}
