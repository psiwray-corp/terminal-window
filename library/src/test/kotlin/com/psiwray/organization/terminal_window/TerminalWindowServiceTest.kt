package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.internal.CursorSample
import com.psiwray.organization.terminal_window.internal.FramebufferSize
import com.psiwray.organization.terminal_window.internal.WindowContentScale
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TerminalWindowServiceTest {
    private val mainThread =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "main").apply { isDaemon = true }
        }

    @AfterAll
    fun stopMainThread() {
        mainThread.shutdownNow()
    }

    @Test
    fun `initialize rejects a non-main thread with the GLFW rule`() {
        val failure =
            assertFailsWith<IllegalStateException> {
                TerminalWindowService.initialize(GlfwOwnership.OWNED, FakeBackend())
            }

        assertContains(failure.message.orEmpty(), "process main thread")
    }

    @Test
    fun `reference count initializes once per call and terminates once`() {
        val backend = FakeBackend()
        val first = start(backend)
        val second = start(backend)
        try {
            assertEquals(2, backend.initCalls.get())

            close(first)
            assertEquals(0, backend.terminateCalls.get())
            close(second)
            assertEquals(1, backend.terminateCalls.get())

            val afterTermination = start(backend)
            try {
                assertEquals(3, backend.initCalls.get())
            } finally {
                close(afterTermination)
            }
            assertEquals(2, backend.terminateCalls.get())
        } finally {
            close(first)
            close(second)
        }
    }

    @Test
    fun `double close is a no-op`() {
        val backend = FakeBackend()
        val service = start(backend)
        try {
            close(service)
            close(service)
        } finally {
            close(service)
        }

        assertEquals(1, backend.initCalls.get())
        assertEquals(1, backend.terminateCalls.get())
    }

    @Test
    fun `EXTERNAL mode neither initializes nor terminates GLFW`() {
        val backend = FakeBackend()
        val service = start(backend, GlfwOwnership.EXTERNAL)
        try {
            assertEquals(0, backend.initCalls.get())
            assertEquals(0, backend.terminateCalls.get())
        } finally {
            close(service)
        }

        assertEquals(0, backend.initCalls.get())
        assertEquals(0, backend.terminateCalls.get())
    }

    @Test
    fun `mixing ownership modes while active is rejected`() {
        val backend = FakeBackend()
        val owned = start(backend)
        try {
            val failure =
                assertFailsWith<IllegalStateException> {
                    onMain { TerminalWindowService.initialize(GlfwOwnership.EXTERNAL, backend) }
                }
            assertContains(failure.message.orEmpty(), "cannot switch")
        } finally {
            close(owned)
        }

        val externalBackend = FakeBackend()
        val external = start(externalBackend, GlfwOwnership.EXTERNAL)
        try {
            assertEquals(0, externalBackend.initCalls.get())
            assertEquals(0, externalBackend.terminateCalls.get())
        } finally {
            close(external)
        }
        assertEquals(0, externalBackend.terminateCalls.get())
    }

    @Test
    fun `init failure maps to GlfwException and rolls back the lifecycle`() {
        val backend =
            FakeBackend().apply {
                initSucceeds = false
                nextError = GlfwError(0x00010001, "initialization exploded")
            }

        val failure =
            assertFailsWith<GlfwException> {
                onMain { TerminalWindowService.initialize(GlfwOwnership.OWNED, backend) }
            }

        assertEquals(0x00010001, failure.code)
        assertEquals("initialization exploded", failure.description)
        assertContains(failure.message.orEmpty(), "initialization exploded")
        assertEquals(1, backend.initCalls.get())
        assertEquals(0, backend.terminateCalls.get())

        backend.initSucceeds = true
        val service = start(backend)
        try {
            assertEquals(2, backend.initCalls.get())
        } finally {
            close(service)
        }
        assertEquals(1, backend.terminateCalls.get())
    }

    @Test
    fun `post-init error terminates a first initialization before rollback`() {
        val backend =
            FakeBackend().apply {
                nextError = GlfwError(0x00010003, "post-init exploded")
            }

        val failure =
            assertFailsWith<GlfwException> {
                onMain { TerminalWindowService.initialize(GlfwOwnership.OWNED, backend) }
            }

        assertEquals(0x00010003, failure.code)
        assertEquals(1, backend.initCalls.get())
        assertEquals(1, backend.terminateCalls.get())

        val service = start(backend)
        try {
            assertEquals(2, backend.initCalls.get())
        } finally {
            close(service)
        }
        assertEquals(2, backend.terminateCalls.get())
    }

    @Test
    fun `pump maps a GLFW error from event polling`() {
        val backend = FakeBackend()
        val service = start(backend)
        try {
            backend.errorOnPoll = GlfwError(0x00010002, "poll exploded")
            val failure =
                assertFailsWith<GlfwException> {
                    onMain { service.pump() }
                }

            assertEquals(0x00010002, failure.code)
            assertEquals("poll exploded", failure.description)

            backend.errorOnPoll = null
            onMain { service.pump() }
        } finally {
            close(service)
        }
    }

    @Test
    fun `state op runs only when the service thread pumps`() {
        val backend = FakeBackend()
        val applied = AtomicBoolean(false)
        val service = start(backend)
        try {
            service.enqueueStateOp { applied.set(true) }
            assertFalse(applied.get())

            onMain { service.pump() }

            assertTrue(applied.get())
            assertEquals(1, backend.pollCalls.get())
        } finally {
            close(service)
        }
    }

    @Test
    fun `destroy request survives a large state backlog`() {
        val backend = FakeBackend()
        val destroyed = AtomicBoolean(false)
        val service = start(backend)
        try {
            repeat(5_000) {
                service.enqueueStateOp { }
            }
            service.enqueueDestroyOp { destroyed.set(true) }

            onMain { service.pump() }

            assertTrue(destroyed.get())
            assertEquals(1, backend.pollCalls.get())
        } finally {
            close(service)
        }
    }

    @Test
    fun `close drains destroy requests and rejects new ones`() {
        val backend = FakeBackend()
        val destroyed = AtomicBoolean(false)
        val service = start(backend)
        service.enqueueDestroyOp { destroyed.set(true) }

        close(service)

        assertTrue(destroyed.get())
        assertEquals(1, backend.terminateCalls.get())
        assertFailsWith<IllegalStateException> {
            service.enqueueDestroyOp { }
        }
    }

    private fun start(
        backend: FakeBackend,
        ownership: GlfwOwnership = GlfwOwnership.OWNED,
    ): TerminalWindowService = onMain { TerminalWindowService.initialize(ownership, backend) }

    private fun close(service: TerminalWindowService) {
        onMain { service.close() }
    }

    private fun <T> onMain(block: () -> T): T {
        val future = mainThread.submit(Callable { block() })
        return try {
            future.get(10, TimeUnit.SECONDS)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (failure: TimeoutException) {
            future.cancel(true)
            throw AssertionError("main-thread task timed out", failure)
        }
    }

    private class FakeBackend : GlfwBackend {
        val initCalls: AtomicInteger = AtomicInteger(0)
        val terminateCalls: AtomicInteger = AtomicInteger(0)
        val pollCalls: AtomicInteger = AtomicInteger(0)
        val waitCalls: AtomicInteger = AtomicInteger(0)

        @Volatile
        var initSucceeds: Boolean = true

        @Volatile
        var nextError: GlfwError? = null

        @Volatile
        var errorOnPoll: GlfwError? = null

        override fun init(): Boolean {
            initCalls.incrementAndGet()
            return initSucceeds
        }

        override fun terminate() {
            terminateCalls.incrementAndGet()
        }

        override fun pollEvents() {
            pollCalls.incrementAndGet()
            errorOnPoll?.let { nextError = it }
        }

        override fun waitEvents(timeout: Duration) {
            waitCalls.incrementAndGet()
            errorOnPoll?.let { nextError = it }
        }

        override fun takeError(): GlfwError? {
            val error = nextError
            nextError = null
            return error
        }

        override fun defaultWindowHints(): Unit = unsupported()

        override fun windowHint(
            hint: Int,
            value: Int,
        ): Unit = unsupported()

        override fun createWindow(
            width: Int,
            height: Int,
            title: String,
        ): Long = unsupported()

        override fun destroyWindow(window: Long): Unit = unsupported()

        override fun makeContextCurrent(window: Long): Unit = unsupported()

        override fun swapBuffers(window: Long): Unit = unsupported()

        override fun swapInterval(interval: Int): Unit = unsupported()

        override fun windowShouldClose(window: Long): Boolean = unsupported()

        override fun setWindowShouldClose(
            window: Long,
            requested: Boolean,
        ): Unit = unsupported()

        override fun setWindowTitle(
            window: Long,
            title: String,
        ): Unit = unsupported()

        override fun setWindowIcon(
            window: Long,
            icons: List<WindowIcon>,
        ): Unit = unsupported()

        override fun createStandardCursor(shape: Int): Long = unsupported()

        override fun createCursor(
            width: Int,
            height: Int,
            hotspotX: Int,
            hotspotY: Int,
            rgba: ByteArray,
        ): Long = unsupported()

        override fun destroyCursor(cursor: Long): Unit = unsupported()

        override fun setCursor(
            window: Long,
            cursor: Long,
        ): Unit = unsupported()

        override fun setInputMode(
            window: Long,
            mode: Int,
            value: Int,
        ): Unit = unsupported()

        override fun showWindow(window: Long): Unit = unsupported()

        override fun hideWindow(window: Long): Unit = unsupported()

        override fun setWindowResizable(
            window: Long,
            resizable: Boolean,
        ): Unit = unsupported()

        override fun framebufferSize(window: Long): FramebufferSize = unsupported()

        override fun contentScale(window: Long): WindowContentScale = unsupported()

        override fun setWindowUserPointer(
            window: Long,
            pointer: Long,
        ): Unit = unsupported()

        override fun windowUserPointer(window: Long): Long = unsupported()

        override fun setKeyCallback(
            window: Long,
            callback: ((Long, Int, Int, Int, Int) -> Unit)?,
        ): Unit = unsupported()

        override fun setCharCallback(
            window: Long,
            callback: ((Long, Int) -> Unit)?,
        ): Unit = unsupported()

        override fun setCursorPosCallback(
            window: Long,
            callback: ((Long, Double, Double) -> Unit)?,
        ): Unit = unsupported()

        override fun setCursorEnterCallback(
            window: Long,
            callback: ((Long, Boolean) -> Unit)?,
        ): Unit = unsupported()

        override fun setMouseButtonCallback(
            window: Long,
            callback: ((Long, Int, Int, Int) -> Unit)?,
        ): Unit = unsupported()

        override fun setScrollCallback(
            window: Long,
            callback: ((Long, Double, Double) -> Unit)?,
        ): Unit = unsupported()

        override fun setFramebufferSizeCallback(
            window: Long,
            callback: ((Long, Int, Int) -> Unit)?,
        ): Unit = unsupported()

        override fun setWindowContentScaleCallback(
            window: Long,
            callback: ((Long, Float, Float) -> Unit)?,
        ): Unit = unsupported()

        override fun setWindowFocusCallback(
            window: Long,
            callback: ((Long, Boolean) -> Unit)?,
        ): Unit = unsupported()

        override fun setWindowCloseCallback(
            window: Long,
            callback: ((Long) -> Unit)?,
        ): Unit = unsupported()

        override fun getKey(
            window: Long,
            key: Int,
        ): Int = unsupported()

        override fun getMouseButton(
            window: Long,
            button: Int,
        ): Int = unsupported()

        override fun cursorPosition(window: Long): CursorSample = unsupported()

        private fun unsupported(): Nothing = error("FakeBackend does not create windows")
    }
}
