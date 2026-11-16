package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.internal.ResizeCalculator
import com.psiwray.organization.terminal_window.testing.GlAssertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ResizeTest {
    private val mainThread =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "main").apply { isDaemon = true }
        }

    private val font: BitmapFont by lazy { BitmapFont.parse(fontFixture()) }

    @AfterAll
    fun stopMainThread() {
        mainThread.shutdownNow()
    }

    @Test
    fun `calculator covers framebuffer, scale, and fractional content scale`() {
        assertMetrics(scale = 1, contentScale = 1.0f, width = 640, height = 384, columns = 80, rows = 24)
        assertMetrics(scale = 1, contentScale = 1.0f, width = 641, height = 385, columns = 80, rows = 24)
        assertMetrics(scale = 1, contentScale = 2.0f, width = 1600, height = 1200, columns = 100, rows = 37)
        assertMetrics(scale = 1, contentScale = 1.5f, width = 1200, height = 900, columns = 100, rows = 37)
        assertMetrics(scale = 2, contentScale = 1.0f, width = 640, height = 384, columns = 40, rows = 12)
        assertMetrics(scale = 1, contentScale = 1.25f, width = 1000, height = 700, columns = 100, rows = 35)
        assertMetrics(scale = 1, contentScale = 1.0f, width = 0, height = 0, columns = 1, rows = 1)
    }

    @Test
    @Timeout(60)
    fun `window resize preserves the top-left and blanks new cells`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                assertEquals(80, window.matrix.columns)
                assertEquals(24, window.matrix.rows)
                window.matrix.set(0, 0, 'A'.code, Colors.WHITE, Colors.BLACK)
                window.matrix.set(79, 23, 'B'.code, Colors.WHITE, Colors.BLACK)

                onMain {
                    GLFW.glfwSetWindowSize(window.rawHandle, 100 * font.cellWidth, 30 * font.cellHeight)
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (window.matrix.columns != 100 && System.nanoTime() < deadline) {
                        service.pump(50.milliseconds)
                        window.frame()
                    }
                }

                assertEquals(100, window.matrix.columns)
                assertEquals(30, window.matrix.rows)
                assertEquals('A'.code, window.matrix.codePointAt(0, 0))
                assertEquals('B'.code, window.matrix.codePointAt(79, 23))
                assertEquals(' '.code, window.matrix.codePointAt(99, 29))
                assertEquals(Colors.WHITE, window.matrix.foregroundAt(99, 29))
                assertEquals(Colors.BLACK, window.matrix.backgroundAt(99, 29))

                val stats = window.renderStats()
                assertEquals(1, stats.uploads)
                assertEquals(100L * 30L * 16L, stats.uploadBytes)
                assertEquals(1, stats.drawCalls)
                assertEquals(100 * 30, stats.cellsDrawn)
                onMain { GlAssertions.assertNoGlError() }
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    private fun assertMetrics(
        scale: Int,
        contentScale: Float,
        width: Int,
        height: Int,
        columns: Int,
        rows: Int,
    ) {
        val metrics =
            ResizeCalculator.compute(
                font = font,
                scale = scale,
                contentScaleX = contentScale,
                contentScaleY = contentScale,
                fbW = width,
                fbH = height,
            )
        assertEquals(columns, metrics.columns, "columns for ${width}x$height @ $contentScale")
        assertEquals(rows, metrics.rows, "rows for ${width}x$height @ $contentScale")
        assertEquals((font.cellWidth * scale * contentScale).roundToInt().coerceAtLeast(1), metrics.cellPxW)
        assertEquals((font.cellHeight * scale * contentScale).roundToInt().coerceAtLeast(1), metrics.cellPxH)
    }

    private fun start(): TerminalWindowService = onMain { TerminalWindowService.initialize() }

    private fun closeWindow(window: TerminalWindow) {
        onMain { window.close() }
    }

    private fun closeService(service: TerminalWindowService) {
        onMain { service.close() }
    }

    private fun config(): TerminalWindowConfig =
        TerminalWindowConfig(
            font = font,
            title = "resize test",
            columns = 80,
            rows = 24,
            visible = false,
            vsync = false,
        )

    private fun <T> onMain(block: () -> T): T {
        val future = mainThread.submit(Callable { block() })
        return try {
            future.get(30, TimeUnit.SECONDS)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (failure: TimeoutException) {
            future.cancel(true)
            throw AssertionError("main-thread task timed out", failure)
        }
    }

    private fun fontFixture(): Path {
        val candidates =
            listOf(
                Path.of("src/test/resources/fonts/valid/ascii-8x16.twf"),
                Path.of("library/src/test/resources/fonts/valid/ascii-8x16.twf"),
            )
        return candidates.firstOrNull { Files.isRegularFile(it) }
            ?: error("missing font fixture; tried ${candidates.joinToString()}")
    }
}
