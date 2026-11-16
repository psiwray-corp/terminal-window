package com.psiwray.organization.terminal_window.examples

import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window.TerminalWindow
import com.psiwray.organization.terminal_window.TerminalWindowConfig
import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.KeyAction
import com.psiwray.organization.terminal_window.input.KeyEvent
import org.joml.Vector2i
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Two windows rendered by two independent owner threads.
 *
 * The main thread owns GLFW, creates both windows, transfers each context with
 * `releaseContext()` and a worker's `acquireContext()`, and keeps pumping until
 * the native registry is empty. Each worker mutates only its own matrix and
 * calls `frame()`; no window is created from a worker.
 */
public fun main(args: Array<String>) {
    val autoClose = autoCloseMillis(args)
    val screenshot = screenshotPath(args)
    val firstShot = screenshot?.let { Path.of("$it.1") }
    val secondShot = screenshot?.let { Path.of("$it.2") }
    val font = BitmapFont.parse(resolveFontPath(args))
    val service = initializeExampleService()
    service.use { svc ->
        val first = svc.openWindow(config(font, "render-1", EXAMPLE_THEMES[0]))
        val second =
            try {
                svc.openWindow(config(font, "render-2", EXAMPLE_THEMES[1]))
            } catch (failure: Throwable) {
                first.close()
                throw failure
            }

        val failures = AtomicReference<Throwable?>()
        val stop = AtomicBoolean(false)
        val activeWorkers = AtomicInteger(2)
        first.addInputListener(quitListener(first))
        second.addInputListener(quitListener(second))
        first.releaseContext()
        second.releaseContext()

        val one =
            startWorker(
                window = first,
                id = 1,
                theme = EXAMPLE_THEMES[0],
                delayMillis = 16L,
                autoCloseMillis = autoClose,
                screenshot = firstShot,
                stop = stop,
                failures = failures,
                activeWorkers = activeWorkers,
            )
        val two =
            startWorker(
                window = second,
                id = 2,
                theme = EXAMPLE_THEMES[1],
                delayMillis = 55L,
                autoCloseMillis = autoClose,
                screenshot = secondShot,
                stop = stop,
                failures = failures,
                activeWorkers = activeWorkers,
            )

        try {
            // Pumping on the main thread is mandatory: workers only render.
            while (activeWorkers.get() > 0) {
                svc.pump()
            }
            // The workers have queued their destroy requests; drain them.
            svc.pump()
        } finally {
            stop.set(true)
            one.join()
            two.join()
        }
        failures.get()?.let { throw it }
        if (screenshot != null) {
            val parts = listOf(firstShot, secondShot).filterNotNull().filter { Files.isRegularFile(it) }
            if (parts.size == 2) {
                composeScreenshots(screenshot, parts)
            }
        }
    }
}

private fun config(
    font: BitmapFont,
    title: String,
    theme: ExampleTheme,
): TerminalWindowConfig =
    TerminalWindowConfig(
        font = font,
        title = title,
        columns = 42,
        rows = 12,
        defaultForeground = theme.foreground,
        defaultBackground = theme.background,
    )

private fun quitListener(window: TerminalWindow): InputListener =
    object : InputListener {
        override fun onKey(event: KeyEvent) {
            if (event.action != KeyAction.RELEASE && event.isQuitChord()) {
                window.requestClose()
            }
        }
    }

private fun startWorker(
    window: TerminalWindow,
    id: Int,
    theme: ExampleTheme,
    delayMillis: Long,
    autoCloseMillis: Long?,
    screenshot: Path?,
    stop: AtomicBoolean,
    failures: AtomicReference<Throwable?>,
    activeWorkers: AtomicInteger,
): Thread =
    thread(name = "render-$id", isDaemon = false) {
        try {
            render(window, id, theme, delayMillis, autoCloseMillis, screenshot, stop)
        } catch (failure: Throwable) {
            failures.compareAndSet(null, failure)
            stop.set(true)
        } finally {
            try {
                if (!window.isClosed) {
                    window.close()
                }
            } catch (failure: Throwable) {
                failures.compareAndSet(null, failure)
                stop.set(true)
            } finally {
                activeWorkers.decrementAndGet()
            }
        }
    }

private fun render(
    window: TerminalWindow,
    id: Int,
    theme: ExampleTheme,
    delayMillis: Long,
    autoCloseMillis: Long?,
    screenshot: Path?,
    stop: AtomicBoolean,
) {
    window.use { w ->
        w.acquireContext()
        val anchor = Vector2i(0, 2)
        val started = System.nanoTime()
        var tick = 0L
        var captured = false
        while (!stop.get() && !w.shouldClose && !deadlineReached(started, autoCloseMillis)) {
            paint(w.matrix, id, theme, tick, anchor)
            w.frame()
            tick++
            if (screenshot != null && !captured && System.nanoTime() - started >= 1_000_000_000L) {
                captureFrontBuffer(screenshot)
                captured = true
            }
            Thread.sleep(delayMillis)
        }
    }
}

private fun paint(
    matrix: TerminalMatrix,
    id: Int,
    theme: ExampleTheme,
    tick: Long,
    anchor: Vector2i,
) {
    matrix.clear()
    if (matrix.columns <= 0 || matrix.rows <= 0) {
        return
    }
    matrix.write(
        0,
        0,
        "render-$id  tick=$tick  ${matrix.columns}x${matrix.rows}".padEnd(matrix.columns),
        theme.accent,
        theme.background,
    )
    val barStart = anchor.x.coerceIn(0, matrix.columns - 1)
    val barRow = anchor.y.coerceIn(0, matrix.rows - 1)
    val barWidth = (tick % (matrix.columns - barStart).toLong()).toInt() + 1
    matrix.write(barStart, barRow, "#".repeat(barWidth), theme.foreground, theme.background)
    if (matrix.rows > 1) {
        matrix.write(
            0,
            matrix.rows - 1,
            "independent owner thread | ${quitChordLabel()}".padEnd(matrix.columns),
            theme.secondary,
            theme.background,
        )
    }
}
