package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.BitmapFont
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TerminalWindowFrameTest {
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
    @Timeout(60)
    fun `frame uploads draws and reports stats on the service thread`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                val columns = window.matrix.columns
                val rows = window.matrix.rows
                var index = 0
                while (index < columns * rows) {
                    window.matrix.set(
                        index % columns,
                        index / columns,
                        'A'.code,
                        Colors.WHITE,
                        Colors.BLACK,
                    )
                    index++
                }

                onMain { window.frame() }
                val stats = window.renderStats()
                assertEquals(1, stats.uploads)
                assertEquals(columns * rows * 16L, stats.uploadBytes)
                assertEquals(1, stats.drawCalls)
                assertEquals(columns * rows, stats.cellsDrawn)

                onMain { window.frame() }
                val clean = window.renderStats()
                assertEquals(0, clean.uploads)
                assertEquals(0L, clean.uploadBytes)
                assertEquals(0, clean.drawCalls)
                assertEquals(0, clean.cellsDrawn)

                onMain {
                    window.forceRepaint = true
                    window.frame()
                }
                val forced = window.renderStats()
                assertEquals(0, forced.uploads)
                assertEquals(0L, forced.uploadBytes)
                assertEquals(1, forced.drawCalls)
                assertEquals(0, forced.cellsDrawn)
            } finally {
                onMain { window.close() }
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `frame works on a non-service owner after the service pumps`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                onMain { window.releaseContext() }
                val ready = CountDownLatch(1)
                val go = CountDownLatch(1)
                val failure = AtomicReference<Throwable?>(null)
                val worker =
                    Thread(
                        {
                            try {
                                window.acquireContext()
                                window.matrix.set(0, 0, 'A'.code, Colors.WHITE, Colors.BLACK)
                                ready.countDown()
                                go.await()
                                window.frame()
                                window.releaseContext()
                            } catch (throwable: Throwable) {
                                failure.set(throwable)
                            }
                        },
                        "window-owner",
                    )
                worker.start()
                assertTrue(ready.await(10, TimeUnit.SECONDS), "worker did not acquire the context")
                onMain { service.pump() }
                go.countDown()
                worker.join(30_000)
                assertTrue(!worker.isAlive, "owner worker did not finish")
                failure.get()?.let { throw it }

                val stats = window.renderStats()
                assertEquals(1, stats.uploads)
                assertEquals(1, stats.drawCalls)
            } finally {
                onMain { window.close() }
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(30)
    fun `frame from a non-owner thread reports the owner rule`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                val failure = assertFailsWith<IllegalStateException> { window.frame() }
                assertTrue(
                    failure.message.orEmpty().contains("owner thread"),
                    failure.message.orEmpty(),
                )
            } finally {
                onMain { window.close() }
            }
        } finally {
            closeService(service)
        }
    }

    private fun start(): TerminalWindowService = onMain { TerminalWindowService.initialize() }

    private fun closeService(service: TerminalWindowService) {
        onMain { service.close() }
    }

    private fun config(): TerminalWindowConfig =
        TerminalWindowConfig(
            font = font,
            title = "frame test",
            columns = 100,
            rows = 10,
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
