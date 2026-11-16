package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.Atlas
import com.psiwray.organization.terminal_window.font.AtlasLayout
import com.psiwray.organization.terminal_window.internal.GlResources
import com.psiwray.organization.terminal_window.internal.GlyphAtlas
import com.psiwray.organization.terminal_window.testing.GlAssertions
import com.psiwray.organization.terminal_window.testing.GlTestContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class GlyphAtlasTest {
    private lateinit var context: GlTestContext

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

    @AfterEach
    fun noErrors() {
        GlAssertions.assertNoGlError()
    }

    @Test
    @Timeout(30)
    fun `uploads R8 bytes with nearest and clamped parameters`() {
        val atlas = sampleAtlas()
        val glyphAtlas = GlyphAtlas(atlas)
        try {
            assertEquals(10, glyphAtlas.width)
            assertEquals(3, glyphAtlas.height)
            assertEquals(4, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT))

            glyphAtlas.bind()
            assertEquals(glyphAtlas.textureId, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D))
            assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE))
            assertEquals(
                10,
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
            )
            assertEquals(
                3,
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT),
            )
            assertEquals(
                GL30.GL_R8,
                GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT),
            )
            assertEquals(
                GL11.GL_NEAREST,
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER),
            )
            assertEquals(
                GL11.GL_NEAREST,
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER),
            )
            assertEquals(
                GL12.GL_CLAMP_TO_EDGE,
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S),
            )
            assertEquals(
                GL12.GL_CLAMP_TO_EDGE,
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T),
            )

            val readback = ByteBuffer.allocateDirect(atlas.pixels.size).order(ByteOrder.nativeOrder())
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
            try {
                GL11.glGetTexImage(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RED,
                    GL11.GL_UNSIGNED_BYTE,
                    readback,
                )
            } finally {
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4)
            }
            val actual = ByteArray(atlas.pixels.size)
            readback.rewind()
            readback.get(actual)
            assertContentEquals(atlas.pixels, actual)
        } finally {
            glyphAtlas.close()
        }
    }

    @Test
    @Timeout(30)
    fun `resource owner creates and deletes the VAO VBO and texture`() {
        val resources = GlResources(sampleAtlas())
        val vao = resources.vao
        val vbo = resources.vbo
        val texture = resources.glyphAtlas.textureId
        try {
            assertTrue(vao != 0)
            assertTrue(vbo != 0)
            assertTrue(texture != 0)
            assertTrue(GL30.glIsVertexArray(vao))
            assertTrue(GL15.glIsBuffer(vbo))
        } finally {
            resources.close()
        }
        resources.close()
        assertFalse(GL30.glIsVertexArray(vao))
        assertFalse(GL15.glIsBuffer(vbo))
        assertEquals(0, resources.glyphAtlas.textureId)
    }

    @Test
    @Timeout(30)
    fun `close is idempotent and prevents later binding`() {
        val glyphAtlas = GlyphAtlas(sampleAtlas())
        glyphAtlas.close()
        glyphAtlas.close()
        assertEquals(0, glyphAtlas.textureId)
        assertFailsWith<IllegalStateException> { glyphAtlas.bind() }
    }

    private fun sampleAtlas(): Atlas {
        val layout =
            AtlasLayout(
                columns = 1,
                rows = 1,
                pixelWidth = 10,
                pixelHeight = 3,
                slots = mapOf(0x41 to 0),
            )
        val pixels = ByteArray(10 * 3) { index -> ((index * 17) and 0xFF).toByte() }
        return Atlas(layout, pixels)
    }
}
