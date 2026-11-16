package com.psiwray.organization.terminal_window

/**
 * Immutable counters for one frame-scheduler tick.
 *
 * [uploads], [uploadBytes], [drawCalls], and [cellsDrawn] describe the tick.
 * [droppedEvents] is the window's cumulative dropped-input count at the end of
 * the tick; it is not a per-tick counter and is not reset by the scheduler.
 *
 * @property columns matrix columns after input dispatch and resize application.
 * @property rows matrix rows after input dispatch and resize application.
 * @property uploads number of whole-matrix uploads: `0` or `1`.
 * @property uploadBytes bytes uploaded in this tick; `0` when no upload occurred.
 * @property drawCalls number of draw calls: `0` or `1`.
 * @property cellsDrawn cells included in the upload; `0` when no upload occurred.
 * @property droppedEvents cumulative discrete input events dropped by the window.
 * @since 2.0.0
 */
public data class RenderStats(
    public val columns: Int,
    public val rows: Int,
    public val uploads: Int,
    public val uploadBytes: Long,
    public val drawCalls: Int,
    public val cellsDrawn: Int,
    public val droppedEvents: Int,
)
