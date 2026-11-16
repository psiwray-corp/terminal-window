package com.psiwray.organization.terminal_window.input

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MouseButtonTest {
    @Test
    fun `mouse buttons preserve GLFW codes`() {
        val expected =
            mapOf(
                MouseButton.LEFT to GLFW.GLFW_MOUSE_BUTTON_LEFT,
                MouseButton.RIGHT to GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                MouseButton.MIDDLE to GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                MouseButton.BUTTON_4 to GLFW.GLFW_MOUSE_BUTTON_4,
                MouseButton.BUTTON_5 to GLFW.GLFW_MOUSE_BUTTON_5,
                MouseButton.BUTTON_6 to GLFW.GLFW_MOUSE_BUTTON_6,
                MouseButton.BUTTON_7 to GLFW.GLFW_MOUSE_BUTTON_7,
                MouseButton.BUTTON_8 to GLFW.GLFW_MOUSE_BUTTON_8,
            )

        for ((button, code) in expected) {
            assertEquals(button, MouseButton.fromGlfw(code))
        }
        assertEquals(MouseButton.entries.size, expected.size)
        assertNull(MouseButton.fromGlfw(Int.MIN_VALUE))
    }
}
