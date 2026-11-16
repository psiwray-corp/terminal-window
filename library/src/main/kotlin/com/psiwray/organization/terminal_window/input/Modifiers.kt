package com.psiwray.organization.terminal_window.input

import org.lwjgl.glfw.GLFW

/** Keyboard modifier bits carried by input events. @since 2.0.0 */
@JvmInline
public value class Modifiers(
    /** Combined `GLFW_MOD_*` bit mask. @since 2.0.0 */
    public val bits: Int,
) {
    /** Returns whether every bit in [modifier] is set. @since 2.0.0 */
    public fun has(modifier: Modifiers): Boolean = modifier.bits != 0 && (bits and modifier.bits) == modifier.bits

    /** Named modifier bits. @since 2.0.0 */
    public companion object {
        /** No modifier keys are active. @since 2.0.0 */
        public val NONE: Modifiers = Modifiers(0)

        /** Shift is active. @since 2.0.0 */
        public val SHIFT: Modifiers = Modifiers(GLFW.GLFW_MOD_SHIFT)

        /** Control is active. @since 2.0.0 */
        public val CONTROL: Modifiers = Modifiers(GLFW.GLFW_MOD_CONTROL)

        /** Alt is active. @since 2.0.0 */
        public val ALT: Modifiers = Modifiers(GLFW.GLFW_MOD_ALT)

        /** Super (Windows/Command) is active. @since 2.0.0 */
        public val SUPER: Modifiers = Modifiers(GLFW.GLFW_MOD_SUPER)

        /** Caps Lock is active. @since 2.0.0 */
        public val CAPS_LOCK: Modifiers = Modifiers(GLFW.GLFW_MOD_CAPS_LOCK)

        /** Num Lock is active. @since 2.0.0 */
        public val NUM_LOCK: Modifiers = Modifiers(GLFW.GLFW_MOD_NUM_LOCK)

        internal const val MASK: Int =
            GLFW.GLFW_MOD_SHIFT or
                GLFW.GLFW_MOD_CONTROL or
                GLFW.GLFW_MOD_ALT or
                GLFW.GLFW_MOD_SUPER or
                GLFW.GLFW_MOD_CAPS_LOCK or
                GLFW.GLFW_MOD_NUM_LOCK

        internal fun fromGlfw(bits: Int): Modifiers = Modifiers(bits and MASK)
    }
}
