package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.internal.NativeGlfwBackend
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
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
internal class WindowCursorTest {
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
    fun `standard shapes custom cursors modes and icons apply`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                for (shape in CursorShape.entries) {
                    window.setCursor(shape)
                    onMain { service.pump() }
                }

                val first =
                    CursorImage.of(
                        2,
                        2,
                        0,
                        0,
                        intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt()),
                    )
                val second =
                    CursorImage.of(
                        2,
                        2,
                        1,
                        1,
                        intArrayOf(0x80112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(), 0xFFAABBCC.toInt()),
                    )

                window.setCursor(first)
                onMain { service.pump() }
                window.setCursor(second)
                onMain { service.pump() }
                window.setCursor(CursorShape.HAND)
                onMain { service.pump() }

                window.setCursorVisible(false)
                onMain { service.pump() }
                window.setCursorMode(CursorMode.DISABLED)
                onMain { service.pump() }
                window.setCursorVisible(true)
                onMain { service.pump() }
                window.setCursorMode(CursorMode.NORMAL)
                onMain { service.pump() }

                val icon = WindowIcon.of(1, 1, intArrayOf(0xFF102030.toInt()))
                val largeIcon =
                    WindowIcon.of(
                        2,
                        2,
                        intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt()),
                    )
                window.setIcon(icon, largeIcon)
                onMain { service.pump() }
                window.clearIcon()
                onMain { service.pump() }

                assertFailsWith<IllegalArgumentException> { window.setIcon() }
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `cursor and icon setters are safe from an application thread`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            val failure = AtomicReference<Throwable?>(null)
            try {
                val caller =
                    Thread(
                        {
                            try {
                                window.setCursor(CursorShape.IBEAM)
                                window.setCursorVisible(false)
                                window.setIcon(WindowIcon.of(1, 1, intArrayOf(0xFF102030.toInt())))
                            } catch (throwable: Throwable) {
                                failure.set(throwable)
                            }
                        },
                        "cursor-caller",
                    )
                caller.start()
                caller.join(30_000)
                check(!caller.isAlive) { "cursor caller did not finish" }
                failure.get()?.let { throw it }
                onMain { service.pump() }
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `native cursor and icon operations run on the service thread`() {
        val backend = RecordingBackend()
        val service = onMain { TerminalWindowService.initialize(GlfwOwnership.OWNED, backend) }
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                val caller =
                    Thread(
                        {
                            window.setCursor(CursorShape.HRESIZE)
                            window.setCursor(CursorImage.of(1, 1, 0, 0, intArrayOf(0xFF102030.toInt())))
                            window.setCursor(CursorShape.VRESIZE)
                            window.setCursorVisible(false)
                            window.setIcon(WindowIcon.of(1, 1, intArrayOf(0xFF102030.toInt())))
                            window.clearIcon()
                        },
                        "cursor-requester",
                    )
                caller.start()
                caller.join(30_000)
                check(!caller.isAlive) { "cursor requester did not finish" }
                onMain { service.pump() }

                assertTrue(backend.operations.isNotEmpty())
                assertTrue(
                    backend.operations.all { it.second == "main" },
                    "native cursor/icon work ran off the service thread: ${backend.operations}",
                )
                assertEquals(2, backend.operations.count { it.first == "createStandardCursor" })
                assertEquals(1, backend.operations.count { it.first == "createCursor" })
                assertEquals(1, backend.operations.count { it.first == "destroyCursor" })
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `closing a window destroys its custom cursor`() {
        val backend = RecordingBackend()
        val service = onMain { TerminalWindowService.initialize(GlfwOwnership.OWNED, backend) }
        try {
            val window = onMain { service.openWindow(config()) }
            window.setCursor(CursorImage.of(1, 1, 0, 0, intArrayOf(0xFF102030.toInt())))
            onMain { service.pump() }
            onMain { window.close() }
            onMain { service.pump() }

            assertEquals(1, backend.operations.count { it.first == "destroyCursor" })
            assertEquals(
                "main",
                backend.operations.first { it.first == "destroyCursor" }.second,
            )
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `cursor and icon setters reject a closed window`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            onMain { window.close() }
            onMain { window.close() }

            assertFailsWith<IllegalStateException> { window.setCursor(CursorShape.ARROW) }
            assertFailsWith<IllegalStateException> { window.setCursorVisible(false) }
            assertFailsWith<IllegalStateException> { window.setCursorMode(CursorMode.HIDDEN) }
            assertFailsWith<IllegalStateException> {
                window.setIcon(WindowIcon.of(1, 1, intArrayOf(0xFF102030.toInt())))
            }
            assertFailsWith<IllegalStateException> { window.clearIcon() }
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

    private class RecordingBackend(
        private val delegate: GlfwBackend = NativeGlfwBackend,
    ) : GlfwBackend by delegate {
        val operations: ConcurrentLinkedQueue<Pair<String, String>> = ConcurrentLinkedQueue()

        private fun record(operation: String) {
            operations.add(operation to Thread.currentThread().name)
        }

        override fun createStandardCursor(shape: Int): Long {
            record("createStandardCursor")
            return delegate.createStandardCursor(shape)
        }

        override fun createCursor(
            width: Int,
            height: Int,
            hotspotX: Int,
            hotspotY: Int,
            rgba: ByteArray,
        ): Long {
            record("createCursor")
            return delegate.createCursor(width, height, hotspotX, hotspotY, rgba)
        }

        override fun destroyCursor(cursor: Long) {
            record("destroyCursor")
            delegate.destroyCursor(cursor)
        }

        override fun setCursor(
            window: Long,
            cursor: Long,
        ) {
            record("setCursor")
            delegate.setCursor(window, cursor)
        }

        override fun setInputMode(
            window: Long,
            mode: Int,
            value: Int,
        ) {
            record("setInputMode")
            delegate.setInputMode(window, mode, value)
        }

        override fun setWindowIcon(
            window: Long,
            icons: List<WindowIcon>,
        ) {
            record("setWindowIcon")
            delegate.setWindowIcon(window, icons)
        }
    }

    private fun config(): TerminalWindowConfig =
        TerminalWindowConfig(
            font = font,
            title = "window cursor test",
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
