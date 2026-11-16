package com.psiwray.organization.terminal_window.input

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModifiersTest {
    @Test
    fun `modifier bits survive mapping and mask unknown bits`() {
        val modifiers =
            Modifiers.fromGlfw(
                GLFW.GLFW_MOD_SHIFT or GLFW.GLFW_MOD_CONTROL or 0x4000,
            )

        assertEquals(GLFW.GLFW_MOD_SHIFT or GLFW.GLFW_MOD_CONTROL, modifiers.bits)
        assertTrue(modifiers.has(Modifiers.SHIFT))
        assertTrue(modifiers.has(Modifiers.CONTROL))
        assertFalse(modifiers.has(Modifiers.ALT))
        assertFalse(Modifiers.NONE.has(Modifiers.SHIFT))
    }

    @Test
    fun `all public modifier constants use their GLFW bit`() {
        assertEquals(GLFW.GLFW_MOD_SHIFT, Modifiers.SHIFT.bits)
        assertEquals(GLFW.GLFW_MOD_CONTROL, Modifiers.CONTROL.bits)
        assertEquals(GLFW.GLFW_MOD_ALT, Modifiers.ALT.bits)
        assertEquals(GLFW.GLFW_MOD_SUPER, Modifiers.SUPER.bits)
        assertEquals(GLFW.GLFW_MOD_CAPS_LOCK, Modifiers.CAPS_LOCK.bits)
        assertEquals(GLFW.GLFW_MOD_NUM_LOCK, Modifiers.NUM_LOCK.bits)
    }
}
