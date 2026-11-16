package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.GlException
import com.psiwray.organization.terminal_window.font.Atlas
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30

/**
 * Owns the glyph texture, vertex array, and instance buffer for one renderer.
 *
 * Creation is one-shot and requires a current GL context. The VBO is empty;
 * the renderer supplies its storage and uploads instance data. [close] deletes
 * all three objects and is idempotent. No GL call is made after close.
 *
 * @property glyphAtlas the uploaded R8 glyph texture.
 * @property vao the configured instance VAO.
 * @property vbo the instance buffer name.
 */
internal class GlResources(
    atlas: Atlas,
) : AutoCloseable {
    /** Uploaded atlas texture and its grid metadata. */
    internal val glyphAtlas: GlyphAtlas

    /** Configured vertex array name. */
    internal val vao: Int

    /** Instance buffer name. */
    internal val vbo: Int

    private var closed: Boolean = false

    init {
        val createdVao = GL30.glGenVertexArrays()
        val createdVbo = GL15.glGenBuffers()
        if (createdVao == 0 || createdVbo == 0) {
            if (createdVao != 0) {
                GL30.glDeleteVertexArrays(createdVao)
            }
            if (createdVbo != 0) {
                GL15.glDeleteBuffers(createdVbo)
            }
            throw GlException("Failed to allocate VAO/VBO for the renderer")
        }

        val createdAtlas =
            try {
                GlyphAtlas(atlas)
            } catch (failure: Throwable) {
                GL15.glDeleteBuffers(createdVbo)
                GL30.glDeleteVertexArrays(createdVao)
                throw failure
            }
        try {
            InstanceFormat.configure(createdVao, createdVbo)
        } catch (failure: Throwable) {
            createdAtlas.close()
            GL15.glDeleteBuffers(createdVbo)
            GL30.glDeleteVertexArrays(createdVao)
            throw failure
        }

        vao = createdVao
        vbo = createdVbo
        glyphAtlas = createdAtlas
    }

    /** Deletes the VAO, VBO, and texture once. */
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        GL30.glDeleteVertexArrays(vao)
        GL15.glDeleteBuffers(vbo)
        glyphAtlas.close()
    }
}
