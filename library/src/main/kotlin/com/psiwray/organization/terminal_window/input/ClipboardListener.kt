package com.psiwray.organization.terminal_window.input

/**
 * Receives completed clipboard reads on the window owner thread.
 *
 * Register with `TerminalWindow.addClipboardListener` and start a read with
 * `TerminalWindow.requestClipboardText`. The result is delivered by the frame
 * pipeline after the input events drained in the same frame, in request order.
 * A clipboard that holds no text, or whose contents cannot be converted to
 * text, is reported as `null`.
 *
 * Finished reads are separate from [InputEvent] because the sealed hierarchy is
 * closed for source compatibility: adding clipboard state never changes an
 * exhaustive `when` over input events.
 *
 * @since 2.1.0
 */
public fun interface ClipboardListener {
    /**
     * Called once for each completed clipboard read.
     *
     * @param text the clipboard text, or `null` when the clipboard held no text
     *   or could not be converted to text.
     * @since 2.1.0
     */
    public fun onClipboardText(text: String?)
}
