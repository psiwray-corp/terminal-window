package com.psiwray.organization.terminal_window.input

import com.psiwray.organization.terminal_window.internal.CursorSample
import com.psiwray.organization.terminal_window.internal.sampleDeviceState
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceStateTest {
    @Test
    fun `snapshot marks pressed keys and buttons from backend state`() {
        val state =
            sampleDeviceState(
                keyState = { code -> if (code == Key.A.glfwCode) GLFW.GLFW_PRESS else GLFW.GLFW_RELEASE },
                mouseButtonState = { code ->
                    if (code == MouseButton.LEFT.glfwCode) GLFW.GLFW_PRESS else GLFW.GLFW_RELEASE
                },
                cursor = CursorSample(17.0, 33.0),
                cellWidth = 8,
                cellHeight = 16,
                scale = 2,
            )

        assertTrue(state.isKeyPressed(Key.A))
        assertFalse(state.isKeyPressed(Key.B))
        assertTrue(state.isMouseButtonPressed(MouseButton.LEFT))
        assertFalse(state.isMouseButtonPressed(MouseButton.RIGHT))
        assertEquals(1, state.cursor.column)
        assertEquals(1, state.cursor.row)
    }

    @Test
    fun `released state between polls is reported released`() {
        var pressed = true
        val state =
            sampleDeviceState(
                keyState = { if (pressed) GLFW.GLFW_PRESS else GLFW.GLFW_RELEASE },
                mouseButtonState = { GLFW.GLFW_RELEASE },
                cursor = CursorSample(0.0, 0.0),
                cellWidth = 8,
                cellHeight = 16,
                scale = 1,
            )
        assertTrue(state.isKeyPressed(Key.A))

        pressed = false
        val released =
            sampleDeviceState(
                keyState = { GLFW.GLFW_RELEASE },
                mouseButtonState = { GLFW.GLFW_RELEASE },
                cursor = CursorSample(0.0, 0.0),
                cellWidth = 8,
                cellHeight = 16,
                scale = 1,
            )
        assertFalse(released.isKeyPressed(Key.A))
    }

    @Test
    fun `negative screen coordinates produce negative cell coordinates`() {
        val state =
            sampleDeviceState(
                keyState = { GLFW.GLFW_RELEASE },
                mouseButtonState = { GLFW.GLFW_RELEASE },
                cursor = CursorSample(-1.0, -0.5),
                cellWidth = 8,
                cellHeight = 16,
                scale = 1,
            )

        assertEquals(-1, state.cursor.column)
        assertEquals(-1, state.cursor.row)
        assertEquals(-1.0, state.cursor.screenX)
        assertEquals(-0.5, state.cursor.screenY)
    }

    @Test
    fun `snapshot does not retain mutable backend values`() {
        var x = 1.0
        val state =
            sampleDeviceState(
                keyState = { GLFW.GLFW_RELEASE },
                mouseButtonState = { GLFW.GLFW_RELEASE },
                cursor = CursorSample(x, 2.0),
                cellWidth = 8,
                cellHeight = 16,
                scale = 1,
            )

        x += 999.0

        assertEquals(1000.0, x)
        assertEquals(1.0, state.cursor.screenX)
    }
}
