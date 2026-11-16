package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.GlfwBackend
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.atomic.AtomicLong

/**
 * Installs and removes the GLFW callbacks for one window.
 *
 * Callbacks only copy primitives into [WindowHandle]'s queue and snapshot
 * state. They never call user code, GL, or window-state functions. The window
 * user pointer carries a numeric registration id so a late native callback
 * cannot be attributed to a different window.
 */
internal class GlfwCallbacks(
    private val handle: WindowHandle,
    private val backend: GlfwBackend,
) {
    private val id: Long = NEXT_ID.getAndIncrement()

    internal fun install() {
        val window = handle.rawHandle
        check(window != MemoryUtil.NULL) { "cannot install callbacks on a null window" }
        try {
            backend.setWindowUserPointer(window, id)
            backend.setKeyCallback(window) { callbackWindow, key, scancode, action, mods ->
                if (isCurrent(callbackWindow)) handle.onKey(key, scancode, action, mods)
            }
            backend.setCharCallback(window) { callbackWindow, codePoint ->
                if (isCurrent(callbackWindow)) handle.onChar(codePoint)
            }
            backend.setCursorPosCallback(window) { callbackWindow, x, y ->
                if (isCurrent(callbackWindow)) handle.onCursorPos(x, y)
            }
            backend.setCursorEnterCallback(window) { callbackWindow, entered ->
                if (isCurrent(callbackWindow)) handle.onCursorEnter(entered)
            }
            backend.setMouseButtonCallback(window) { callbackWindow, button, action, mods ->
                if (isCurrent(callbackWindow)) handle.onMouseButton(button, action, mods)
            }
            backend.setScrollCallback(window) { callbackWindow, xOffset, yOffset ->
                if (isCurrent(callbackWindow)) handle.onScroll(xOffset, yOffset)
            }
            backend.setFramebufferSizeCallback(window) { callbackWindow, width, height ->
                if (isCurrent(callbackWindow)) handle.onFramebufferSize(width, height)
            }
            backend.setWindowContentScaleCallback(window) { callbackWindow, x, y ->
                if (isCurrent(callbackWindow)) handle.onContentScale(x, y)
            }
            backend.setWindowFocusCallback(window) { callbackWindow, focused ->
                if (isCurrent(callbackWindow)) handle.onFocus(focused)
            }
            backend.setWindowCloseCallback(window) { callbackWindow ->
                if (isCurrent(callbackWindow)) handle.onCloseRequested()
            }
        } catch (failure: Throwable) {
            try {
                uninstall()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    internal fun uninstall() {
        val window = handle.rawHandle
        if (window == MemoryUtil.NULL) {
            return
        }
        backend.setKeyCallback(window, null)
        backend.setCharCallback(window, null)
        backend.setCursorPosCallback(window, null)
        backend.setCursorEnterCallback(window, null)
        backend.setMouseButtonCallback(window, null)
        backend.setScrollCallback(window, null)
        backend.setFramebufferSizeCallback(window, null)
        backend.setWindowContentScaleCallback(window, null)
        backend.setWindowFocusCallback(window, null)
        backend.setWindowCloseCallback(window, null)
        backend.setWindowUserPointer(window, MemoryUtil.NULL)
    }

    private fun isCurrent(callbackWindow: Long): Boolean = backend.windowUserPointer(callbackWindow) == id

    private companion object {
        private val NEXT_ID: AtomicLong = AtomicLong(1)
    }
}
