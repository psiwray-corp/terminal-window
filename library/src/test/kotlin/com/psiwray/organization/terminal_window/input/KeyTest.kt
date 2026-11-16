package com.psiwray.organization.terminal_window.input

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyTest {
    @Test
    fun `every GLFW key token maps to exactly one Key`() {
        val glfwCodes =
            GLFW::class.java.fields
                .filter { it.name.startsWith("GLFW_KEY_") && it.name != "GLFW_KEY_LAST" }
                .map { it.getInt(null) }
                .toSet()

        val mappedCodes = Key.entries.map { it.glfwCode }.toSet()
        assertEquals(glfwCodes, mappedCodes)
        assertEquals(Key.entries.size, mappedCodes.size)
        assertEquals(Key.entries.size, glfwCodes.size)

        for (code in glfwCodes) {
            val key = Key.fromGlfw(code)
            if (code == GLFW.GLFW_KEY_UNKNOWN) {
                assertEquals(Key.UNKNOWN, key)
            } else {
                assertTrue(key != Key.UNKNOWN, "GLFW code $code did not map to a key")
            }
        }
    }

    @Test
    fun `unknown GLFW codes map to UNKNOWN`() {
        assertEquals(Key.UNKNOWN, Key.fromGlfw(GLFW.GLFW_KEY_UNKNOWN))
        assertEquals(Key.UNKNOWN, Key.fromGlfw(Int.MAX_VALUE))
    }

    @Test
    fun `key actions preserve GLFW values`() {
        assertEquals(KeyAction.PRESS, KeyAction.fromGlfw(GLFW.GLFW_PRESS))
        assertEquals(KeyAction.RELEASE, KeyAction.fromGlfw(GLFW.GLFW_RELEASE))
        assertEquals(KeyAction.REPEAT, KeyAction.fromGlfw(GLFW.GLFW_REPEAT))
        assertEquals(null, KeyAction.fromGlfw(Int.MIN_VALUE))
    }
}
