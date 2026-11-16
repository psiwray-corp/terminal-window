package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.GlException
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

/**
 * One linked GLSL 330 program with cached uniform locations.
 *
 * Compilation and link logs are captured even on success. Failures throw
 * [GlException] containing the driver log and the source name. Calls are made
 * on the context-owning thread; this class does not transfer or query context
 * state.
 *
 * @property handle the GL program name; zero after [close].
 * @property uniforms cached uniform locations.
 * @property vertexLog the vertex shader's compile log.
 * @property fragmentLog the fragment shader's compile log.
 * @property linkLog the program link log.
 */
internal class ShaderProgram private constructor(
    handle: Int,
    internal val uniforms: Uniforms,
    internal val vertexLog: String,
    internal val fragmentLog: String,
    internal val linkLog: String,
) : AutoCloseable {
    internal var handle: Int = handle
        private set

    /** Makes this program current. */
    internal fun use() {
        check(handle != 0) { "ShaderProgram is closed" }
        GL20.glUseProgram(handle)
    }

    /** Deletes the program once; subsequent calls are no-ops. */
    override fun close() {
        val current = handle
        if (current != 0) {
            handle = 0
            GL20.glDeleteProgram(current)
        }
    }

    /**
     * Cached uniform locations for [handle].
     *
     * Locations are queried once after linking. A missing uniform is a
     * creation failure, not a later draw-time surprise.
     */
    internal class Uniforms internal constructor(
        /** `uViewportPx` location. */
        internal val uViewportPx: Int,
        /** `uCellPx` location. */
        internal val uCellPx: Int,
        /** `uAtlasCellPx` location. */
        internal val uAtlasCellPx: Int,
        /** `uAtlasGrid` location. */
        internal val uAtlasGrid: Int,
        /** `uAtlasTexels` location. */
        internal val uAtlasTexels: Int,
        /** `uAtlas` sampler location. */
        internal val uAtlas: Int,
    )

    internal companion object {
        private const val VERTEX_RESOURCE =
            "/com/psiwray/organization/terminal_window/shaders/terminal.vert"
        private const val FRAGMENT_RESOURCE =
            "/com/psiwray/organization/terminal_window/shaders/terminal.frag"

        private const val VIEWPORT_UNIFORM = "uViewportPx"
        private const val CELL_UNIFORM = "uCellPx"
        private const val ATLAS_CELL_UNIFORM = "uAtlasCellPx"
        private const val ATLAS_GRID_UNIFORM = "uAtlasGrid"
        private const val ATLAS_TEXELS_UNIFORM = "uAtlasTexels"
        private const val ATLAS_UNIFORM = "uAtlas"

        /** Loads the packaged terminal vertex and fragment shaders. */
        internal fun load(): ShaderProgram = load(VERTEX_RESOURCE, FRAGMENT_RESOURCE)

        /**
         * Loads UTF-8 shader sources from [vertexPath] and [fragmentPath].
         *
         * @throws GlException if a resource is missing.
         */
        internal fun load(
            vertexPath: String,
            fragmentPath: String,
        ): ShaderProgram =
            compile(
                vertexSource = readUtf8(vertexPath),
                fragmentSource = readUtf8(fragmentPath),
                vertexName = vertexPath,
                fragmentName = fragmentPath,
            )

        /**
         * Compiles and links the supplied sources.
         *
         * @throws GlException with the driver log and the offending source name
         *   when compilation, linking, or uniform lookup fails.
         */
        internal fun compile(
            vertexSource: String,
            fragmentSource: String,
            vertexName: String,
            fragmentName: String,
        ): ShaderProgram {
            var vertex = 0
            var fragment = 0
            var program = 0
            try {
                val compiledVertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource, vertexName)
                vertex = compiledVertex.handle
                val compiledFragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource, fragmentName)
                fragment = compiledFragment.handle

                program = GL20.glCreateProgram()
                if (program == 0) {
                    throw GlException("glCreateProgram failed for $vertexName + $fragmentName")
                }
                GL20.glAttachShader(program, vertex)
                GL20.glAttachShader(program, fragment)
                GL20.glLinkProgram(program)
                val linkLog = GL20.glGetProgramInfoLog(program)
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
                    throw GlException(
                        "Shader link failed for $vertexName + $fragmentName:\n" +
                            linkLog.ifBlank { "<no driver log>" },
                    )
                }

                val uniforms =
                    Uniforms(
                        uViewportPx = uniform(program, VIEWPORT_UNIFORM),
                        uCellPx = uniform(program, CELL_UNIFORM),
                        uAtlasCellPx = uniform(program, ATLAS_CELL_UNIFORM),
                        uAtlasGrid = uniform(program, ATLAS_GRID_UNIFORM),
                        uAtlasTexels = uniform(program, ATLAS_TEXELS_UNIFORM),
                        uAtlas = uniform(program, ATLAS_UNIFORM),
                    )
                GL20.glUseProgram(program)
                try {
                    GL20.glUniform1i(uniforms.uAtlas, 0)
                } finally {
                    GL20.glUseProgram(0)
                }
                checkNoGlError("ShaderProgram setup for $vertexName + $fragmentName")

                return ShaderProgram(
                    handle = program,
                    uniforms = uniforms,
                    vertexLog = compiledVertex.log,
                    fragmentLog = compiledFragment.log,
                    linkLog = linkLog,
                )
            } catch (failure: Throwable) {
                if (program != 0) {
                    GL20.glDeleteProgram(program)
                    program = 0
                }
                throw failure
            } finally {
                if (program != 0) {
                    GL20.glDetachShader(program, vertex)
                    GL20.glDetachShader(program, fragment)
                }
                if (vertex != 0) {
                    GL20.glDeleteShader(vertex)
                }
                if (fragment != 0) {
                    GL20.glDeleteShader(fragment)
                }
            }
        }

        private fun compileShader(
            type: Int,
            source: String,
            name: String,
        ): CompiledShader {
            val shader = GL20.glCreateShader(type)
            if (shader == 0) {
                throw GlException("glCreateShader failed for $name")
            }
            GL20.glShaderSource(shader, source)
            GL20.glCompileShader(shader)
            val log = GL20.glGetShaderInfoLog(shader)
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
                GL20.glDeleteShader(shader)
                throw GlException(
                    "Shader compilation failed for $name:\n" +
                        log.ifBlank { "<no driver log>" } +
                        "\n--- source ---\n" +
                        source,
                )
            }
            return CompiledShader(shader, log)
        }

        private fun uniform(
            program: Int,
            name: String,
        ): Int {
            val location = GL20.glGetUniformLocation(program, name)
            if (location < 0) {
                throw GlException("Uniform $name is missing after linking")
            }
            return location
        }

        private fun readUtf8(path: String): String {
            val stream =
                ShaderProgram::class.java.getResourceAsStream(path)
                    ?: throw GlException("Shader resource not found on classpath: $path")
            return stream.use { it.readBytes().toString(Charsets.UTF_8) }
        }

        private fun checkNoGlError(context: String) {
            val error = GL11.glGetError()
            if (error != GL11.GL_NO_ERROR) {
                throw GlException("$context failed with OpenGL error 0x${Integer.toHexString(error)}")
            }
        }

        private data class CompiledShader(
            val handle: Int,
            val log: String,
        )
    }
}
