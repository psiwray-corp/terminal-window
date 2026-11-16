package com.psiwray.organization.terminal_window.internal

import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import java.io.IOException
import java.nio.file.Path

/** Decoded image pixels in public ARGB layout. */
internal data class LoadedImage(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val rgba: ByteArray,
)

/** Decodes image files with LWJGL's stb_image. */
internal object ImageLoader {
    /**
     * Loads [path] as RGBA8 and converts it once to ARGB.
     *
     * stb_image uses a top-left origin, which is the public image contract.
     *
     * @throws IOException if the file cannot be read or decoded.
     */
    @Throws(IOException::class)
    internal fun load(path: Path): LoadedImage {
        val stack = MemoryStack.stackPush()
        try {
            val width = stack.mallocInt(1)
            val height = stack.mallocInt(1)
            val channels = stack.mallocInt(1)
            val pixels =
                STBImage.stbi_load(path.toString(), width, height, channels, RGBA_CHANNELS)
                    ?: throw IOException(
                        "could not decode image '$path': ${STBImage.stbi_failure_reason()}",
                    )
            try {
                val imageWidth = width[0]
                val imageHeight = height[0]
                val pixelCount = imageWidth.toLong() * imageHeight.toLong()
                if (imageWidth <= 0 ||
                    imageHeight <= 0 ||
                    pixelCount > Int.MAX_VALUE.toLong() ||
                    pixelCount > (Int.MAX_VALUE / 4).toLong()
                ) {
                    throw IOException(
                        "image '$path' has unsupported dimensions ${imageWidth}x$imageHeight",
                    )
                }
                val argb = IntArray(pixelCount.toInt())
                val rgba = ByteArray(pixelCount.toInt() * RGBA_CHANNELS)
                var index = 0
                var offset = 0
                while (index < argb.size) {
                    val red = pixels.get(offset).toInt() and 0xFF
                    val green = pixels.get(offset + 1).toInt() and 0xFF
                    val blue = pixels.get(offset + 2).toInt() and 0xFF
                    val alpha = pixels.get(offset + 3).toInt() and 0xFF
                    argb[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    rgba[offset] = red.toByte()
                    rgba[offset + 1] = green.toByte()
                    rgba[offset + 2] = blue.toByte()
                    rgba[offset + 3] = alpha.toByte()
                    index++
                    offset += RGBA_CHANNELS
                }
                return LoadedImage(imageWidth, imageHeight, argb, rgba)
            } finally {
                STBImage.stbi_image_free(pixels)
            }
        } finally {
            stack.pop()
        }
    }

    private const val RGBA_CHANNELS: Int = 4
}

/** Converts public ARGB pixels to GLFW's non-premultiplied RGBA8 byte layout. */
internal fun argbToRgba(pixels: IntArray): ByteArray {
    val rgba = ByteArray(pixels.size * 4)
    var index = 0
    var offset = 0
    while (index < pixels.size) {
        val value = pixels[index]
        rgba[offset] = (value ushr 16).toByte()
        rgba[offset + 1] = (value ushr 8).toByte()
        rgba[offset + 2] = value.toByte()
        rgba[offset + 3] = (value ushr 24).toByte()
        index++
        offset += 4
    }
    return rgba
}
