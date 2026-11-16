package com.psiwray.organization.terminal_window.font

/**
 * A strict `.twf` syntax or consistency failure with its source position.
 *
 * @since 2.0.0
 */
public class FontFormatException public constructor(
    /** `TW-001` through `TW-011`. @since 2.0.0 */
    public val code: String,
    /** Source path, or `<input>` for anonymous streams and readers. @since 2.0.0 */
    public val path: String,
    /** One-based logical line number. @since 2.0.0 */
    public val line: Int,
    /** One-based Unicode-scalar column number. @since 2.0.0 */
    public val column: Int,
    message: String,
) : IllegalArgumentException("$path:$line:$column: $code: $message")
