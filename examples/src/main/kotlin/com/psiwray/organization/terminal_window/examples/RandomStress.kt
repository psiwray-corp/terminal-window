package com.psiwray.organization.terminal_window.examples

import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.KeyAction
import com.psiwray.organization.terminal_window.input.KeyEvent
import java.nio.file.Path
import java.util.Locale
import java.util.SplittableRandom

private const val OPAQUE_ALPHA: Int = 0xFF000000.toInt()

/**
 * Random-cell stress demo for the matrix, instance packing, upload, and draw
 * path.
 *
 * Every rendered frame writes a random defined code point and random opaque
 * foreground/background colors into all 80x24 cells. The matrix is therefore
 * dirty and uploads exactly once per frame. FPS, matrix-update time, upload
 * throughput, draw count, and dropped events are printed once per second.
 *
 * Ctrl+Q and Cmd+Q both request a clean close. `--auto-close-ms` and
 * `--screenshot` are supported for headless verification.
 */
public fun main(args: Array<String>) {
    val autoClose = autoCloseMillis(args)
    val screenshot = screenshotPath(args)
    val font = BitmapFont.parse(resolveFontPath(args))
    val service = initializeExampleService()
    service.use {
        val window =
            it.openWindow(
                TerminalWindowConfig(
                    font = font,
                    title = "random-stress | 80×24",
                    columns = 80,
                    rows = 24,
                ),
            )
        window.use { runStress(it, autoClose, screenshot) }
    }
}

private fun runStress(
    window: TerminalWindow,
    autoCloseMillis: Long?,
    screenshot: Path?,
) {
    val matrix: TerminalMatrix = window.matrix
    val codePoints = definedCodePoints(window.font)
    val random = SplittableRandom()
    window.forceRepaint = true
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
    var lastReport = started
    var frames = 0L
    var updateNanos = 0L
    var uploadBytes = 0L
    var drawCalls = 0L
    var captured = false

    while (!window.shouldClose && !deadlineReached(started, autoCloseMillis)) {
        val updateStart = System.nanoTime()
        fillRandom(matrix, codePoints, random)
        updateNanos += System.nanoTime() - updateStart

        window.frame()
        val stats = window.renderStats()
        frames++
        uploadBytes += stats.uploadBytes
        drawCalls += stats.drawCalls

        if (screenshot != null && !captured && System.nanoTime() - started >= 1_000_000_000L) {
            captureFrontBuffer(screenshot)
            captured = true
        }

        val now = System.nanoTime()
        if (now - lastReport >= 1_000_000_000L) {
            report(
                window = window,
                elapsedNanos = now - lastReport,
                frames = frames,
                updateNanos = updateNanos,
                uploadBytes = uploadBytes,
                drawCalls = drawCalls,
            )
            frames = 0
            updateNanos = 0
            uploadBytes = 0
            drawCalls = 0
            lastReport = now
        }
    }
}

private fun fillRandom(
    matrix: TerminalMatrix,
    codePoints: IntArray,
    random: SplittableRandom,
) {
    val columns = matrix.columns
    val rows = matrix.rows
    var row = 0
    while (row < rows) {
        var column = 0
        while (column < columns) {
            val codePoint = codePoints[random.nextInt(codePoints.size)]
            matrix.set(
                column,
                row,
                codePoint,
                randomColor(random),
                randomColor(random),
            )
            column++
        }
        row++
    }
}

private fun definedCodePoints(font: BitmapFont): IntArray {
    val codePoints = font.codePoints.toIntArray()
    return if (codePoints.isEmpty()) intArrayOf('?'.code) else codePoints
}

private fun randomColor(random: SplittableRandom): Int = random.nextInt() or OPAQUE_ALPHA

private fun report(
    window: TerminalWindow,
    elapsedNanos: Long,
    frames: Long,
    updateNanos: Long,
    uploadBytes: Long,
    drawCalls: Long,
) {
    val seconds = elapsedNanos / 1_000_000_000.0
    val fps = frames / seconds
    val updateMillis = if (frames == 0L) 0.0 else updateNanos / 1_000_000.0 / frames
    val uploadMiBPerSecond = uploadBytes / (1024.0 * 1024.0) / seconds
    val stats = window.renderStats()
    window.setTitle(
        String.format(
            Locale.ROOT,
            "random-stress | %d×%d | %.1f FPS",
            stats.columns,
            stats.rows,
            fps,
        ),
    )
    println(
        String.format(
            Locale.ROOT,
            "randomStress fps=%.1f update=%.3fms/frame upload=%.2fMiB/s draws=%d cells=%dx%d dropped=%d",
            fps,
            updateMillis,
            uploadMiBPerSecond,
            drawCalls,
            stats.columns,
            stats.rows,
            stats.droppedEvents,
        ),
    )
}
