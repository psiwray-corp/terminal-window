package com.psiwray.organization.terminal_window.input

/** An immutable input event delivered on the window owner thread. @since 2.0.0 */
public sealed interface InputEvent

/**
 * A keyboard event.
 *
 * [repeat] is true when GLFW reported a repeat action. The event stream keeps
 * repeat actions distinct from the initial press; [action] is [KeyAction.REPEAT]
 * for the same event.
 *
 * @property key mapped key token.
 * @property scancode platform-specific physical-key scancode.
 * @property action press, release, or repeat.
 * @property modifiers modifier keys active for this event.
 * @property repeat whether this event is a repeat action.
 * @throws IllegalArgumentException if [repeat] and [action] disagree.
 * @since 2.0.0
 */
public data class KeyEvent(
    public val key: Key,
    public val scancode: Int,
    public val action: KeyAction,
    public val modifiers: Modifiers,
    public val repeat: Boolean,
) : InputEvent {
    init {
        require(repeat == (action == KeyAction.REPEAT)) {
            "repeat must be true exactly when action is REPEAT: action=$action, repeat=$repeat"
        }
    }
}

/**
 * A Unicode code point produced by the operating system text-input system.
 *
 * GLFW's character callback has no modifier argument, so this event carries no
 * modifiers. A single code point may be outside the Basic Multilingual Plane.
 *
 * @property codePoint Unicode scalar value.
 * @throws IllegalArgumentException if [codePoint] is not a Unicode scalar value.
 * @since 2.0.0
 */
public data class CharEvent(
    public val codePoint: Int,
) : InputEvent {
    init {
        require(codePoint in 0..MAX_CODE_POINT && codePoint !in SURROGATE_RANGE) {
            "codePoint must be a Unicode scalar value: $codePoint"
        }
    }

    private companion object {
        private const val MAX_CODE_POINT: Int = 0x10FFFF
        private val SURROGATE_RANGE: IntRange = 0xD800..0xDFFF
    }
}

/**
 * A cursor movement.
 *
 * [pixelX] and [pixelY] are content-area pixels after applying the current
 * content scale. [column] and [row] are the corresponding cell coordinates and
 * may be negative outside the content area.
 *
 * @property pixelX horizontal framebuffer pixel.
 * @property pixelY vertical framebuffer pixel.
 * @property column cell column containing the cursor.
 * @property row cell row containing the cursor.
 * @since 2.0.0
 */
public data class MouseMoveEvent(
    public val pixelX: Int,
    public val pixelY: Int,
    public val column: Int,
    public val row: Int,
) : InputEvent

/**
 * The cursor entered or left the window content area.
 *
 * @property entered true on enter, false on leave.
 * @since 2.0.0
 */
public data class CursorEnterEvent(
    public val entered: Boolean,
) : InputEvent

/**
 * A mouse-button event with the cell coordinates at the time of the event.
 *
 * @property button mouse button.
 * @property action press or release action.
 * @property modifiers modifier keys active for this event.
 * @property column cell column at the event.
 * @property row cell row at the event.
 * @since 2.0.0
 */
public data class MouseButtonEvent(
    public val button: MouseButton,
    public val action: KeyAction,
    public val modifiers: Modifiers,
    public val column: Int,
    public val row: Int,
) : InputEvent

/**
 * A scroll-wheel movement in platform-defined scroll units.
 *
 * @property xOffset horizontal scroll offset.
 * @property yOffset vertical scroll offset.
 * @throws IllegalArgumentException if an offset is not finite.
 * @since 2.0.0
 */
public data class ScrollEvent(
    public val xOffset: Double,
    public val yOffset: Double,
) : InputEvent {
    init {
        require(xOffset.isFinite()) { "xOffset must be finite: $xOffset" }
        require(yOffset.isFinite()) { "yOffset must be finite: $yOffset" }
    }
}

/**
 * The framebuffer changed size, in pixels.
 *
 * @property framebufferWidth new framebuffer width.
 * @property framebufferHeight new framebuffer height.
 * @throws IllegalArgumentException if a framebuffer dimension is negative.
 * @since 2.0.0
 */
public data class ResizeEvent(
    public val framebufferWidth: Int,
    public val framebufferHeight: Int,
) : InputEvent {
    init {
        require(framebufferWidth >= 0) { "framebufferWidth must be non-negative: $framebufferWidth" }
        require(framebufferHeight >= 0) { "framebufferHeight must be non-negative: $framebufferHeight" }
    }
}

/**
 * The window content scale changed.
 *
 * @property x horizontal content scale.
 * @property y vertical content scale.
 * @throws IllegalArgumentException if a scale is not finite and positive.
 * @since 2.0.0
 */
public data class ContentScaleEvent(
    public val x: Float,
    public val y: Float,
) : InputEvent {
    init {
        require(x > 0.0f && x.isFinite()) { "x must be finite and positive: $x" }
        require(y > 0.0f && y.isFinite()) { "y must be finite and positive: $y" }
    }
}

/**
 * The window gained or lost input focus.
 *
 * @property focused whether the window now has focus.
 * @since 2.0.0
 */
public data class FocusEvent(
    public val focused: Boolean,
) : InputEvent

/** The user requested that the window close; listeners may veto it. @since 2.0.0 */
public data object CloseRequestedEvent : InputEvent
