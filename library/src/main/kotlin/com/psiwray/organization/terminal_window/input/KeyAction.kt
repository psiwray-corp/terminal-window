package com.psiwray.organization.terminal_window.input

import org.lwjgl.glfw.GLFW

/** The action carried by a keyboard or mouse-button event. @since 2.0.0 */
public enum class KeyAction(
    internal val glfwCode: Int,
) {
    PRESS(GLFW.GLFW_PRESS),
    RELEASE(GLFW.GLFW_RELEASE),
    REPEAT(GLFW.GLFW_REPEAT),
    ;

    internal companion object {
        internal fun fromGlfw(code: Int): KeyAction? = entries.firstOrNull { it.glfwCode == code }
    }
}
