package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.internal.CursorHandle
import com.psiwray.organization.terminal_window.internal.CursorSample
import com.psiwray.organization.terminal_window.internal.FramebufferSize
import com.psiwray.organization.terminal_window.internal.NativeGlfwBackend
import com.psiwray.organization.terminal_window.internal.ServiceThread
import com.psiwray.organization.terminal_window.internal.WindowContentScale
import com.psiwray.organization.terminal_window.internal.WindowRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * Who owns the process-global GLFW initialization.
 *
 * [OWNED] means the service calls `glfwInit` and `glfwTerminate`.
 * [EXTERNAL] means the application already initialized GLFW; the service never
 * initializes or terminates it.
 *
 * @since 2.0.0
 */
public enum class GlfwOwnership {
    /** The service owns GLFW initialization and termination. @since 2.0.0 */
    OWNED,

    /** The application owns GLFW initialization; the service never terminates it. @since 2.0.0 */
    EXTERNAL,
}

/** Internal error payload copied from GLFW before a checked operation returns. */
internal data class GlfwError(
    val code: Int,
    val description: String,
) {
    fun asException(): GlfwException = GlfwException(code, description)
}

/**
 * The GLFW operations the service needs, isolated so lifecycle and queue
 * behavior can be tested without a display.
 */
internal interface GlfwBackend {
    fun init(): Boolean

    fun terminate()

    fun pollEvents()

    fun waitEvents(timeout: Duration)

    fun takeError(): GlfwError?

    fun defaultWindowHints()

    fun windowHint(
        hint: Int,
        value: Int,
    )

    fun createWindow(
        width: Int,
        height: Int,
        title: String,
    ): Long

    fun destroyWindow(window: Long)

    fun makeContextCurrent(window: Long)

    fun swapBuffers(window: Long)

    fun swapInterval(interval: Int)

    fun windowShouldClose(window: Long): Boolean

    fun setWindowShouldClose(
        window: Long,
        requested: Boolean,
    )

    fun setWindowTitle(
        window: Long,
        title: String,
    )

    fun setWindowIcon(
        window: Long,
        icons: List<WindowIcon>,
    )

    /** Reads the UTF-8 clipboard text; null when the clipboard holds no text. */
    fun getClipboardString(window: Long): String?

    /** Writes [text] to the clipboard. */
    fun setClipboardString(
        window: Long,
        text: String,
    )

    fun createStandardCursor(shape: Int): Long

    fun createCursor(
        width: Int,
        height: Int,
        hotspotX: Int,
        hotspotY: Int,
        rgba: ByteArray,
    ): Long

    fun destroyCursor(cursor: Long)

    fun setCursor(
        window: Long,
        cursor: Long,
    )

    fun setInputMode(
        window: Long,
        mode: Int,
        value: Int,
    )

    fun showWindow(window: Long)

    fun hideWindow(window: Long)

    fun setWindowResizable(
        window: Long,
        resizable: Boolean,
    )

    fun framebufferSize(window: Long): FramebufferSize

    fun contentScale(window: Long): WindowContentScale

    fun setWindowUserPointer(
        window: Long,
        pointer: Long,
    )

    fun windowUserPointer(window: Long): Long

    fun setKeyCallback(
        window: Long,
        callback: ((Long, Int, Int, Int, Int) -> Unit)?,
    )

    fun setCharCallback(
        window: Long,
        callback: ((Long, Int) -> Unit)?,
    )

    fun setCursorPosCallback(
        window: Long,
        callback: ((Long, Double, Double) -> Unit)?,
    )

    fun setCursorEnterCallback(
        window: Long,
        callback: ((Long, Boolean) -> Unit)?,
    )

    fun setMouseButtonCallback(
        window: Long,
        callback: ((Long, Int, Int, Int) -> Unit)?,
    )

    fun setScrollCallback(
        window: Long,
        callback: ((Long, Double, Double) -> Unit)?,
    )

    fun setFramebufferSizeCallback(
        window: Long,
        callback: ((Long, Int, Int) -> Unit)?,
    )

