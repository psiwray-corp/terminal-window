package com.psiwray.organization.terminal_window.testing

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.lwjgl.opengl.GL11
import kotlin.test.assertEquals

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class HeadlessGlSmokeTest {
    private lateinit var context: GlTestContext
    private lateinit var framebuffer: TestFramebuffer

    @BeforeAll
    fun createContext() {
        context = GlTestContext.create()
    }

    @AfterAll
    fun destroyContext() {
        if (::context.isInitialized) {
            context.close()
        }
    }

    @BeforeEach
    fun createFramebuffer() {
        framebuffer = TestFramebuffer(64, 64)
    }

    @AfterEach
    fun destroyFramebuffer() {
        if (::framebuffer.isInitialized) {
            try {
                framebuffer.close()
            } finally {
                GlAssertions.assertNoGlError()
            }
        }
    }

    @Test
    @Timeout(20)
    fun `clear color round-trips exact bytes`() {
        val expected = 0xFF102030.toInt()
        GL11.glClearColor(0x10 / 255.0f, 0x20 / 255.0f, 0x30 / 255.0f, 1.0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

        GlAssertions.assertRect(expected, framebuffer, 0, 0, framebuffer.width, framebuffer.height)
        GlAssertions.assertPixel(expected, framebuffer, 0, 0)
        GlAssertions.assertPixel(expected, framebuffer, framebuffer.width - 1, framebuffer.height - 1)
    }

    @Test
    @Timeout(20)
    fun `readback uses top-left origin`() {
        val blue = 0xFF0000FF.toInt()
        val green = 0xFF00FF00.toInt()
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        try {
            GL11.glScissor(0, 0, framebuffer.width, framebuffer.height / 2)
            GL11.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

            GL11.glScissor(0, framebuffer.height / 2, framebuffer.width, framebuffer.height / 2)
            GL11.glClearColor(0.0f, 0.0f, 1.0f, 1.0f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }

        GlAssertions.assertPixel(blue, framebuffer, 0, 0)
        GlAssertions.assertPixel(green, framebuffer, 0, framebuffer.height - 1)
        val pixels = framebuffer.readAll()
        assertEquals(blue, pixels[0])
        assertEquals(green, pixels[(framebuffer.height - 1) * framebuffer.width])
    }
}
