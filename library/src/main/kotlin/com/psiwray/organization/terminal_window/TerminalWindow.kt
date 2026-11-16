package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.font.BitmapFont
import com.psiwray.organization.terminal_window.input.CursorPosition
import com.psiwray.organization.terminal_window.input.InputListener
import com.psiwray.organization.terminal_window.input.Key
import com.psiwray.organization.terminal_window.input.MouseButton
import com.psiwray.organization.terminal_window.internal.WindowHandle

/**
 * One terminal window and OpenGL context.
 *
 * The window is created on the [TerminalWindowService] thread. Its context
 * starts current on that thread. In multi-threaded mode, transfer it
 * explicitly before the owner thread renders:
 *
 * ```kotlin
 * serviceThread.releaseContext()
 * ownerThread.acquireContext()
 * ```
 *
 * Window state changes are queued to the service thread. Matrix writes and
 * `forceRepaint` are owner-thread controls. `requestRepaint()` is a coalesced,
 * one-shot request and does not call OpenGL directly.
 *
 * Closing is two-phase. [close] releases the context if the caller owns it,
 * marks the window closed, and queues native destruction. The service thread
 * performs `glfwDestroyWindow` during a later [TerminalWindowService.pump] or
 * [TerminalWindowService.close]. [awaitClosed] waits for that native
 * destruction when called from a non-service thread.
 *
 * Thread affinity:
 * - Service thread: [frame], [acquireContext], [releaseContext], and [close]
 *   when the service thread owns the context.
 * - Owner thread: [matrix] writes, [forceRepaint], [frame], and context
 *   release/acquire.
 * - Any thread: [setTitle], [setCursor], [setCursorVisible], [setCursorMode],
 *   [setIcon], [clearIcon], [addInputListener], [removeInputListener],
 *   [isKeyPressed], [isMouseButtonPressed], [cursorPosition], [renderStats],
 *   [requestRepaint], [requestClose], [setCloseRequested], [setVisible],
 *   [setResizable], [shouldClose], [droppedEvents], and [awaitClosed].
 * @since 2.0.0
 */
