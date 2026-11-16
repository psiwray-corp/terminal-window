package com.psiwray.organization.terminal_window

/**
 * An OpenGL environment or context failure that is not a raw GLFW error.
 *
 * GLFW-originated failures are reported as [GlfwException]. This exception is
 * used when GLFW succeeded but the resulting context cannot satisfy the
 * library's requirements, for example an OpenGL version below 3.3.
 *
 * @param message the failure description, including requested and actual
 *   context capabilities when available.
 * @since 2.0.0
 */
public class GlException(
    message: String,
) : RuntimeException(message)
