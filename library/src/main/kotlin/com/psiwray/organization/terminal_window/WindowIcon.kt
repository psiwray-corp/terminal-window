package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.internal.ImageLoader
import com.psiwray.organization.terminal_window.internal.LoadedImage
import com.psiwray.organization.terminal_window.internal.argbToRgba
import java.io.IOException
import java.nio.file.Path

/**
 * An immutable window icon image.
 *
 * Pixels use `0xAARRGGBB`, row-major order with a top-left origin. Alpha is
 * stored but icons are not premultiplied. The caller's pixel array is copied by
 * [of]; later mutation of the input does not affect this icon.
 *
 * The constructor is private because array identity would make generated
 * `equals`/`hashCode` methods misleading. Use [of] or [fromPath].
 *
 * @since 2.0.0
 */
public class WindowIcon private constructor(
    /** The image width in pixels. @since 2.0.0 */
    public val width: Int,
    /** The image height in pixels. @since 2.0.0 */
    public val height: Int,
    private val argb: IntArray,
    internal val rgba: ByteArray,
) {
    /** Returns a copy of the ARGB pixels in row-major, top-left-origin order. @since 2.0.0 */
    public fun copyPixels(): IntArray = argb.copyOf()

    /** Builds an icon from ARGB pixels. @since 2.0.0 */
    public companion object {
        /**
         * Copies and validates [pixels].
         *
         * @throws IllegalArgumentException if a dimension is not positive or
         *   the pixel count does not match the dimensions.
         * @since 2.0.0
         */
        @JvmStatic
        public fun of(
            width: Int,
            height: Int,
            pixels: IntArray,
        ): WindowIcon {
            require(width > 0) { "width must be positive: $width" }
            require(height > 0) { "height must be positive: $height" }
            val pixelCount = width.toLong() * height.toLong()
            require(pixelCount <= Int.MAX_VALUE.toLong()) {
                "pixel count $pixelCount exceeds Int range"
            }
            require(pixelCount <= (Int.MAX_VALUE / 4).toLong()) {
                "pixel byte count ${pixelCount * 4} exceeds Int range"
            }
            require(pixels.size == pixelCount.toInt()) {
                "pixels.size must be ${pixelCount.toInt()}: ${pixels.size}"
            }
            val copy = pixels.copyOf()
            return WindowIcon(width, height, copy, argbToRgba(copy))
        }

        /**
         * Decodes an image file with stb_image and copies its pixels.
         *
         * Supported formats are those compiled into LWJGL's stb_image,
         * including PNG, JPEG, BMP, TGA, and GIF. Supply several sizes to
         * [TerminalWindow.setIcon] when the platform should choose the best
         * icon for the taskbar or window manager.
         *
         * @throws IllegalArgumentException if the decoded dimensions are invalid.
         * @throws IOException if the file cannot be read or decoded.
         * @since 2.0.0
         */
        @JvmStatic
        @Throws(IOException::class)
        public fun fromPath(path: Path): WindowIcon = fromLoaded(ImageLoader.load(path))

        internal fun fromLoaded(loaded: LoadedImage): WindowIcon {
            require(loaded.width > 0) { "width must be positive: ${loaded.width}" }
            require(loaded.height > 0) { "height must be positive: ${loaded.height}" }
            val pixelCount = loaded.width.toLong() * loaded.height.toLong()
            require(pixelCount <= (Int.MAX_VALUE / 4).toLong()) {
                "decoded pixel count $pixelCount exceeds Int range"
            }
            require(loaded.argb.size == pixelCount.toInt()) {
                "decoded pixel count does not match ${loaded.width}x${loaded.height}"
            }
            require(loaded.rgba.size == pixelCount.toInt() * 4) {
                "decoded RGBA byte count does not match ${loaded.width}x${loaded.height}"
            }
            return WindowIcon(
                loaded.width,
                loaded.height,
                loaded.argb.copyOf(),
                loaded.rgba.copyOf(),
            )
        }
    }
}
