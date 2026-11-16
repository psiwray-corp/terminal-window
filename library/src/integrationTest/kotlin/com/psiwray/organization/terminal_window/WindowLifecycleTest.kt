package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.Key
import com.psiwray.organization.terminal_window.input.KeyEvent
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class WindowLifecycleTest {
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
    fun `hidden window reports a 3_3 context and matrix geometry`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                val info = window.contextInfo
                assertTrue(
                    info.actualMajor > ContextInfo.MIN_OPENGL_MAJOR ||
                        (
                            info.actualMajor == ContextInfo.MIN_OPENGL_MAJOR &&
                                info.actualMinor >= ContextInfo.MIN_OPENGL_MINOR
                        ),
                    "actual context: ${info.actualMajor}.${info.actualMinor} (${info.versionString})",
                )
                assertTrue(info.versionString.isNotBlank())
                assertTrue(info.renderer.isNotBlank())
                assertTrue(info.vendor.isNotBlank())
                assertTrue(info.shadingLanguageVersion.isNotBlank())
                assertTrue(
                    info.maxTextureSize >= ContextInfo.MIN_MAX_TEXTURE_SIZE,
                    "max texture size: ${info.maxTextureSize}",
                )
                assertEquals(80, window.matrix.columns)
                assertEquals(24, window.matrix.rows)
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(30)
    fun `creation from a non-service thread quotes the service rule`() {
        val service = start()
        try {
            val failure = assertFailsWith<IllegalStateException> { service.openWindow(config()) }
            assertTrue(failure.message.orEmpty().contains("service thread"), failure.message.orEmpty())
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `close from the owner worker queues destruction until the service pumps`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            onMain { window.releaseContext() }

            val failure = AtomicReference<Throwable?>(null)
            val worker =
                Thread(
                    {
                        try {
                            window.acquireContext()
                            window.close()
                        } catch (throwable: Throwable) {
                            failure.set(throwable)
                        }
                    },
                    "window-owner",
                )
            worker.start()
            worker.join(30_000)
            assertFalse(worker.isAlive, "owner worker did not finish")
            failure.get()?.let { throw it }

            assertTrue(window.isClosed)

            val awaitReturned = CountDownLatch(1)
            val awaitThread =
                Thread(
                    {
                        window.awaitClosed()
                        awaitReturned.countDown()
                    },
                    "close-await",
                )
            awaitThread.start()
            assertFalse(
                awaitReturned.await(200, TimeUnit.MILLISECONDS),
                "awaitClosed returned before the service pumped destruction",
            )

            onMain { service.pump() }

            assertTrue(awaitReturned.await(10, TimeUnit.SECONDS))
            window.awaitClosed()
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `event loop returns after the last registry entry is destroyed`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            onMain { window.releaseContext() }

            val loop = mainThread.submit(Callable { service.runEventLoop() })
            val failure = AtomicReference<Throwable?>(null)
            val worker =
                Thread(
                    {
                        try {
                            window.acquireContext()
                            window.close()
                        } catch (throwable: Throwable) {
                            failure.set(throwable)
                            window.close()
                        }
                    },
                    "window-owner",
                )
            worker.start()
            worker.join(30_000)
            assertFalse(worker.isAlive, "owner worker did not finish")

            loop.get(30, TimeUnit.SECONDS)
            failure.get()?.let { throw it }
            assertTrue(window.isClosed)
            window.awaitClosed()
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `double close is idempotent and later calls throw`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            onMain { window.close() }
            onMain { window.close() }

            assertTrue(window.isClosed)
            assertFailsWith<IllegalStateException> { window.setTitle("too late") }
            assertFailsWith<IllegalStateException> { window.setVisible(false) }
            assertFailsWith<IllegalStateException> { window.acquireContext() }
            assertFailsWith<IllegalStateException> { window.requestRepaint() }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `repaint requests coalesce and force repaint is independent`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                window.requestRepaint()
                window.requestRepaint()
                window.forceRepaint = true
                assertTrue(window.forceRepaint)
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `direct input state is readable off the owner thread`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                assertFalse(window.isKeyPressed(Key.UNKNOWN))
                assertTrue(window.cursorPosition().screenX.isFinite())
                assertTrue(window.cursorPosition().screenY.isFinite())
                val listener =
                    object : InputListener {
                        override fun onKey(event: KeyEvent) {
                            error("no input was injected")
                        }
                    }
                onMain { window.addInputListener(listener) }
                onMain { window.removeInputListener(listener) }
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `close requested flag is applied on the service thread`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                window.requestClose()
                onMain { service.pump() }
                assertTrue(window.shouldClose)

                window.setCloseRequested(false)
                onMain { service.pump() }
                assertFalse(window.shouldClose)
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
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
            title = "window lifecycle test",
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
