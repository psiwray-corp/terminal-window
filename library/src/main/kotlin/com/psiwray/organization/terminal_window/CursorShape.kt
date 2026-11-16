package com.psiwray.organization.terminal_window

/**
 * Standard cursor shapes understood by [TerminalWindow.setCursor].
 *
 * Unsupported platform shapes fall back to [ARROW].
 *
 * @since 2.0.0
 */
public enum class CursorShape {
    /** The platform's default arrow cursor. @since 2.0.0 */
    ARROW,

    /** A text insertion cursor. @since 2.0.0 */
    IBEAM,

    /** A crosshair cursor. @since 2.0.0 */
    CROSSHAIR,

    /** A pointing-hand cursor. @since 2.0.0 */
    HAND,

    /** A horizontal-resize cursor. @since 2.0.0 */
    HRESIZE,

    /** A vertical-resize cursor. @since 2.0.0 */
    VRESIZE,

    /** A two-axis resize cursor. @since 2.0.0 */
    RESIZE_ALL,

    /** A not-allowed cursor. @since 2.0.0 */
    NOT_ALLOWED,
}
