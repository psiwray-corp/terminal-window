package com.psiwray.organization.terminal_window.input

/**
 * Receives input events on the window owner thread.
 *
 * Every method has a no-op default, so listeners implement only the events they
 * care about. Listeners run in registration order. Adding a listener during
 * dispatch affects later events, not the event currently being delivered.
 *
 * @since 2.0.0
 */
public interface InputListener {
    /** Called for every keyboard press, repeat, and release. @since 2.0.0 */
    public fun onKey(event: KeyEvent) {}

    /** Called for text input produced by the operating system. @since 2.0.0 */
    public fun onChar(event: CharEvent) {}

    /** Called when the cursor enters or leaves the window content area. @since 2.0.0 */
    public fun onCursorEnter(event: CursorEnterEvent) {}

    /** Called for cursor movement. @since 2.0.0 */
    public fun onMouseMove(event: MouseMoveEvent) {}

    /** Called for mouse-button press, repeat, and release. @since 2.0.0 */
    public fun onMouseButton(event: MouseButtonEvent) {}

    /** Called for scroll-wheel movement. @since 2.0.0 */
    public fun onScroll(event: ScrollEvent) {}

    /** Called when the framebuffer size changes. @since 2.0.0 */
    public fun onResize(event: ResizeEvent) {}

    /** Called when the window content scale changes. @since 2.0.0 */
    public fun onContentScale(event: ContentScaleEvent) {}

    /** Called when the window gains or loses focus. @since 2.0.0 */
    public fun onFocus(event: FocusEvent) {}

    /** Called when the user requests that the window close. @since 2.0.0 */
    public fun onCloseRequested(event: CloseRequestedEvent) {}
}
