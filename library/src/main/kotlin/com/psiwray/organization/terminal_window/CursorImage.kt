package com.psiwray.organization.terminal_window

import com.psiwray.organization.terminal_window.internal.ImageLoader
import com.psiwray.organization.terminal_window.internal.LoadedImage
import com.psiwray.organization.terminal_window.internal.argbToRgba
import java.io.IOException
import java.nio.file.Path

/**
 * An immutable cursor image with a hotspot in image pixel coordinates.
 *
 * Pixels use `0xAARRGGBB`, row-major order with a top-left origin. Alpha is
 * stored but cursor images are not premultiplied. The caller's pixel array is
 * copied by [of]; later mutation of the input does not affect this image.
 *
 * The constructor is private because array identity would make generated
 * `equals`/`hashCode` methods misleading. Use [of] or [fromPath].
 *
 * @since 2.0.0
 */
public class CursorImage private constructor(
    /** The image width in pixels. @since 2.0.0 */
    public val width: Int,
    /** The image height in pixels. @since 2.0.0 */
    public val height: Int,
    /** Horizontal hotspot position in image pixels. @since 2.0.0 */
    public val hotspotX: Int,
    /** Vertical hotspot position in image pixels. @since 2.0.0 */
    public val hotspotY: Int,
    private val argb: IntArray,
    internal val rgba: ByteArray,
) {
    /** Returns a copy of the ARGB pixels in row-major, top-left-origin order. @since 2.0.0 */
    public fun copyPixels(): IntArray = argb.copyOf()

    /** Builds an image from ARGB pixels. @since 2.0.0 */
    public companion object {
        /**
         * Copies and validates [pixels].
         *
         * @throws IllegalArgumentException if a dimension is not positive, the
         *   pixel count does not match the dimensions, or the hotspot is
         *   outside the image.
         * @since 2.0.0
         */
        @JvmStatic
        public fun of(
            width: Int,
            height: Int,
            hotspotX: Int,
            hotspotY: Int,
            pixels: IntArray,
        ): CursorImage {
            validateImage(width, height, pixels)
            require(hotspotX in 0 until width) {
                "hotspotX must be in 0..${width - 1}: $hotspotX"
            }
            require(hotspotY in 0 until height) {
                "hotspotY must be in 0..${height - 1}: $hotspotY"
            }
            val copy = pixels.copyOf()
            return CursorImage(width, height, hotspotX, hotspotY, copy, argbToRgba(copy))
        }

        private fun validateImage(
            width: Int,
            height: Int,
            pixels: IntArray,
        ) {
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
        }

        /**
         * Decodes an image file with stb_image and copies its pixels.
         *
         * Supported formats are those compiled into LWJGL's stb_image,
         * including PNG, JPEG, BMP, TGA, and GIF. The hotspot is in decoded
         * image pixels.
         *
         * @throws IllegalArgumentException if the hotspot or decoded dimensions
         *   are invalid.
         * @throws IOException if the file cannot be read or decoded.
         * @since 2.0.0
         */
        @JvmStatic
        @Throws(IOException::class)
        public fun fromPath(
            path: Path,
            hotspotX: Int,
            hotspotY: Int,
        ): CursorImage = fromLoaded(ImageLoader.load(path), hotspotX, hotspotY)

        internal fun fromLoaded(
            loaded: LoadedImage,
            hotspotX: Int,
            hotspotY: Int,
        ): CursorImage {
            validateImage(loaded.width, loaded.height, loaded.argb)
            require(loaded.rgba.size == loaded.argb.size * 4) {
                "decoded RGBA byte count does not match ${loaded.width}x${loaded.height}"
            }
            require(hotspotX in 0 until loaded.width) {
                "hotspotX must be in 0..${loaded.width - 1}: $hotspotX"
            }
            require(hotspotY in 0 until loaded.height) {
                "hotspotY must be in 0..${loaded.height - 1}: $hotspotY"
            }
            return CursorImage(
                loaded.width,
                loaded.height,
                hotspotX,
                hotspotY,
                loaded.argb.copyOf(),
                loaded.rgba.copyOf(),
            )
        }
    }
}
