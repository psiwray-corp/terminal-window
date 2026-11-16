package com.psiwray.organization.terminal_window

/**
 * A GLFW error copied at the checked service-thread operation that observed it.
 *
 * GLFW's description string is valid only until the next GLFW call or callback;
 * [description] is materialized immediately so it remains safe to inspect.
 *
 * @since 2.0.0
 */
public class GlfwException(
    /** The numeric `GLFW_*` error code. @since 2.0.0 */
    public val code: Int,
    /** The copied, human-readable error description. @since 2.0.0 */
    public val description: String,
) : RuntimeException("GLFW error 0x${code.toString(16)}: $description")
