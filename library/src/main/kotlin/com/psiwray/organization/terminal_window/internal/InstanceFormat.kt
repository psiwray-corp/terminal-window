package com.psiwray.organization.terminal_window.internal

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33

/**
 * The fixed 16-byte instance layout shared by the CPU packer and the shader.
 *
 * Each cell occupies one instance. Multibyte integer fields are host-endian
 * `u16` values; the color fields are normalized `u8` RGBA values. Attributes
 * 0 and 1 must use [GL30.glVertexAttribIPointer] because GLSL declares them as
 * integer (`uvec2` and `uint`). Attributes 2 and 3 use the float pointer form
 * with normalization enabled.
 */
internal object InstanceFormat {
    /** Bytes per cell instance. */
    internal const val STRIDE_BYTES: Int = 16

    /** `iCell` attribute location. */
    internal const val CELL_LOCATION: Int = 0

    /** `iGlyph` attribute location. */
    internal const val GLYPH_LOCATION: Int = 1

    /** `iFg` attribute location. */
    internal const val FOREGROUND_LOCATION: Int = 2

    /** `iBg` attribute location. */
    internal const val BACKGROUND_LOCATION: Int = 3

    /** Byte offset of the cell column/row pair. */
    internal const val CELL_OFFSET: Int = 0

    /** Byte offset of the glyph slot. */
    internal const val GLYPH_OFFSET: Int = 4

    /** Byte offset of the reserved padding word. */
    internal const val RESERVED_OFFSET: Int = 6

    /** Byte offset of the foreground RGBA bytes. */
    internal const val FOREGROUND_OFFSET: Int = 8

    /** Byte offset of the background RGBA bytes. */
    internal const val BACKGROUND_OFFSET: Int = 12

    /** Sentinel slot meaning "background only"; never a baked glyph slot. */
    internal const val NO_GLYPH: Int = 0xFFFF

    /** Attribute names used by the vertex shader. */
    internal const val CELL_ATTRIBUTE: String = "iCell"

    /** Glyph slot attribute name. */
    internal const val GLYPH_ATTRIBUTE: String = "iGlyph"

    /** Foreground color attribute name. */
    internal const val FOREGROUND_ATTRIBUTE: String = "iFg"

    /** Background color attribute name. */
    internal const val BACKGROUND_ATTRIBUTE: String = "iBg"

    /**
     * Configures [vao] with [vbo] as its instance source.
     *
     * The VBO must already exist; its data contents are uploaded by the
     * renderer. The VAO retains the buffer binding. This function also disables
     * depth testing, blending, face culling, and dithering for the draw path,
     * and leaves the array-buffer and vertex-array bindings at zero.
     *
     * @throws IllegalArgumentException if either object name is zero.
     */
    internal fun configure(
        vao: Int,
        vbo: Int,
    ) {
        require(vao != 0) { "VAO must be a non-zero GL name" }
        require(vbo != 0) { "VBO must be a non-zero GL name" }
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glDisable(GL11.GL_DITHER)
        GL30.glBindVertexArray(vao)
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
            GL30.glVertexAttribIPointer(
                CELL_LOCATION,
                2,
                GL11.GL_UNSIGNED_SHORT,
                STRIDE_BYTES,
                CELL_OFFSET.toLong(),
            )
            GL20.glEnableVertexAttribArray(CELL_LOCATION)
            GL30.glVertexAttribIPointer(
                GLYPH_LOCATION,
                1,
                GL11.GL_UNSIGNED_SHORT,
                STRIDE_BYTES,
                GLYPH_OFFSET.toLong(),
            )
            GL20.glEnableVertexAttribArray(GLYPH_LOCATION)
            GL20.glVertexAttribPointer(
                FOREGROUND_LOCATION,
                4,
                GL11.GL_UNSIGNED_BYTE,
                true,
                STRIDE_BYTES,
                FOREGROUND_OFFSET.toLong(),
            )
            GL20.glEnableVertexAttribArray(FOREGROUND_LOCATION)
            GL20.glVertexAttribPointer(
                BACKGROUND_LOCATION,
                4,
                GL11.GL_UNSIGNED_BYTE,
                true,
                STRIDE_BYTES,
                BACKGROUND_OFFSET.toLong(),
            )
            GL20.glEnableVertexAttribArray(BACKGROUND_LOCATION)
            for (location in CELL_LOCATION..BACKGROUND_LOCATION) {
                GL33.glVertexAttribDivisor(location, 1)
            }
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
            GL30.glBindVertexArray(0)
        }
    }
}
