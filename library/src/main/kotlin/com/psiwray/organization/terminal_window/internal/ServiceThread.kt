package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.GlfwBackend
import com.psiwray.organization.terminal_window.GlfwError
import com.psiwray.organization.terminal_window.WindowIcon
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWCharCallback
import org.lwjgl.glfw.GLFWCursorEnterCallback
import org.lwjgl.glfw.GLFWCursorPosCallback
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.glfw.GLFWKeyCallback
import org.lwjgl.glfw.GLFWMouseButtonCallback
import org.lwjgl.glfw.GLFWScrollCallback
import org.lwjgl.glfw.GLFWWindowCloseCallback
import org.lwjgl.glfw.GLFWWindowContentScaleCallback
import org.lwjgl.glfw.GLFWWindowFocusCallback
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * The process-global GLFW service-thread identity and its main-thread guard.
 *
 * GLFW's main thread is identified by the conventional JVM main-thread name
 * `main`. The name is a guard against accidental background-thread use, not a
 * security boundary; the JVM does not expose an authoritative main-thread ID.
 */
internal object ServiceThread {
    private const val MAIN_THREAD_NAME: String = "main"

    @Volatile
    private var owner: Thread? = null

    /** Returns the thread that currently owns the active service, if any. */
    internal fun ownerThread(): Thread? = owner

    /** Captures [thread] as the sole service thread. */
    internal fun bind(thread: Thread) {
        check(owner == null) { "GLFW service thread is already bound to '${owner?.name}'" }
        owner = thread
    }

    /** Clears the service-thread binding after full termination. */
    internal fun unbind() {
        owner = null
    }

    /**
     * Rejects initialization from any thread other than the process main thread.
     *
     * @throws IllegalStateException with the GLFW thread rule in the message.
     */
    internal fun requireMainThread() {
        val current = Thread.currentThread()
        check(current.name == MAIN_THREAD_NAME) {
            "GLFW requires glfwInit, window operations, and event polling on the process main thread; " +
                "initialize() was called from '${current.name}'"
        }
    }

    /**
     * Rejects service operations from any thread other than [expected].
     *
     * @throws IllegalStateException naming the operation and both thread names.
     */
    internal fun assertServiceThread(
        expected: Thread,
        operation: String,
    ) {
        val current = Thread.currentThread()
        check(current === expected) {
            "$operation must run on the GLFW service thread '${expected.name}'; " +
                "current thread is '${current.name}'"
        }
    }
}

/**
 * The only production implementation that calls into LWJGL GLFW.
 *
 * Error text is copied immediately with `glfwGetError`; no process-wide error
 * callback is installed. See ADR-0004 and `docs/threading.md`.
 */
internal object NativeGlfwBackend : GlfwBackend {
    private val callbacks: MutableMap<Long, NativeCallbacks> = HashMap()

    override fun init(): Boolean = GLFW.glfwInit()

    override fun terminate() {
        GLFW.glfwTerminate()
    }

    override fun pollEvents() {
        GLFW.glfwPollEvents()
    }

    override fun waitEvents(timeout: Duration) {
        GLFW.glfwWaitEventsTimeout(timeout.toDouble(DurationUnit.SECONDS))
    }

    override fun takeError(): GlfwError? {
        val stack = MemoryStack.stackPush()
        try {
            val descriptionPointer = stack.mallocPointer(1)
            val code = GLFW.glfwGetError(descriptionPointer)
            if (code == GLFW.GLFW_NO_ERROR) {
                return null
            }
            val pointer = descriptionPointer.get(0)
            val description =
                if (pointer == MemoryUtil.NULL) {
                    "no description"
                } else {
                    GLFWErrorCallback.getDescription(pointer)
                }
            return GlfwError(code, description)
        } finally {
            stack.pop()
        }
    }

    override fun defaultWindowHints() {
        GLFW.glfwDefaultWindowHints()
    }

    override fun windowHint(
        hint: Int,
        value: Int,
    ) {
        GLFW.glfwWindowHint(hint, value)
    }

