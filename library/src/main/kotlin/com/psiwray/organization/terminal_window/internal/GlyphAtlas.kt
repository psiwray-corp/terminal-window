package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.GlException
import com.psiwray.organization.terminal_window.font.Atlas
import com.psiwray.organization.terminal_window.font.AtlasLayout
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One immutable glyph-mask texture uploaded from a baked [Atlas].
 *
 * The texture is `GL_R8`, uses nearest filtering and clamped edges, has no
 * mipmaps, and is active on texture unit 0. The pixel bytes are copied into a
 * temporary direct buffer exactly once during construction and then discarded;
 * [bind] performs no allocation.
 *
 * The constructor and [close] require a current GL context. [close] is
 * idempotent and no GL call is made after the texture name becomes zero.
 *
 * @property layout the atlas grid and slot metadata.
 */
internal class GlyphAtlas(
    atlas: Atlas,
) : AutoCloseable {
    /** Grid and slot metadata for the uploaded mask. */
    internal val layout: AtlasLayout = atlas.layout

    /** Texture width in pixels. */
    internal val width: Int = atlas.layout.pixelWidth

    /** Texture height in pixels. */
    internal val height: Int = atlas.layout.pixelHeight

    /** GL texture name; zero after [close]. */
    internal var textureId: Int = 0
        private set

    init {
        require(width > 0) { "Atlas width must be positive: $width" }
        require(height > 0) { "Atlas height must be positive: $height" }
        val expectedBytes = width.toLong() * height.toLong()
        require(atlas.pixels.size.toLong() == expectedBytes) {
            "Atlas pixel count ${atlas.pixels.size} does not match ${width}x$height"
        }

        val created = GL11.glGenTextures()
        if (created == 0) {
            throw GlException("glGenTextures failed for the ${width}x$height glyph atlas")
        }
        textureId = created
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, created)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)

            val pixels =
                ByteBuffer
                    .allocateDirect(atlas.pixels.size)
                    .order(ByteOrder.nativeOrder())
                    .put(atlas.pixels)
            pixels.flip()
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_R8,
                width,
                height,
                0,
                GL11.GL_RED,
                GL11.GL_UNSIGNED_BYTE,
                pixels,
            )
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
            checkNoGlError()
        } catch (failure: Throwable) {
            textureId = 0
            GL11.glDeleteTextures(created)
            throw failure
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
        }
    }

    /**
     * Makes this texture active on unit 0.
     *
     * @throws IllegalStateException after [close].
     */
    internal fun bind() {
        check(textureId != 0) { "GlyphAtlas is closed" }
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
    }

    /** Deletes the texture once; subsequent calls are no-ops. */
    override fun close() {
        val current = textureId
        if (current != 0) {
            textureId = 0
            GL11.glDeleteTextures(current)
        }
    }

    private fun checkNoGlError() {
        val error = GL11.glGetError()
        if (error != GL11.GL_NO_ERROR) {
            throw GlException(
                "GlyphAtlas setup failed with OpenGL error 0x${Integer.toHexString(error)}",
            )
        }
    }
}
