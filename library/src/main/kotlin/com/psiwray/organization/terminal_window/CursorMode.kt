package com.psiwray.organization.terminal_window

/**
 * Cursor input mode for a [TerminalWindow].
 *
 * [HIDDEN] hides the operating-system cursor but keeps normal cursor
 * coordinates. [DISABLED] hides and grabs the cursor and reports virtual,
 * unbounded movement. Visibility and mode interact as described by
 * [TerminalWindow.setCursorVisible] and [TerminalWindow.setCursorMode].
 *
 * @since 2.0.0
 */
public enum class CursorMode {
    /** The cursor is normally visible and moves without being grabbed. @since 2.0.0 */
    NORMAL,

    /** The cursor is hidden but still moves normally. @since 2.0.0 */
    HIDDEN,

    /** The cursor is hidden, grabbed, and reported as virtual unbounded movement. @since 2.0.0 */
    DISABLED,
}
