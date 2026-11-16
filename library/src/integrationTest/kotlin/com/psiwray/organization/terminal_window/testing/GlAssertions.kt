package com.psiwray.organization.terminal_window.testing

import org.lwjgl.opengl.GL11
import kotlin.test.assertEquals

/** Pixel and GL-error assertions shared by the integration suite. */
internal object GlAssertions {
    /** Asserts one top-left-origin ARGB pixel. */
    fun assertPixel(
        expected: Int,
        framebuffer: TestFramebuffer,
        x: Int,
        y: Int,
    ) {
        val actual = framebuffer.readPixel(x, y)
        assertEquals(
            expected,
            actual,
            "pixel ($x, $y): expected ${hex(expected)} but was ${hex(actual)}",
        )
    }

    /** Asserts every pixel in a top-left-origin rectangle. */
    fun assertRect(
        expected: Int,
        framebuffer: TestFramebuffer,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        require(width > 0 && height > 0) { "Rectangle dimensions must be positive" }
        require(x >= 0 && y >= 0 && x + width <= framebuffer.width && y + height <= framebuffer.height) {
            "Rectangle ($x, $y, $width, $height) is outside the framebuffer"
        }

        val pixels = framebuffer.readAll()
        for (row in y until y + height) {
            for (column in x until x + width) {
                val actual = pixels[(row * framebuffer.width) + column]
                assertEquals(
                    expected,
                    actual,
                    "pixel ($column, $row): expected ${hex(expected)} but was ${hex(actual)}",
                )
            }
        }
    }

    /** Fails if the GL error queue is not empty. */
    fun assertNoGlError() {
        val error = GL11.glGetError()
        assertEquals(
            GL11.GL_NO_ERROR,
            error,
            "OpenGL error after test: 0x${Integer.toHexString(error)}",
        )
    }

    private fun hex(value: Int): String = "0x${Integer.toHexString(value)}"
}
