package com.psiwray.organization.terminal_window.testing

import com.psiwray.organization.terminal_window.ContextInfo
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil

/**
 * One hidden OpenGL 3.3 core context for a JUnit test class.
 *
 * GLFW initialization and window creation are process-global and must happen on
 * the thread that calls [GLFW.glfwInit]. Keep this helper on the JUnit test
 * thread; the suite runs with one fork and one context per class.
 */
internal class GlTestContext private constructor(
    private val window: Long,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        try {
            GL.setCapabilities(null)
            GLFW.glfwDestroyWindow(window)
        } finally {
            try {
                clearErrorCallback()
            } finally {
                GLFW.glfwTerminate()
            }
        }
    }

    internal companion object {
        private const val WIDTH = 64
        private const val HEIGHT = 64
        private const val NO_DISPLAY_MESSAGE = "GL integration tests require a display or Xvfb"

        /**
         * Creates the hidden window and makes its core context current.
         *
         * A missing display is a test-environment failure, not an assertion
         * failure: fail with [NO_DISPLAY_MESSAGE] so CI logs show the fix.
         */
        fun create(): GlTestContext {
            GLFWErrorCallback.createPrint(System.err).set()
            if (!GLFW.glfwInit()) {
                clearErrorCallback()
                throw IllegalStateException(NO_DISPLAY_MESSAGE)
            }

            var window = MemoryUtil.NULL
            try {
                GLFW.glfwDefaultWindowHints()
                GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, ContextInfo.MIN_OPENGL_MAJOR)
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, ContextInfo.MIN_OPENGL_MINOR)
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)

                window =
                    GLFW.glfwCreateWindow(
                        WIDTH,
                        HEIGHT,
                        "terminal-window integration test",
                        MemoryUtil.NULL,
                        MemoryUtil.NULL,
                    )
                if (window == MemoryUtil.NULL) {
                    throw IllegalStateException(
                        "$NO_DISPLAY_MESSAGE: GLFW could not create a hidden window",
                    )
                }

                GLFW.glfwMakeContextCurrent(window)
                GL.createCapabilities()
                println("GL integration test renderer: ${GL11.glGetString(GL11.GL_RENDERER)}")
                return GlTestContext(window)
            } catch (failure: Throwable) {
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwDestroyWindow(window)
                }
                try {
                    clearErrorCallback()
                } finally {
                    GLFW.glfwTerminate()
                }
                throw failure
            }
        }

        private fun clearErrorCallback() {
            val previous = GLFW.glfwSetErrorCallback(null)
            if (previous != null) {
                previous.free()
            }
        }
    }
}
