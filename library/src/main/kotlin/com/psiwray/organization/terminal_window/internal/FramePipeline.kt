package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.RenderStats
import com.psiwray.organization.terminal_window.TerminalMatrix

/**
 * The renderer operations the frame scheduler may call.
 *
 * The sink is intentionally internal: it is a testing seam, not public API.
 * Implementations must not be called outside [FrameScheduler.tick].
 */
internal interface FrameSink {
    /** Uploads the whole current matrix once. */
    fun upload()

    /** Draws one frame from the last uploaded data. */
    fun draw()

    /** Presents the frame. */
    fun swap()
}

/**
 * Owner-thread inputs and controls consumed by [FrameScheduler].
 *
 * [drainEvents] dispatches queued input and applies its matrix mutations.
 * [applyPendingResize] applies a pending framebuffer/content-scale resize and
 * returns true when it changed the matrix or required a renderer reallocation.
 * It must mark the matrix dirty when content changed.
 */
internal interface FrameHost {
    fun drainEvents()

    fun applyPendingResize(): Boolean

    fun consumeRepaintRequest(): Boolean

    /** Restores a one-shot request that was consumed but not rendered. */
    fun restoreRepaintRequest()

    val forceRepaint: Boolean

    /** True while the latest framebuffer is zero-sized (for example, minimized). */
    val isMinimized: Boolean
        get() = false

    val droppedEvents: Int
}

/**
 * Serializes one frame opportunity into the section 14 ordering.
 *
 * The scheduler owns no GL or GLFW state. It drains input, applies a pending
 * resize, skips a zero-sized framebuffer, decides whether to render, uploads
 * only when the matrix is dirty or a resize was applied, then draws and swaps
 * at most once.
 *
 * A minimized framebuffer consumes no repaint request and performs no sink
 * work, so restoration can render the pending state. A listener or resize
 * exception propagates before the repaint request is consumed. If a sink
 * operation fails after consumption, the request is restored so the next tick
 * retries the rendered frame.
 */
internal class FrameScheduler(
    private val matrix: TerminalMatrix,
    private val sink: FrameSink,
    private val host: FrameHost,
) {
    private var emptyStats: RenderStats = zeroStats()
    private var renderedStats: RenderStats? = null

    internal fun tick(): RenderStats {
        host.drainEvents()
        val resized = host.applyPendingResize()
        if (host.isMinimized) {
            return emptyStats()
        }
        val repaintRequested = host.consumeRepaintRequest()
        val dirty = matrix.isDirty

        if (!dirty && !resized && !repaintRequested && !host.forceRepaint) {
            return emptyStats()
        }

        var uploads = 0
        var uploadBytes = 0L
        var cellsDrawn = 0
        try {
            if (dirty || resized) {
                sink.upload()
                matrix.clearDirty()
                uploads = 1
                cellsDrawn = matrix.columns * matrix.rows
                uploadBytes = cellsDrawn.toLong() * CELL_BYTES
            }
            sink.draw()
            sink.swap()
        } catch (failure: Throwable) {
            if (repaintRequested) {
                try {
                    host.restoreRepaintRequest()
                } catch (restoreFailure: Throwable) {
                    failure.addSuppressed(restoreFailure)
                }
            }
            throw failure
        }

        val columns = matrix.columns
        val rows = matrix.rows
        val droppedEvents = host.droppedEvents
        val current = renderedStats
        if (current != null &&
            current.columns == columns &&
            current.rows == rows &&
            current.uploads == uploads &&
            current.uploadBytes == uploadBytes &&
            current.drawCalls == 1 &&
            current.cellsDrawn == cellsDrawn &&
            current.droppedEvents == droppedEvents
        ) {
            return current
        }
        return RenderStats(
            columns = columns,
            rows = rows,
            uploads = uploads,
            uploadBytes = uploadBytes,
            drawCalls = 1,
            cellsDrawn = cellsDrawn,
            droppedEvents = droppedEvents,
        ).also { renderedStats = it }
    }

    private fun emptyStats(): RenderStats {
        val current = emptyStats
        val columns = matrix.columns
        val rows = matrix.rows
        val dropped = host.droppedEvents
        if (current.columns == columns && current.rows == rows && current.droppedEvents == dropped) {
            return current
        }
        return zeroStats().also { emptyStats = it }
    }

    private fun zeroStats(): RenderStats =
        RenderStats(
            columns = matrix.columns,
            rows = matrix.rows,
            uploads = 0,
            uploadBytes = 0L,
            drawCalls = 0,
            cellsDrawn = 0,
            droppedEvents = host.droppedEvents,
        )

    private companion object {
        private const val CELL_BYTES: Int = 16
    }
}
