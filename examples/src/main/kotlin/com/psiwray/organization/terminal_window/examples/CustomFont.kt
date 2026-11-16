package com.psiwray.organization.terminal_window.examples

import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.KeyAction
import com.psiwray.organization.terminal_window.input.KeyEvent
import java.nio.file.Path

/**
 * Font sampler for a `.twf` file.
 *
 * Pass a path as the first argument, or use `examples/fonts/ascii-8x16.twf`.
 * The grid shows each defined code point, its glyph cell, and a color sample.
 * An absent code point at the bottom demonstrates the font fallback glyph.
 * Ctrl+Q and Cmd+Q both request a clean close.
 */
public fun main(args: Array<String>) {
    val path = resolveFontPath(args)
    val font = BitmapFont.parse(path)
    val autoClose = autoCloseMillis(args)
    val screenshot = screenshotPath(args)
    val theme = EXAMPLE_THEMES[0]
    val service = initializeExampleService()
    service.use {
        val window =
            it.openWindow(
                TerminalWindowConfig(
                    font = font,
                    title = "CustomFont | ${path.fileName} | ${font.cellWidth}x${font.cellHeight}",
                    columns = 80,
                    rows = 24,
                    defaultForeground = theme.foreground,
                    defaultBackground = theme.background,
                ),
            )
        window.use { drawAndLoop(it, path, font, theme, autoClose, screenshot) }
    }
}

private fun drawAndLoop(
    window: TerminalWindow,
    path: Path,
    font: BitmapFont,
    theme: ExampleTheme,
    autoCloseMillis: Long?,
    screenshot: Path?,
) {
    paintSampler(window.matrix, path, font, theme)
    window.addInputListener(
        object : InputListener {
            override fun onKey(event: KeyEvent) {
                if (event.action != KeyAction.RELEASE && event.isQuitChord()) {
                    window.requestClose()
                }
            }
        },
    )
    val started = System.nanoTime()
    var frames = 0
    var captured = false
    while (!window.shouldClose && !deadlineReached(started, autoCloseMillis)) {
        window.frame()
        frames++
        if (screenshot != null && !captured && frames >= 2) {
            captureFrontBuffer(screenshot)
            captured = true
        }
        Thread.sleep(16L)
    }
}

private fun paintSampler(
    matrix: TerminalMatrix,
    path: Path,
    font: BitmapFont,
    theme: ExampleTheme,
) {
    matrix.clear()
    if (matrix.rows <= 0 || matrix.columns <= 0) {
        return
    }

    val header =
        "CustomFont | ${path.fileName} | ${font.cellWidth}x${font.cellHeight} | " +
            "${font.glyphCount} glyphs | fallback=${fallbackLabel(font)}"
    matrix.write(0, 0, header.padEnd(matrix.columns), theme.accent, theme.background)
    if (matrix.rows > 1) {
        matrix.write(
            0,
            1,
            "glyph + code point color sampler | ${quitChordLabel()} quits".padEnd(matrix.columns),
            theme.foreground,
            theme.background,
        )
    }

    val codePoints = font.codePoints.sorted()
    val perRow = 10
    val sampleStartRow = 3
    codePoints.forEachIndexed { index, codePoint ->
        val row = sampleStartRow + index / perRow
        if (row >= matrix.rows) {
            return@forEachIndexed
        }
        val column = (index % perRow) * 8
        if (column + 7 >= matrix.columns) {
            return@forEachIndexed
        }
        val color = sampleColor(index, theme)
        matrix.set(column, row, codePoint, color, theme.background)
        matrix.write(column + 1, row, codePointLabel(codePoint), color, theme.background)
    }

    val fallbackRow = sampleStartRow + (codePoints.size + perRow - 1) / perRow + 1
    if (fallbackRow < matrix.rows) {
        val absent = 0x1F600
        matrix.set(0, fallbackRow, absent, theme.accent, theme.background)
        val label =
            "U+1F600 is absent -> fallback ${fallbackLabel(font)}; " +
                "defined=${font.hasGlyph(absent)}"
        matrix.write(2, fallbackRow, label, theme.secondary, theme.background)
    }
}

private fun sampleColor(
    index: Int,
    theme: ExampleTheme,
): Int =
    when (index % 3) {
        0 -> theme.foreground
        1 -> theme.accent
        else -> theme.secondary
    }

private fun codePointLabel(codePoint: Int): String = "U+" + codePoint.toString(16).uppercase().padStart(4, '0')

private fun fallbackLabel(font: BitmapFont): String =
    font.fallbackCodePoint?.let { "U+" + it.toString(16).uppercase().padStart(4, '0') } ?: "blank"