    fun setWindowContentScaleCallback(
        window: Long,
        callback: ((Long, Float, Float) -> Unit)?,
    )

    fun setWindowFocusCallback(
        window: Long,
        callback: ((Long, Boolean) -> Unit)?,
    )

    fun setWindowCloseCallback(
        window: Long,
        callback: ((Long) -> Unit)?,
    )

    fun getKey(
        window: Long,
        key: Int,
    ): Int

    fun getMouseButton(
        window: Long,
        button: Int,
    ): Int

    fun cursorPosition(window: Long): CursorSample
}

/**
 * Process-global GLFW service for the thread that calls `main`.
 *
 * The service captures the calling thread as the only legal service thread,
 * owns the GLFW lifetime through a reference count, serializes service-thread
 * work through two low-frequency queues, and owns the registry of open
 * windows. [openWindow] creates windows on the service thread; their contexts
 * are transferred explicitly by the owner.
 *
 * Construct instances with [initialize], call [pump] only on the service thread,
 * and call [close] on the service thread. Every public operation checks the
 * thread contract before touching GLFW.
 *
 * @throws IllegalStateException if initialized or used from a non-main thread.
 * @throws GlfwException if GLFW reports an error while the service operations
 *   are being checked.
 * @since 2.0.0
 */
public class TerminalWindowService private constructor(
    private val serviceThread: Thread,
    private val backend: GlfwBackend,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)

    /**
     * Non-terminal window-state work waiting for the service thread.
     *
     * Producers may be any thread. The only consumer is the service thread in
     * [pump], which drains this queue to exhaustion before polling GLFW and
     * before draining [destroyOps]. FIFO order is preserved within the queue,
     * and an operation may enqueue more work that is handled in the same pump.
     *
     * The queue is unbounded and low-frequency. Operations are not coalesced:
     * every accepted operation runs, even if a later one overwrites the same
     * window state.
     *
     * If an operation throws, draining stops at that operation; the remaining
     * state operations and all destroy operations wait for the next pump.
     * [close] intentionally does not drain this queue: state work still pending
     * when shutdown begins is the caller's responsibility.
     */
    private val stateOps: ConcurrentLinkedQueue<() -> Unit> = ConcurrentLinkedQueue()

    /**
     * Terminal window-destruction work waiting for the service thread.
     *
     * Producers may be any thread. [pump] drains this queue after [stateOps]
     * and before polling; [close] drains it even though it skips [stateOps].
     * FIFO order is preserved, and an operation may enqueue further destroy
     * work that is handled by the same drain loop.
     *
     * Unlike state work, destroy work is never dropped: exceptions do not stop
     * the drain. The first exception is rethrown after every request has run.
     * New requests are rejected once [close] has marked the service closed.
     *
     * The queue is unbounded and low-frequency; it is not a substitute for the
     * window registry, which owns the identity of each window being destroyed.
     */
    private val destroyOps: ConcurrentLinkedQueue<() -> Unit> = ConcurrentLinkedQueue()

    /**
     * Service-thread-owned identity map for open windows.
     *
     * This is the source of truth for [runEventLoop] termination and for
     * per-window callback routing. It is confined to the service thread and is
     * not synchronized.
     */
    private val windows: WindowRegistry = WindowRegistry()

    private val queueLock: Any = Any()

    /** Factory and process-global lifecycle state for the service. */
    public companion object {
        private val lifecycleLock: Any = Any()
        private val refCount: AtomicInteger = AtomicInteger(0)

        @Volatile
        private var activeMode: GlfwOwnership? = null

        @Volatile
        private var activeBackend: GlfwBackend? = null

        /**
         * Initializes GLFW or joins the existing process-global initialization.
         *
         * The caller must be the process main thread. [GlfwOwnership.OWNED]
         * calls `glfwInit`; [GlfwOwnership.EXTERNAL] assumes the application
         * already did so. Multiple calls on that same thread increment the
         * reference count.
         *
         * @throws IllegalStateException if called from a non-main thread or
         *   while a different ownership mode is active.
         * @throws GlfwException if `glfwInit` fails and GLFW reports an error.
         * @since 2.0.0
         */
        @JvmStatic
        @JvmOverloads
        public fun initialize(ownership: GlfwOwnership = GlfwOwnership.OWNED): TerminalWindowService =
            initialize(ownership, NativeGlfwBackend)

        internal fun initialize(
            ownership: GlfwOwnership,
            backend: GlfwBackend,
        ): TerminalWindowService {
            ServiceThread.requireMainThread()
            val current = Thread.currentThread()
            synchronized(lifecycleLock) {
                val alreadyActive = refCount.get() > 0
                if (alreadyActive) {
                    check(activeMode == ownership) {
                        "GLFW is already owned as $activeMode; cannot switch to $ownership while active"
                    }
                    check(ServiceThread.ownerThread() === current) {
                        "GLFW service operations must run on the service thread " +
                            "'${ServiceThread.ownerThread()?.name}'; initialize() was called from '${current.name}'"
                    }
                } else {
                    ServiceThread.bind(current)
                    activeMode = ownership
                    activeBackend = backend
                }

                val selected = activeBackend ?: error("GLFW service has no active backend")
                var initialized = false
                try {
                    if (ownership == GlfwOwnership.OWNED) {
                        if (!selected.init()) {
                            val error = selected.takeError()
                            if (error != null) {
                                throw error.asException()
                            }
                            throw IllegalStateException("glfwInit failed; GLFW reported no error")
                        }
                        initialized = true
                    }
                    selected.takeError()?.let { throw it.asException() }
                } catch (failure: Throwable) {
                    if (!alreadyActive) {
                        if (initialized && ownership == GlfwOwnership.OWNED) {
                            try {
                                selected.terminate()
                            } catch (terminateFailure: Throwable) {
                                failure.addSuppressed(terminateFailure)
                            }
                        }
                        rollback()
                    }
                    throw failure
                }

                refCount.incrementAndGet()
                return TerminalWindowService(current, selected)
            }
        }

        private fun rollback() {
            activeMode = null
            activeBackend = null
            refCount.set(0)
            ServiceThread.unbind()
        }
    }

    /**
     * Creates one GLFW window and OpenGL context on the service thread.
     *
     * The new window starts registered with this service and is included in
     * [runEventLoop] termination and per-window state refreshes. Its context is
     * current on the calling service thread until it is released for transfer
     * to an owner thread.
     *
     * @throws IllegalStateException if the service is closed or the caller is
     *   not the service thread.
     * @throws GlfwException if GLFW reports an error during creation.
     * @throws GlException if GLFW creates a context that does not satisfy the
     *   requested OpenGL core version.
     * @since 2.0.0
     */
    public fun openWindow(config: TerminalWindowConfig): TerminalWindow {
        check(!closed.get()) { "TerminalWindowService is closed" }
        ServiceThread.assertServiceThread(serviceThread, "openWindow")
        val window = TerminalWindow.create(this, backend, config)
        windows.add(window)
        return window
    }

    /**
     * Polls GLFW once and drains queued state and destroy operations.
     *
     * Call this only from the service thread. In single-threaded mode this is
     * called automatically by the future `TerminalWindow.frame()`; in
     * multi-threaded mode the service thread runs this method in its loop.
     * @since 2.0.0
     */
    public fun pump() {
        pumpInternal(null)
    }

    /**
     * Waits for GLFW events for at most [timeout], then drains queued work.
     *
     * A zero timeout behaves like [pump]. The timeout must be finite and
     * non-negative. Call this only from the service thread.
     * @since 2.0.0
     */
    public fun pump(timeout: Duration) {
        require(timeout >= Duration.ZERO && timeout != Duration.INFINITE) {
            "timeout must be finite and non-negative: $timeout"
        }
        pumpInternal(timeout)
    }

    private fun pumpInternal(timeout: Duration?) {
        check(!closed.get()) { "TerminalWindowService is closed" }
        ServiceThread.assertServiceThread(serviceThread, "pump")
        throwPendingError()

        drainStateOps()
        drainDestroyOps()?.let { throw it }

        if (timeout == null || timeout == Duration.ZERO) {
            backend.pollEvents()
        } else {
            backend.waitEvents(timeout)
        }
        throwPendingError()
        windows.forEach { window ->
            window.refreshShouldClose()
            window.refreshDeviceState()
        }
    }

    /**
     * Pumps events until the last window opened through this service is closed.
     *
     * This is the multi-threaded service-loop entry point. It returns after the
     * final window destroy has been drained, so the caller's `main` method can
     * return and the JVM can exit exactly as it does after the last Swing
     * window is disposed. It does not call `System.exit` and does not stop any
     * other thread.
     *
     * Call this only from the service thread, after opening at least one window.
     * If no window is currently open, it returns immediately.
     * @since 2.0.0
     */
    public fun runEventLoop() {
        ServiceThread.assertServiceThread(serviceThread, "runEventLoop")
        while (!closed.get() && !windows.isEmpty()) {
            pump()
        }
    }

    private fun drainStateOps() {
        while (true) {
            val operation = stateOps.poll() ?: return
            operation()
            throwPendingError()
        }
    }

    private fun drainDestroyOps(): Throwable? {
        var failure: Throwable? = null
        while (true) {
            val operation = destroyOps.poll() ?: return failure
            try {
                operation()
                throwPendingError()
            } catch (failureFromOperation: Throwable) {
                if (failure == null) {
                    failure = failureFromOperation
                }
            }
        }
    }

    private fun throwPendingError() {
        backend.takeError()?.let { throw it.asException() }
    }

    /**
     * Queues a window-state operation for the service thread.
     *
     * The operation is applied by the next [pump] call before event polling.
     * The request is rejected rather than dropped after [close] begins.
     */
    internal fun enqueueStateOp(operation: () -> Unit) {
        synchronized(queueLock) {
            check(!closed.get()) { "TerminalWindowService is closed" }
            stateOps.add(operation)
        }
    }

    /**
     * Queues a destroy operation for the service thread.
     *
     * Destroy requests are never dropped: they remain queued until a [pump] or
     * [close] drains them, and new requests are rejected after the service is
     * closed.
     */
    internal fun enqueueDestroyOp(operation: () -> Unit) {
        synchronized(queueLock) {
            check(!closed.get()) { "TerminalWindowService is closed" }
            destroyOps.add(operation)
        }
    }

    /**
     * Removes a destroyed window from the registry.
     *
     * Called on the service thread after `glfwDestroyWindow`. It remains legal
     * while [close] is draining queued destroy work.
     */
    internal fun windowClosed(handle: Long) {
        ServiceThread.assertServiceThread(serviceThread, "windowClosed")
        windows.remove(handle)
    }

    /**
     * Drains queued destroy work and releases this service reference.
     *
     * At reference count zero the owned GLFW instance is terminated. The second
     * and later calls on this instance are no-ops. Call this only from the
     * service thread.
     * @since 2.0.0
     */
    override fun close() {
        if (closed.get()) {
            return
        }
        ServiceThread.assertServiceThread(serviceThread, "close")

        synchronized(queueLock) {
            if (!closed.compareAndSet(false, true)) {
                return
            }
        }

        var failure = drainDestroyOps()
        synchronized(lifecycleLock) {
            val remaining = refCount.decrementAndGet()
            check(remaining >= 0) { "GLFW service reference count underflow" }
            if (remaining == 0) {
                try {
                    if (windows.isEmpty()) {
                        CursorHandle.releaseStandardCursors(backend)
                    }
                } catch (cursorFailure: Throwable) {
                    if (failure == null) {
                        failure = cursorFailure
                    }
                }
                try {
                    if (activeMode == GlfwOwnership.OWNED) {
                        backend.terminate()
                    }
                } catch (terminationFailure: Throwable) {
                    if (failure == null) {
                        failure = terminationFailure
                    }
                } finally {
                    activeMode = null
                    activeBackend = null
                    ServiceThread.unbind()
                }
            }
        }
        failure?.let { throw it }
    }
}