    override fun createWindow(
        width: Int,
        height: Int,
        title: String,
    ): Long = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)

    override fun destroyWindow(window: Long) {
        callbacks.remove(window)?.freeAll()
        GLFW.glfwDestroyWindow(window)
    }

    override fun makeContextCurrent(window: Long) {
        GLFW.glfwMakeContextCurrent(window)
    }

    override fun swapBuffers(window: Long) {
        GLFW.glfwSwapBuffers(window)
    }

    override fun swapInterval(interval: Int) {
        GLFW.glfwSwapInterval(interval)
    }

    override fun windowShouldClose(window: Long): Boolean = GLFW.glfwWindowShouldClose(window)

    override fun setWindowShouldClose(
        window: Long,
        requested: Boolean,
    ) {
        GLFW.glfwSetWindowShouldClose(window, requested)
    }

    override fun setWindowTitle(
        window: Long,
        title: String,
    ) {
        GLFW.glfwSetWindowTitle(window, title)
    }

    override fun setWindowIcon(
        window: Long,
        icons: List<WindowIcon>,
    ) {
        if (icons.isEmpty()) {
            GLFW.glfwSetWindowIcon(window, null)
            return
        }
        val stack = MemoryStack.stackPush()
        try {
            val images = GLFWImage.malloc(icons.size, stack)
            for (index in icons.indices) {
                val icon = icons[index]
                val image = images[index]
                image.width(icon.width)
                image.height(icon.height)
                val pixels = stack.malloc(icon.rgba.size)
                pixels.put(icon.rgba)
                pixels.flip()
                image.pixels(pixels)
            }
            GLFW.glfwSetWindowIcon(window, images)
        } finally {
            stack.pop()
        }
    }

    override fun createStandardCursor(shape: Int): Long = GLFW.glfwCreateStandardCursor(shape)

    override fun createCursor(
        width: Int,
        height: Int,
        hotspotX: Int,
        hotspotY: Int,
        rgba: ByteArray,
    ): Long {
        val stack = MemoryStack.stackPush()
        try {
            val image = GLFWImage.malloc(1, stack)[0]
            image.width(width)
            image.height(height)
            val pixels = stack.malloc(rgba.size)
            pixels.put(rgba)
            pixels.flip()
            image.pixels(pixels)
            return GLFW.glfwCreateCursor(image, hotspotX, hotspotY)
        } finally {
            stack.pop()
        }
    }

    override fun destroyCursor(cursor: Long) {
        GLFW.glfwDestroyCursor(cursor)
    }

    override fun setCursor(
        window: Long,
        cursor: Long,
    ) {
        GLFW.glfwSetCursor(window, cursor)
    }

    override fun setInputMode(
        window: Long,
        mode: Int,
        value: Int,
    ) {
        GLFW.glfwSetInputMode(window, mode, value)
    }

    override fun showWindow(window: Long) {
        GLFW.glfwShowWindow(window)
    }

    override fun hideWindow(window: Long) {
        GLFW.glfwHideWindow(window)
    }

    override fun setWindowResizable(
        window: Long,
        resizable: Boolean,
    ) {
        GLFW.glfwSetWindowAttrib(
            window,
            GLFW.GLFW_RESIZABLE,
            if (resizable) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE,
        )
    }

    override fun framebufferSize(window: Long): FramebufferSize {
        val stack = MemoryStack.stackPush()
        try {
            val width = stack.mallocInt(1)
            val height = stack.mallocInt(1)
            GLFW.glfwGetFramebufferSize(window, width, height)
            return FramebufferSize(width[0], height[0])
        } finally {
            stack.pop()
        }
    }

    override fun contentScale(window: Long): WindowContentScale {
        val stack = MemoryStack.stackPush()
        try {
            val x = stack.mallocFloat(1)
            val y = stack.mallocFloat(1)
            GLFW.glfwGetWindowContentScale(window, x, y)
            return WindowContentScale(x[0], y[0])
        } finally {
            stack.pop()
        }
    }

    override fun setWindowUserPointer(
        window: Long,
        pointer: Long,
    ) {
        GLFW.glfwSetWindowUserPointer(window, pointer)
    }

    override fun windowUserPointer(window: Long): Long = GLFW.glfwGetWindowUserPointer(window)

    override fun setKeyCallback(
        window: Long,
        callback: ((Long, Int, Int, Int, Int) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.key
        entry.key =
            if (callback == null) {
                GLFW.glfwSetKeyCallback(window, null)
                null
            } else {
                GLFWKeyCallback
                    .create { callbackWindow, key, scancode, action, mods ->
                        callback(callbackWindow, key, scancode, action, mods)
                    }.also { GLFW.glfwSetKeyCallback(window, it) }
            }
        previous?.free()
    }

    override fun setCharCallback(
        window: Long,
        callback: ((Long, Int) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.char
        entry.char =
            if (callback == null) {
                GLFW.glfwSetCharCallback(window, null)
                null
            } else {
                GLFWCharCallback
                    .create { callbackWindow, codePoint ->
                        callback(callbackWindow, codePoint)
                    }.also { GLFW.glfwSetCharCallback(window, it) }
            }
        previous?.free()
    }

    override fun setCursorPosCallback(
        window: Long,
        callback: ((Long, Double, Double) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.cursorPos
        entry.cursorPos =
            if (callback == null) {
                GLFW.glfwSetCursorPosCallback(window, null)
                null
            } else {
                GLFWCursorPosCallback
                    .create { callbackWindow, x, y ->
                        callback(callbackWindow, x, y)
                    }.also { GLFW.glfwSetCursorPosCallback(window, it) }
            }
        previous?.free()
    }

    override fun setCursorEnterCallback(
        window: Long,
        callback: ((Long, Boolean) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.cursorEnter
        entry.cursorEnter =
            if (callback == null) {
                GLFW.glfwSetCursorEnterCallback(window, null)
                null
            } else {
                GLFWCursorEnterCallback
                    .create { callbackWindow, entered ->
                        callback(callbackWindow, entered)
                    }.also { GLFW.glfwSetCursorEnterCallback(window, it) }
            }
        previous?.free()
    }

    override fun setMouseButtonCallback(
        window: Long,
        callback: ((Long, Int, Int, Int) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.mouseButton
        entry.mouseButton =
            if (callback == null) {
                GLFW.glfwSetMouseButtonCallback(window, null)
                null
            } else {
                GLFWMouseButtonCallback
                    .create { callbackWindow, button, action, mods ->
                        callback(callbackWindow, button, action, mods)
                    }.also { GLFW.glfwSetMouseButtonCallback(window, it) }
            }
        previous?.free()
    }

    override fun setScrollCallback(
        window: Long,
        callback: ((Long, Double, Double) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.scroll
        entry.scroll =
            if (callback == null) {
                GLFW.glfwSetScrollCallback(window, null)
                null
            } else {
                GLFWScrollCallback
                    .create { callbackWindow, xOffset, yOffset ->
                        callback(callbackWindow, xOffset, yOffset)
                    }.also { GLFW.glfwSetScrollCallback(window, it) }
            }
        previous?.free()
    }

    override fun setFramebufferSizeCallback(
        window: Long,
        callback: ((Long, Int, Int) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.framebufferSize
        entry.framebufferSize =
            if (callback == null) {
                GLFW.glfwSetFramebufferSizeCallback(window, null)
                null
            } else {
                GLFWFramebufferSizeCallback
                    .create { callbackWindow, width, height ->
                        callback(callbackWindow, width, height)
                    }.also { GLFW.glfwSetFramebufferSizeCallback(window, it) }
            }
        previous?.free()
    }

    override fun setWindowContentScaleCallback(
        window: Long,
        callback: ((Long, Float, Float) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.contentScale
        entry.contentScale =
            if (callback == null) {
                GLFW.glfwSetWindowContentScaleCallback(window, null)
                null
            } else {
                GLFWWindowContentScaleCallback
                    .create { callbackWindow, x, y ->
                        callback(callbackWindow, x, y)
                    }.also { GLFW.glfwSetWindowContentScaleCallback(window, it) }
            }
        previous?.free()
    }

    override fun setWindowFocusCallback(
        window: Long,
        callback: ((Long, Boolean) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.focus
        entry.focus =
            if (callback == null) {
                GLFW.glfwSetWindowFocusCallback(window, null)
                null
            } else {
                GLFWWindowFocusCallback
                    .create { callbackWindow, focused ->
                        callback(callbackWindow, focused)
                    }.also { GLFW.glfwSetWindowFocusCallback(window, it) }
            }
        previous?.free()
    }

    override fun setWindowCloseCallback(
        window: Long,
        callback: ((Long) -> Unit)?,
    ) {
        val entry = callbacks.getOrPut(window) { NativeCallbacks() }
        val previous = entry.close
        entry.close =
            if (callback == null) {
                GLFW.glfwSetWindowCloseCallback(window, null)
                null
            } else {
                GLFWWindowCloseCallback
                    .create { callbackWindow ->
                        callback(callbackWindow)
                    }.also { GLFW.glfwSetWindowCloseCallback(window, it) }
            }
        previous?.free()
    }

    override fun getKey(
        window: Long,
        key: Int,
    ): Int = GLFW.glfwGetKey(window, key)

    override fun getMouseButton(
        window: Long,
        button: Int,
    ): Int = GLFW.glfwGetMouseButton(window, button)

    override fun cursorPosition(window: Long): CursorSample {
        val stack = MemoryStack.stackPush()
        try {
            val x = stack.mallocDouble(1)
            val y = stack.mallocDouble(1)
            GLFW.glfwGetCursorPos(window, x, y)
            return CursorSample(x[0], y[0])
        } finally {
            stack.pop()
        }
    }
}

/** Strong references to the native callback objects registered for one window. */
private class NativeCallbacks {
    var key: GLFWKeyCallback? = null
    var char: GLFWCharCallback? = null
    var cursorPos: GLFWCursorPosCallback? = null
    var cursorEnter: GLFWCursorEnterCallback? = null
    var mouseButton: GLFWMouseButtonCallback? = null
    var scroll: GLFWScrollCallback? = null
    var framebufferSize: GLFWFramebufferSizeCallback? = null
    var contentScale: GLFWWindowContentScaleCallback? = null
    var focus: GLFWWindowFocusCallback? = null
    var close: GLFWWindowCloseCallback? = null

    fun freeAll() {
        key?.free()
        char?.free()
        cursorPos?.free()
        cursorEnter?.free()
        mouseButton?.free()
        scroll?.free()
        framebufferSize?.free()
        contentScale?.free()
        focus?.free()
        close?.free()
    }
}
