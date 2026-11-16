package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.TerminalMatrix
import com.psiwray.organization.terminal_window.font.Atlas
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL31
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packs one matrix into 16-byte instances and renders it with one instanced
 * triangle-strip draw.
 *
 * The staging buffer and GL objects are created once. [upload] only grows the
 * staging buffer when the matrix needs more bytes; [draw] makes no heap
 * allocation. All methods require the owning OpenGL context to be current and
 * must not be called after [close].
 *
 * @param matrix the source cells, confined to the owner thread.
 * @param atlas the baked R8 mask uploaded once at construction.
 * @param framebufferWidth initial framebuffer width in pixels.
 * @param framebufferHeight initial framebuffer height in pixels.
 * @param cellWidthPx initial cell width in framebuffer pixels.
 * @param cellHeightPx initial cell height in framebuffer pixels.
 * @param atlasCellWidth atlas cell width in texels.
 * @param atlasCellHeight atlas cell height in texels.
 * @param swapAction presents the frame on the owner thread.
 */
internal class Renderer(
    private val matrix: TerminalMatrix,
    atlas: Atlas,
    private var framebufferWidth: Int,
    private var framebufferHeight: Int,
    private var cellWidthPx: Int,
    private var cellHeightPx: Int,
    private val atlasCellWidth: Int,
    private val atlasCellHeight: Int,
    private val swapAction: () -> Unit,
) : FrameSink,
    AutoCloseable {
    private val resources: GlResources
    private val program: ShaderProgram
    private var staging: ByteBuffer
    private var closed: Boolean = false

    init {
        require(framebufferWidth >= 0) { "framebufferWidth must be non-negative: $framebufferWidth" }
        require(framebufferHeight >= 0) { "framebufferHeight must be non-negative: $framebufferHeight" }
        require(cellWidthPx >= 1) { "cellWidthPx must be positive: $cellWidthPx" }
        require(cellHeightPx >= 1) { "cellHeightPx must be positive: $cellHeightPx" }
        require(atlasCellWidth >= 1) { "atlasCellWidth must be positive: $atlasCellWidth" }
        require(atlasCellHeight >= 1) { "atlasCellHeight must be positive: $atlasCellHeight" }

        val createdResources = GlResources(atlas)
        try {
            program = ShaderProgram.load()
        } catch (failure: Throwable) {
            createdResources.close()
            throw failure
        }
        resources = createdResources
        staging = allocateStaging(stagingBytes(matrix.columns * matrix.rows))
    }

    /**
     * Applies a framebuffer/cell-size change and reallocates the staging buffer
     * to exactly the current matrix size.
     *
     * The next [upload] replaces the VBO storage with the same exact byte count.
     * This method requires a current context and does not upload or draw.
     */
    internal fun resize(
        framebufferWidth: Int,
        framebufferHeight: Int,
        cellWidthPx: Int,
        cellHeightPx: Int,
    ) {
        check(!closed) { "Renderer is closed" }
        require(framebufferWidth >= 0) { "framebufferWidth must be non-negative: $framebufferWidth" }
        require(framebufferHeight >= 0) { "framebufferHeight must be non-negative: $framebufferHeight" }
        require(cellWidthPx >= 1) { "cellWidthPx must be positive: $cellWidthPx" }
        require(cellHeightPx >= 1) { "cellHeightPx must be positive: $cellHeightPx" }

        this.framebufferWidth = framebufferWidth
        this.framebufferHeight = framebufferHeight
        this.cellWidthPx = cellWidthPx
        this.cellHeightPx = cellHeightPx
        staging = allocateStaging(stagingBytes(matrix.columns * matrix.rows))
        GL11.glViewport(0, 0, framebufferWidth, framebufferHeight)
    }

    /** Packs the whole matrix and uploads it in one `glBufferData` call. */
    override fun upload() {
        check(!closed) { "Renderer is closed" }
        val columns = matrix.columns
        val rows = matrix.rows
        val cellCount = columns * rows
        val bytes = stagingBytes(cellCount)
        if (staging.capacity() < bytes) {
            staging = allocateStaging(bytes)
        }

        val slots = resources.glyphAtlas.layout.slots
        var row = 0
        var cell = 0
        while (row < rows) {
            var column = 0
            while (column < columns) {
                val offset = cell * InstanceFormat.STRIDE_BYTES
                val codePoint = matrix.codePointAt(column, row)
                val slot = slots[codePoint] ?: InstanceFormat.NO_GLYPH

                staging.putShort(offset + InstanceFormat.CELL_OFFSET, column.toShort())
                staging.putShort(offset + InstanceFormat.CELL_OFFSET + 2, row.toShort())
                staging.putShort(offset + InstanceFormat.GLYPH_OFFSET, slot.toShort())
                staging.putShort(offset + InstanceFormat.RESERVED_OFFSET, 0)
                putColor(offset + InstanceFormat.FOREGROUND_OFFSET, matrix.foregroundAt(column, row))
                putColor(offset + InstanceFormat.BACKGROUND_OFFSET, matrix.backgroundAt(column, row))

                column++
                cell++
            }
            row++
        }

        staging.position(0)
        staging.limit(bytes)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, resources.vbo)
        try {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, staging, GL15.GL_STREAM_DRAW)
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
        }
    }

    /** Clears to the matrix background and draws every cell in one call. */
    override fun draw() {
        check(!closed) { "Renderer is closed" }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return
        }

        val background = matrix.defaultBackground
        GL11.glViewport(0, 0, framebufferWidth, framebufferHeight)
        GL11.glClearColor(
            ((background ushr 16) and 0xFF) / 255.0f,
            ((background ushr 8) and 0xFF) / 255.0f,
            (background and 0xFF) / 255.0f,
            ((background ushr 24) and 0xFF) / 255.0f,
        )
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

        program.use()
        try {
            GL20.glUniform2f(program.uniforms.uViewportPx, framebufferWidth.toFloat(), framebufferHeight.toFloat())
            GL20.glUniform2f(program.uniforms.uCellPx, cellWidthPx.toFloat(), cellHeightPx.toFloat())
            GL20.glUniform2f(
                program.uniforms.uAtlasCellPx,
                atlasCellWidth.toFloat(),
                atlasCellHeight.toFloat(),
            )
            GL20.glUniform2f(
                program.uniforms.uAtlasGrid,
                resources.glyphAtlas.layout.columns
                    .toFloat(),
                resources.glyphAtlas.layout.rows
                    .toFloat(),
            )
            GL20.glUniform2f(
                program.uniforms.uAtlasTexels,
                resources.glyphAtlas.width.toFloat(),
                resources.glyphAtlas.height.toFloat(),
            )

            resources.glyphAtlas.bind()
            GL30.glBindVertexArray(resources.vao)
            try {
                GL31.glDrawArraysInstanced(
                    GL11.GL_TRIANGLE_STRIP,
                    0,
                    4,
                    matrix.columns * matrix.rows,
                )
            } finally {
                GL30.glBindVertexArray(0)
            }
        } finally {
            GL20.glUseProgram(0)
        }
    }

    /** Presents the rendered frame through the owner-thread swap action. */
    override fun swap() {
        check(!closed) { "Renderer is closed" }
        swapAction()
    }

    /** Deletes the program and GL resources once. */
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        program.close()
        resources.close()
    }

    private fun putColor(
        offset: Int,
        argb: Int,
    ) {
        staging.put(offset, ((argb ushr 16) and 0xFF).toByte())
        staging.put(offset + 1, ((argb ushr 8) and 0xFF).toByte())
        staging.put(offset + 2, (argb and 0xFF).toByte())
        staging.put(offset + 3, ((argb ushr 24) and 0xFF).toByte())
    }

    private fun allocateStaging(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())

    private fun stagingBytes(cellCount: Int): Int {
        val bytes = cellCount.toLong() * InstanceFormat.STRIDE_BYTES
        require(bytes <= Int.MAX_VALUE.toLong()) {
            "matrix needs $bytes instance bytes, exceeding Int capacity"
        }
        return bytes.toInt()
    }
}