public class TerminalWindow internal constructor(
    private val handle: WindowHandle,
    /** Immutable configuration used to create this window. @since 2.0.0 */
    public val config: TerminalWindowConfig,
) : AutoCloseable {
    /** Immutable parsed font used for cell dimensions and glyph masks. @since 2.0.0 */
    public val font: BitmapFont
        get() = config.font

    /** Immutable context and driver capabilities captured at creation. @since 2.0.0 */
    public val contextInfo: ContextInfo
        get() = handle.contextInfo

    /** Mutable cell matrix owned by this window's owner thread. @since 2.0.0 */
    public val matrix: TerminalMatrix
        get() = handle.matrix

    /** Whether [close] has been called on this window. @since 2.0.0 */
    public val isClosed: Boolean
        get() = handle.isClosed

    /**
     * Number of discrete input events dropped because the window queue was full.
     *
     * The value is monotonic for the lifetime of the window. Cursor movement and
     * the latest window-state events are coalesced instead of counted here.
     * @since 2.0.0
     */
    public val droppedEvents: Int
        get() = handle.droppedEvents

    /**
     * Cached GLFW close flag.
     *
     * The flag is updated on the service thread while pumping. It is safe to
     * read from any thread and never queries GLFW directly.
     * @since 2.0.0
     */
    public val shouldClose: Boolean
        get() = handle.shouldClose

    /**
     * Continuous repaint control.
     *
     * When true, the frame scheduler treats every frame opportunity as
     * renderable even when the matrix is clean. This value is owner-thread
     * oriented; it is read by the frame path and never calls GL.
     * @since 2.0.0
     */
    public var forceRepaint: Boolean
        get() = handle.forceRepaint
        set(value) {
            handle.forceRepaint = value
        }

    /**
     * Requests a title change.
     *
     * The request is applied by the service thread before its next poll. The
     * caller is not required to be the owner thread.
     * @since 2.0.0
     */
    public fun setTitle(title: String) {
        handle.setTitle(title)
    }

    /** Registers [listener] to receive events on the window owner thread. @since 2.0.0 */
    public fun addInputListener(listener: InputListener) {
        handle.addInputListener(listener)
    }

    /** Removes [listener]; no further events are delivered to it. @since 2.0.0 */
    public fun removeInputListener(listener: InputListener) {
        handle.removeInputListener(listener)
    }

    /**
     * Returns the last polled GLFW state for [key].
     *
     * This is a cached snapshot, not an OS device query. A press and release
     * between two polls is therefore reported as released. The method is safe
     * from any thread and never calls GLFW.
     * @since 2.0.0
     */
    public fun isKeyPressed(key: Key): Boolean = handle.isKeyPressed(key)

    /**
     * Returns the last polled GLFW state for [button].
     *
     * This is a cached snapshot, not an OS device query. The method is safe
     * from any thread and never calls GLFW.
     * @since 2.0.0
     */
    public fun isMouseButtonPressed(button: MouseButton): Boolean = handle.isMouseButtonPressed(button)

    /**
     * Returns the last polled cursor position.
     *
     * Screen coordinates are relative to the content area; [CursorPosition.column]
     * and [CursorPosition.row] may be negative outside the content area. The
     * method is safe from any thread and never calls GLFW.
     * @since 2.0.0
     */
    public fun cursorPosition(): CursorPosition = handle.cursorPositionSnapshot()

    /**
     * Runs one frame opportunity.
     *
     * On the service thread this pumps GLFW first, then drains input, applies
     * a pending resize, and uploads/draws/swaps at most once. On a non-service
     * owner thread it does not pump; the service thread must pump separately.
     *
     * The owning context must be current on the calling thread. A clean,
     * unforced, unrequested frame performs no upload, draw, or swap.
     *
     * @throws IllegalStateException if this window is closed, the caller is not
     *   the context owner, or the caller is on the service thread without
     *   owning the context.
     * @since 2.0.0
     */
    public fun frame() {
        handle.frame()
    }

    /**
     * Returns counters for the most recent [frame] opportunity.
     *
     * Before the first frame all per-tick counters are zero. The returned
     * snapshot is immutable.
     * @since 2.0.0
     */
    public fun renderStats(): RenderStats = handle.renderStats()

    /** Requests this window to close after the service thread applies the flag. @since 2.0.0 */
    public fun requestClose() {
        handle.setCloseRequested(true)
    }

    /**
     * Sets the GLFW close-requested flag through the service thread.
     *
     * This is a request flag, not [close]: the window remains open and usable
     * until the application closes it.
     * @since 2.0.0
     */
    public fun setCloseRequested(requested: Boolean) {
        handle.setCloseRequested(requested)
    }

    /** Shows or hides the window through the service thread. @since 2.0.0 */
    public fun setVisible(visible: Boolean) {
        handle.setVisible(visible)
    }

    /** Changes whether the user may resize the window through the service thread. @since 2.0.0 */
    public fun setResizable(resizable: Boolean) {
        handle.setResizable(resizable)
    }

    /**
     * Replaces the cursor with a standard [shape].
     *
     * The native cursor is selected or created on the service thread. A
     * previously installed custom cursor is destroyed after the new cursor is
     * installed. Safe from any thread.
     * @since 2.0.0
     */
    public fun setCursor(shape: CursorShape) {
        handle.setCursor(shape)
    }

    /**
     * Replaces the cursor with a custom [image].
     *
     * The image is immutable; callers may reuse it. The native cursor is
     * created on the service thread. A previously installed custom cursor is
     * destroyed after the new cursor is installed. Safe from any thread.
     * @since 2.0.0
     */
    public fun setCursor(image: CursorImage) {
        handle.setCursor(image)
    }

    /**
     * Records whether the cursor should be visible when the mode allows it.
     *
     * When [CursorMode.DISABLED] is active this only updates the recorded
     * visibility; the cursor remains hidden and grabbed until
     * [setCursorMode] changes the mode. Safe from any thread.
     * @since 2.0.0
     */
    public fun setCursorVisible(visible: Boolean) {
        handle.setCursorVisible(visible)
    }

    /**
     * Sets the cursor input mode.
     *
     * [CursorMode.DISABLED] overrides recorded visibility. Returning to
     * [CursorMode.NORMAL] or [CursorMode.HIDDEN] applies the visibility set by
     * [setCursorVisible]. Safe from any thread.
     * @since 2.0.0
     */
    public fun setCursorMode(mode: CursorMode) {
        handle.setCursorMode(mode)
    }

    /**
     * Replaces the window icon with one or more sizes.
     *
     * Pixels are copied into immutable [WindowIcon] values before this call;
     * the operating system chooses among the supplied sizes. The operation is
     * queued to the service thread. Use [clearIcon] to restore the platform
     * default. Safe from any thread.
     *
     * @throws IllegalArgumentException if no icons are supplied.
     * @since 2.0.0
     */
    public fun setIcon(vararg icons: WindowIcon) {
        require(icons.isNotEmpty()) { "at least one icon is required; use clearIcon()" }
        handle.setIcon(icons)
    }

    /**
     * Restores the platform default window icon.
     *
     * The operation is queued to the service thread. Safe from any thread.
     * @since 2.0.0
     */
    public fun clearIcon() {
        handle.setIcon(emptyArray())
    }

    /**
     * Requests exactly one frame using the current bounds.
     *
     * Repeated calls coalesce until the frame scheduler consumes the request.
     * This method never calls GL or GLFW. It is safe from any thread.
     * @since 2.0.0
     */
    public fun requestRepaint() {
        handle.requestRepaint()
    }

    /**
     * Makes this window's context current on the calling thread.
     *
     * The context must not be current on another thread. The caller becomes the
     * owner until it calls [releaseContext] or [close].
     *
     * @throws IllegalStateException if the window is closed or the context is
     *   already owned by another thread.
     * @since 2.0.0
     */
    public fun acquireContext() {
        handle.acquireContext()
    }

    /**
     * Releases this window's context on the calling owner thread.
     *
     * @throws IllegalStateException if the caller is not the owner or the
     *   window is closed.
     * @since 2.0.0
     */
    public fun releaseContext() {
        handle.releaseContext()
    }

    /**
     * Starts the two-phase close protocol.
     *
     * If the caller owns the context, it is released first. The window is then
     * marked closed and native destruction is queued. Calling close more than
     * once is a no-op.
     *
     * @throws IllegalStateException if the context is current on a different
     *   thread; call [releaseContext] on that thread first.
     * @since 2.0.0
     */
    override fun close() {
        handle.close()
    }

    /**
     * Waits for native window destruction.
     *
     * Returns immediately on the service thread, where blocking would deadlock
     * the thread that must perform destruction.
     * @since 2.0.0
     */
    public fun awaitClosed() {
        handle.awaitClosed()
    }

    /** Service-thread hook that refreshes the cached GLFW close flag. */
    internal fun refreshShouldClose() {
        handle.refreshShouldClose()
    }

    /** Service-thread hook that refreshes direct device snapshots. */
    internal fun refreshDeviceState() {
        handle.refreshDeviceState()
    }

    /** One-shot internal hook consumed by the frame scheduler. */
    internal fun consumeRepaintRequest(): Boolean = handle.consumeRepaintRequest()

    /** Owner-thread hook used by the frame pipeline to dispatch queued input. */
    internal fun drainEvents() {
        handle.drainEvents()
    }

    /** Raw handle used only by internal registry and destroy plumbing. */
    internal val rawHandle: Long
        get() = handle.rawHandle

    internal companion object {
        internal fun create(
            service: TerminalWindowService,
            backend: GlfwBackend,
            config: TerminalWindowConfig,
        ): TerminalWindow {
            val handle = WindowHandle.create(service, backend, config)
            return TerminalWindow(handle, config)
        }
    }
}
