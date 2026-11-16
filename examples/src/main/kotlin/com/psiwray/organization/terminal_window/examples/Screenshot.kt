package com.psiwray.organization.terminal_window.examples

import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.IntBuffer
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Screenshot helpers used by the demos with `--screenshot=`.
 *
 * GL readback captures the window content after a swap; it does not depend on
 * a compositor or desktop screenshot tool. Image encoding uses headless AWT.
 */
internal fun captureFrontBuffer(path: Path) {
    val viewport = BufferUtils.createIntBuffer(4)
    GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
    var width = viewport.get(2)
    var height = viewport.get(3)
    if (width <= 0 || height <= 0) {
        val requestedWidth: IntBuffer = IntBuffer.allocate(1)
        val requestedHeight: IntBuffer = IntBuffer.allocate(1)
        GLFW.glfwGetFramebufferSize(
            GLFW.glfwGetCurrentContext(),
            requestedWidth,
            requestedHeight,
        )
        width = requestedWidth.get(0)
        height = requestedHeight.get(0)
    }
    require(width > 0 && height > 0) { "framebuffer is empty: ${width}x$height" }

    val pixels = BufferUtils.createByteBuffer(width * height * 4)
    GL11.glFinish()
    GL11.glReadBuffer(GL11.GL_BACK)
    GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
    GL11.glReadPixels(
        0,
        0,
        width,
        height,
        GL11.GL_RGBA,
        GL11.GL_UNSIGNED_BYTE,
        pixels,
    )

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val source = ((height - 1 - y) * width + x) * 4
            val red = pixels.get(source).toInt() and 0xFF
            val green = pixels.get(source + 1).toInt() and 0xFF
            val blue = pixels.get(source + 2).toInt() and 0xFF
            val alpha = pixels.get(source + 3).toInt() and 0xFF
            image.setRGB(x, y, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
            x++
        }
        y++
    }
    writeImage(path, image)
}

/** Composes per-window captures side by side for the TwoThreads screenshot. */
internal fun composeScreenshots(
    output: Path,
    parts: List<Path>,
) {
    require(parts.isNotEmpty()) { "at least one screenshot part is required" }
    val images = parts.map { part -> requireNotNull(ImageIO.read(part.toFile())) }
    val gap = 12
    val margin = 12
    val width = images.sumOf { it.width } + gap * (images.size - 1) + margin * 2
    val height = images.maxOf { it.height } + margin * 2
    val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = canvas.createGraphics()
    try {
        graphics.color = Color(24, 24, 24)
        graphics.fillRect(0, 0, width, height)
        var x = margin
        for (image in images) {
            graphics.drawImage(image, x, margin, null)
            x += image.width + gap
        }
    } finally {
        graphics.dispose()
    }
    writeImage(output, canvas)
    parts.forEach { Files.deleteIfExists(it) }
}

private fun writeImage(
    path: Path,
    image: BufferedImage,
) {
    val absolute = path.toAbsolutePath()
    absolute.parent?.let { Files.createDirectories(it) }
    check(ImageIO.write(image, "png", absolute.toFile())) { "PNG writer unavailable" }
}

/** Returns the optional `--screenshot=path` target. */
internal fun screenshotPath(args: Array<String>): Path? {
    val raw = args.firstOrNull { it.startsWith("--screenshot=") } ?: return null
    val value = raw.substringAfter('=')
    require(value.isNotBlank()) { "--screenshot requires a path" }
    return Path.of(value)
}
