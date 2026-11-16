package com.psiwray.organization.terminal_window.input

import org.lwjgl.glfw.GLFW

/** A mouse button reported by GLFW. @since 2.0.0 */
public enum class MouseButton(
    internal val glfwCode: Int,
) {
    LEFT(GLFW.GLFW_MOUSE_BUTTON_LEFT),
    RIGHT(GLFW.GLFW_MOUSE_BUTTON_RIGHT),
    MIDDLE(GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
    BUTTON_4(GLFW.GLFW_MOUSE_BUTTON_4),
    BUTTON_5(GLFW.GLFW_MOUSE_BUTTON_5),
    BUTTON_6(GLFW.GLFW_MOUSE_BUTTON_6),
    BUTTON_7(GLFW.GLFW_MOUSE_BUTTON_7),
    BUTTON_8(GLFW.GLFW_MOUSE_BUTTON_8),
    ;

    internal companion object {
        private val byGlfwCode: Map<Int, MouseButton> = entries.associateBy { it.glfwCode }

        internal fun fromGlfw(code: Int): MouseButton? = byGlfwCode[code]
    }
}
