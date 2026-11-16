package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.CursorImage
import com.psiwray.organization.terminal_window.CursorShape
import com.psiwray.organization.terminal_window.GlException
import com.psiwray.organization.terminal_window.GlfwBackend
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryUtil

/**
 * Service-thread cursor creation, standard-shape caching, and destruction.
 *
 * All methods must be called on the GLFW service thread. The cache is
 * process-wide because GLFW standard cursors are not tied to a window.
 */
internal object CursorHandle {
    private val standardCursors: MutableMap<CursorShape, Long> = HashMap()

    /** Returns a cached standard cursor, creating it on first use. */
    internal fun standard(
        shape: CursorShape,
        backend: GlfwBackend,
    ): Long {
        standardCursors[shape]?.let { return it }
        val cursor = backend.createStandardCursor(glfwShape(shape))
        if (cursor != MemoryUtil.NULL) {
            standardCursors[shape] = cursor
            return cursor
        }
        if (shape != CursorShape.ARROW) {
            // Consume the unsupported-shape error before trying the fallback.
            backend.takeError()
            val fallback = standard(CursorShape.ARROW, backend)
            standardCursors[shape] = fallback
            return fallback
        }
        fail(backend, "GLFW could not create the standard arrow cursor")
    }

    /** Creates one native custom cursor from [image]. */
    internal fun create(
        image: CursorImage,
        backend: GlfwBackend,
    ): Long {
        val cursor =
            backend.createCursor(
                width = image.width,
                height = image.height,
                hotspotX = image.hotspotX,
                hotspotY = image.hotspotY,
                rgba = image.rgba,
            )
        if (cursor == MemoryUtil.NULL) {
            fail(backend, "GLFW could not create a ${image.width}x${image.height} cursor")
        }
        return cursor
    }

    /** Destroys one native cursor handle; a null handle is ignored. */
    internal fun destroy(
        cursor: Long,
        backend: GlfwBackend,
    ) {
        if (cursor != MemoryUtil.NULL) {
            backend.destroyCursor(cursor)
        }
    }

    /** Destroys every cached standard cursor. Called before GLFW termination. */
    internal fun releaseStandardCursors(backend: GlfwBackend) {
        val cursors = standardCursors.values.toSet()
        standardCursors.clear()
        for (cursor in cursors) {
            backend.destroyCursor(cursor)
        }
    }

    private fun glfwShape(shape: CursorShape): Int =
        when (shape) {
            CursorShape.ARROW -> GLFW.GLFW_ARROW_CURSOR
            CursorShape.IBEAM -> GLFW.GLFW_IBEAM_CURSOR
            CursorShape.CROSSHAIR -> GLFW.GLFW_CROSSHAIR_CURSOR
            CursorShape.HAND -> GLFW.GLFW_HAND_CURSOR
            CursorShape.HRESIZE -> GLFW.GLFW_HRESIZE_CURSOR
            CursorShape.VRESIZE -> GLFW.GLFW_VRESIZE_CURSOR
            CursorShape.RESIZE_ALL -> GLFW.GLFW_RESIZE_ALL_CURSOR
            CursorShape.NOT_ALLOWED -> GLFW.GLFW_NOT_ALLOWED_CURSOR
        }

    private fun fail(
        backend: GlfwBackend,
        message: String,
    ): Nothing {
        backend.takeError()?.let { throw it.asException() }
        throw GlException(message)
    }
}
