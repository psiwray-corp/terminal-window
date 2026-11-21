package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.internal.NativeGlfwBackend
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ClipboardTest {
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
    fun `clipboard round trips on the service thread`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                val text = "Grüße, 世界 😀"
                val results = mutableListOf<String?>()
                window.addClipboardListener { results.add(it) }
                onMain {
                    window.setClipboardText(text)
                    assertEquals(text, window.clipboardText())
                    window.requestClipboardText()
                }
                onMain { window.drainEvents() }

                assertEquals(listOf<String?>(text), results)
                assertFailsWith<IllegalStateException> { window.clipboardText() }
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `clipboard requests deliver text on the owner thread`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                onMain { window.setClipboardText("first") }
                val results = mutableListOf<String?>()
                val threads = mutableListOf<String>()
                window.addClipboardListener { text ->
                    results.add(text)
                    threads.add(Thread.currentThread().name)
                }

                val producer =
                    Thread(
                        {
                            window.setClipboardText("😀 second")
                            window.requestClipboardText()
                        },
                        "clipboard-producer",
                    ).apply { isDaemon = true }
                producer.start()
                producer.join(5_000)
                onMain { service.pump() }
                onMain { window.drainEvents() }

                assertEquals(listOf("😀 second"), results.toList())
                assertEquals(listOf("main"), threads.toList())
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `clipboard access calls GLFW only on the service thread`() {
        val backend = RecordingBackend()
        val service =
            onMain {
                preferX11()
                TerminalWindowService.initialize(GlfwOwnership.OWNED, backend)
            }
        try {
            val window = onMain { service.openWindow(config()) }
            try {
                val results = mutableListOf<String?>()
                window.addClipboardListener { results.add(it) }
                window.setClipboardText("thread check")
                window.requestClipboardText()
                onMain { service.pump() }
                onMain { window.drainEvents() }

                assertEquals(listOf("thread check"), results.toList())
                assertEquals(
                    listOf("setClipboardString:main", "getClipboardString:main"),
                    backend.operations.toList(),
                )
            } finally {
                closeWindow(window)
            }
        } finally {
            closeService(service)
        }
    }

    @Test
    @Timeout(60)
    fun `clipboard calls reject NUL text and closed windows`() {
        val service = start()
        try {
            val window = onMain { service.openWindow(config()) }
            assertFailsWith<IllegalArgumentException> { window.setClipboardText("a\u0000b") }

            onMain { window.close() }

            assertFailsWith<IllegalStateException> { window.setClipboardText("x") }
            assertFailsWith<IllegalStateException> { window.requestClipboardText() }
            assertFailsWith<IllegalStateException> { window.addClipboardListener { } }
            assertFailsWith<IllegalStateException> { window.removeClipboardListener { } }
        } finally {
            closeService(service)
        }
    }

    private fun start(): TerminalWindowService =
        onMain {
            preferX11()
            TerminalWindowService.initialize()
        }

    /**
     * Forces GLFW's X11 backend before initialization when an X display exists.
     *
     * Wayland clipboard reads need a focused window and a compositor data offer,
     * so hidden test windows cannot round-trip text there. The examples make the
     * same Linux choice; CI runs GLFW on Xvfb.
     */
    private fun preferX11() {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("linux") && !System.getenv("DISPLAY").isNullOrBlank()) {
            GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11)
        }
    }

    private fun closeWindow(window: TerminalWindow) {
        onMain { window.close() }
    }

    private fun closeService(service: TerminalWindowService) {
        onMain { service.close() }
    }

    private class RecordingBackend(
        private val delegate: GlfwBackend = NativeGlfwBackend,
    ) : GlfwBackend by delegate {
        val operations: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

        override fun getClipboardString(window: Long): String? {
            operations.add("getClipboardString:${Thread.currentThread().name}")
            return delegate.getClipboardString(window)
        }

        override fun setClipboardString(
            window: Long,
            text: String,
        ) {
            operations.add("setClipboardString:${Thread.currentThread().name}")
            delegate.setClipboardString(window, text)
        }
    }

    private fun config(): TerminalWindowConfig =
        TerminalWindowConfig(
            font = font,
            title = "clipboard test",
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
