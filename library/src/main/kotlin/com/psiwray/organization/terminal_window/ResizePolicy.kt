package com.psiwray.organization.terminal_window

/**
 * Determines how [TerminalMatrix.resize] handles existing cell contents.
 *
 * @since 2.0.0
 */
public enum class ResizePolicy {
    /**
     * Copies the overlapping top-left block and fills newly exposed cells with
     * a space and the matrix's default colors.
     * @since 2.0.0
     */
    PRESERVE_TOP_LEFT,

    /** Discards all cells and fills the new matrix with spaces and default colors. @since 2.0.0 */
    CLEAR,
}
