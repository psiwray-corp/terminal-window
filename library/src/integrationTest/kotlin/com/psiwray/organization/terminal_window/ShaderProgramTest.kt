package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.internal.InstanceFormat
import com.psiwray.organization.terminal_window.internal.ShaderProgram
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
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33
import org.lwjgl.system.MemoryStack
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("gl")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ShaderProgramTest {
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
    fun `program links with the four expected attributes`() {
        val program = ShaderProgram.load()
        try {
            val attributes = activeAttributes(program.handle)
            assertEquals(setOf("iCell", "iGlyph", "iFg", "iBg"), attributes.keys)

            assertAttribute(program.handle, attributes, "iCell", GL30.GL_UNSIGNED_INT_VEC2, 1, 0)
            assertAttribute(program.handle, attributes, "iGlyph", GL11.GL_UNSIGNED_INT, 1, 1)
            assertAttribute(program.handle, attributes, "iFg", GL20.GL_FLOAT_VEC4, 1, 2)
            assertAttribute(program.handle, attributes, "iBg", GL20.GL_FLOAT_VEC4, 1, 3)

            assertEquals(0, GL20.glGetAttribLocation(program.handle, InstanceFormat.CELL_ATTRIBUTE))
            assertEquals(1, GL20.glGetAttribLocation(program.handle, InstanceFormat.GLYPH_ATTRIBUTE))
            assertEquals(
                2,
                GL20.glGetAttribLocation(program.handle, InstanceFormat.FOREGROUND_ATTRIBUTE),
            )
            assertEquals(
                3,
                GL20.glGetAttribLocation(program.handle, InstanceFormat.BACKGROUND_ATTRIBUTE),
            )
        } finally {
            program.close()
        }
    }

    @Test
    @Timeout(30)
    fun `program caches every uniform location`() {
        val program = ShaderProgram.load()
        try {
            val uniforms = program.uniforms
            assertUniform(program.handle, "uViewportPx", uniforms.uViewportPx)
            assertUniform(program.handle, "uCellPx", uniforms.uCellPx)
            assertUniform(program.handle, "uAtlasCellPx", uniforms.uAtlasCellPx)
            assertUniform(program.handle, "uAtlasGrid", uniforms.uAtlasGrid)
            assertUniform(program.handle, "uAtlasTexels", uniforms.uAtlasTexels)
            assertUniform(program.handle, "uAtlas", uniforms.uAtlas)
        } finally {
            program.close()
        }
    }

    @Test
    @Timeout(30)
    fun `instance format configures four vertex attributes`() {
        val vao = GL30.glGenVertexArrays()
        val vbo = GL15.glGenBuffers()
        try {
            InstanceFormat.configure(vao, vbo)
            assertFalse(GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
            assertFalse(GL11.glIsEnabled(GL11.GL_BLEND))
            assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE))
            assertFalse(GL11.glIsEnabled(GL11.GL_DITHER))
            GL30.glBindVertexArray(vao)
            try {
                assertVertexAttribute(0, 2, GL11.GL_UNSIGNED_SHORT, false)
                assertVertexAttribute(1, 1, GL11.GL_UNSIGNED_SHORT, false)
                assertVertexAttribute(2, 4, GL11.GL_UNSIGNED_BYTE, true)
                assertVertexAttribute(3, 4, GL11.GL_UNSIGNED_BYTE, true)
            } finally {
                GL30.glBindVertexArray(0)
            }
        } finally {
            GL15.glDeleteBuffers(vbo)
            GL30.glDeleteVertexArrays(vao)
        }
    }

    @Test
    @Timeout(30)
    fun `packaged shader sources declare GLSL 330 core`() {
        val vertex = resourceText("/com/psiwray/organization/terminal_window/shaders/terminal.vert")
        val fragment = resourceText("/com/psiwray/organization/terminal_window/shaders/terminal.frag")
        assertTrue(vertex.contains("#version 330 core"), vertex)
        assertTrue(fragment.contains("#version 330 core"), fragment)
    }

    @Test
    @Timeout(30)
    fun `broken shader includes the driver log and source name`() {
        val fragment =
            """
            #version 330 core
            out vec4 oColor;
            void main() {
                oColor = vec4(1.0);
            }
            """.trimIndent()
        val failure =
            assertFailsWith<GlException> {
                ShaderProgram.compile(
                    vertexSource =
                        """
                        #version 330 core
                        void main() {
                            gl_Position = ;
                        }
                        """.trimIndent(),
                    fragmentSource = fragment,
                    vertexName = "broken.vert",
                    fragmentName = "terminal.frag",
                )
            }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("broken.vert"), message)
        assertTrue(message.contains("Shader compilation failed"), message)
        assertTrue(message.contains("error", ignoreCase = true), message)
    }

    @Test
    @Timeout(30)
    fun `missing shader resource names the path`() {
        val failure =
            assertFailsWith<GlException> {
                ShaderProgram.load(
                    "/com/psiwray/organization/terminal_window/shaders/missing.vert",
                    "/com/psiwray/organization/terminal_window/shaders/terminal.frag",
                )
            }
        assertTrue(failure.message.orEmpty().contains("missing.vert"), failure.message.orEmpty())
    }

    @Test
    @Timeout(30)
    fun `closing a program twice is safe`() {
        val program = ShaderProgram.load()
        program.close()
        program.close()
        assertFailsWith<IllegalStateException> { program.use() }
    }

    private fun activeAttributes(program: Int): Map<String, Attribute> {
        val count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_ATTRIBUTES)
        val result = LinkedHashMap<String, Attribute>(count)
        MemoryStack.stackPush().use { stack ->
            val size = stack.mallocInt(1)
            val type = stack.mallocInt(1)
            var index = 0
            while (index < count) {
                val name = GL20.glGetActiveAttrib(program, index, MAX_ATTRIBUTE_NAME, size, type)
                if (GL20.glGetAttribLocation(program, name) >= 0) {
                    result[name] = Attribute(size.get(0), type.get(0))
                }
                index++
            }
        }
        return result
    }

    private fun assertAttribute(
        program: Int,
        attributes: Map<String, Attribute>,
        name: String,
        type: Int,
        size: Int,
        location: Int,
    ) {
        val attribute = attributes.getValue(name)
        assertEquals(type, attribute.type, "$name type")
        assertEquals(size, attribute.size, "$name size")
        assertEquals(location, GL20.glGetAttribLocation(program, name), "$name location")
    }

    private fun assertUniform(
        program: Int,
        name: String,
        cached: Int,
    ) {
        assertTrue(cached >= 0, "$name must be present")
        assertEquals(cached, GL20.glGetUniformLocation(program, name), "$name location")
    }

    private fun assertVertexAttribute(
        location: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
    ) {
        assertEquals(1, GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED))
        assertEquals(size, GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE))
        assertEquals(type, GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE))
        assertEquals(
            if (normalized) 1 else 0,
            GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED),
        )
        assertEquals(InstanceFormat.STRIDE_BYTES, GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE))
        assertEquals(1, GL20.glGetVertexAttribi(location, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR))
    }

    private fun resourceText(path: String): String {
        val stream = ShaderProgramTest::class.java.getResourceAsStream(path)
        check(stream != null) { "missing test resource $path" }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private data class Attribute(
        val size: Int,
        val type: Int,
    )

    private companion object {
        private const val MAX_ATTRIBUTE_NAME: Int = 64
    }
}
