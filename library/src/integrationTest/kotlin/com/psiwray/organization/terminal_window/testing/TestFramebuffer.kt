package com.psiwray.organization.terminal_window.testing

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

/**
 * An RGBA8 color-only framebuffer for exact pixel assertions.
 *
 * The origin exposed by [readPixel] and [readAll] is top-left. OpenGL's
 * bottom-left origin is flipped here so tests can use ordinary image
 * coordinates. Dithering, blending, and sRGB conversion are disabled.
 */
internal class TestFramebuffer(
    val width: Int,
    val height: Int,
) : AutoCloseable {
    private val framebuffer: Int
    private val texture: Int
    private var closed = false

    init {
        require(width > 0 && height > 0) { "Framebuffer dimensions must be positive" }
        framebuffer = GL30.glGenFramebuffers()
        texture = GL11.glGenTextures()
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                MemoryUtil.NULL,
            )

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                texture,
                0,
            )
            val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
            check(status == GL30.GL_FRAMEBUFFER_COMPLETE) {
                "Framebuffer is incomplete: 0x${Integer.toHexString(status)}"
            }

            GL11.glViewport(0, 0, width, height)
            GL11.glDisable(GL11.GL_DITHER)
            GL11.glDisable(GL11.GL_BLEND)
            GL30.glDisable(GL30.GL_FRAMEBUFFER_SRGB)
        } catch (failure: Throwable) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
            GL11.glDeleteTextures(texture)
            GL30.glDeleteFramebuffers(framebuffer)
            throw failure
        }
    }

    /** Reads one pixel as ARGB, with `(0, 0)` at the top-left. */
    fun readPixel(
        x: Int,
        y: Int,
    ): Int {
        require(x in 0 until width && y in 0 until height) {
            "Pixel ($x, $y) is outside ${width}x$height"
        }
        val buffer = MemoryUtil.memAlloc(Integer.BYTES)
        try {
            GL11.glReadPixels(
                x,
                height - 1 - y,
                1,
                1,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer,
            )
            return argb(buffer, 0)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    /** Reads all pixels as row-major ARGB, with row zero at the top. */
    fun readAll(): IntArray {
        val buffer = MemoryUtil.memAlloc(width * height * Integer.BYTES)
        try {
            GL11.glReadPixels(
                0,
                0,
                width,
                height,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                buffer,
            )
            val pixels = IntArray(width * height)
            for (row in 0 until height) {
                val sourceRow = height - 1 - row
                for (column in 0 until width) {
                    pixels[(row * width) + column] =
                        argb(buffer, ((sourceRow * width) + column) * Integer.BYTES)
                }
            }
            return pixels
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
        GL11.glDeleteTextures(texture)
        GL30.glDeleteFramebuffers(framebuffer)
    }

    private fun argb(
        buffer: ByteBuffer,
        offset: Int,
    ): Int {
        val red = buffer.get(offset).toInt() and 0xFF
        val green = buffer.get(offset + 1).toInt() and 0xFF
        val blue = buffer.get(offset + 2).toInt() and 0xFF
        val alpha = buffer.get(offset + 3).toInt() and 0xFF
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
