package com.psiwray.organization.terminal_window.internal

import com.psiwray.organization.terminal_window.input.CursorPosition
import com.psiwray.organization.terminal_window.input.Key
import com.psiwray.organization.terminal_window.input.MouseButton
import org.lwjgl.glfw.GLFW
import kotlin.math.floor

/** A cursor position copied from GLFW, in screen coordinates. */
internal data class CursorSample(
    val x: Double,
    val y: Double,
)

/**
 * Immutable service-thread snapshot backing the public direct-state queries.
 *
 * The snapshot is replaced atomically after every GLFW poll. Owner and
 * application threads only read this value; they never query GLFW.
 */
internal data class DeviceState(
    val pressedKeys: Set<Key>,
    val pressedMouseButtons: Set<MouseButton>,
    val cursor: CursorPosition,
) {
    internal fun isKeyPressed(key: Key): Boolean = key != Key.UNKNOWN && key in pressedKeys

    internal fun isMouseButtonPressed(button: MouseButton): Boolean = button in pressedMouseButtons

    internal companion object {
        internal val EMPTY: DeviceState =
            DeviceState(
                pressedKeys = emptySet(),
                pressedMouseButtons = emptySet(),
                cursor = CursorPosition(0.0, 0.0, 0, 0),
            )
    }
}

/**
 * Builds a snapshot from a backend polling surface.
 *
 * [keyState] and [mouseButtonState] return GLFW press/release values. This
 * function performs no GLFW calls itself; tests supply fakes.
 */
internal fun sampleDeviceState(
    keyState: (Int) -> Int,
    mouseButtonState: (Int) -> Int,
    cursor: CursorSample,
    cellWidth: Int,
    cellHeight: Int,
    scale: Int,
): DeviceState {
    val keys =
        buildSet {
            for (key in Key.entries) {
                if (key != Key.UNKNOWN && keyState(key.glfwCode) == GLFW.GLFW_PRESS) {
                    add(key)
                }
            }
        }
    val buttons =
        buildSet {
            for (button in MouseButton.entries) {
                if (mouseButtonState(button.glfwCode) == GLFW.GLFW_PRESS) {
                    add(button)
                }
            }
        }
    return DeviceState(
        pressedKeys = keys,
        pressedMouseButtons = buttons,
        cursor = cursorPosition(cursor, cellWidth, cellHeight, scale),
    )
}

/** Converts screen coordinates to cell coordinates. */
internal fun cursorPosition(
    cursor: CursorSample,
    cellWidth: Int,
    cellHeight: Int,
    scale: Int,
): CursorPosition {
    require(cellWidth >= 1) { "cellWidth must be positive: $cellWidth" }
    require(cellHeight >= 1) { "cellHeight must be positive: $cellHeight" }
    require(scale >= 1) { "scale must be positive: $scale" }
    val column = floor(cursor.x / (cellWidth.toDouble() * scale)).toInt()
    val row = floor(cursor.y / (cellHeight.toDouble() * scale)).toInt()
    return CursorPosition(cursor.x, cursor.y, column, row)
}
