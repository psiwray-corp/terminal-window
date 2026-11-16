package com.psiwray.organization.terminal_window

/**
 * Helpers and constants for the public ARGB color representation.
 *
 * Every color in the public API is an `Int` laid out as `0xAARRGGBB`.
 * The renderer converts these values to RGBA byte order exactly once, at the
 * GPU boundary. JOML is not used for colors.
 *
 * @since 2.0.0
 */
public object Colors {
    /** Fully transparent black (`0x00000000`). @since 2.0.0 */
    public const val TRANSPARENT: Int = 0x00000000

    /** Opaque black (`0xFF000000`). @since 2.0.0 */
    public const val BLACK: Int = 0xFF000000.toInt()

    /** Opaque white (`0xFFFFFFFF`). @since 2.0.0 */
    public const val WHITE: Int = 0xFFFFFFFF.toInt()

    private const val OPAQUE: Int = 0xFF

    /**
     * Builds an opaque ARGB color from 8-bit [red], [green], and [blue]
     * channels.
     *
     * @throws IllegalArgumentException if any channel is outside `0..255`.
     * @since 2.0.0
     */
    @JvmStatic
    public fun rgb(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = rgba(red, green, blue, OPAQUE)

    /**
     * Builds an ARGB color from 8-bit [red], [green], [blue], and [alpha]
     * channels.
     *
     * @throws IllegalArgumentException if any channel is outside `0..255`.
     * @since 2.0.0
     */
    @JvmStatic
    public fun rgba(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): Int {
        requireChannel(red, "red")
        requireChannel(green, "green")
        requireChannel(blue, "blue")
        requireChannel(alpha, "alpha")
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun requireChannel(
        value: Int,
        name: String,
    ) {
        require(value in 0..255) { "$name must be in 0..255: $value" }
    }
}
