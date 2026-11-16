package com.psiwray.organization.terminal_window

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CursorImageTest {
    @Test
    fun `of copies ARGB pixels and converts them to RGBA once`() {
        val source = intArrayOf(0x80112233.toInt())
        val image = CursorImage.of(1, 1, 0, 0, source)

        source[0] = 0
        assertContentEquals(intArrayOf(0x80112233.toInt()), image.copyPixels())
        assertContentEquals(byteArrayOf(0x11, 0x22, 0x33, 0x80.toByte()), image.rgba)
    }

    @Test
    fun `of validates dimensions and hotspot`() {
        assertFailsWith<IllegalArgumentException> { CursorImage.of(0, 1, 0, 0, IntArray(0)) }
        assertFailsWith<IllegalArgumentException> { CursorImage.of(2, 2, 0, 0, IntArray(3)) }
        assertFailsWith<IllegalArgumentException> { CursorImage.of(2, 2, 2, 0, IntArray(4)) }
        assertFailsWith<IllegalArgumentException> { CursorImage.of(2, 2, 0, 2, IntArray(4)) }
    }

    @Test
    fun `fromPath decodes PNG pixels in top-left ARGB order`() {
        val image = CursorImage.fromPath(fixture(), 1, 1)

        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals(1, image.hotspotX)
        assertEquals(1, image.hotspotY)
        assertContentEquals(
            intArrayOf(
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
                0x80FFFFFF.toInt(),
            ),
            image.copyPixels(),
        )
    }

    @Test
    fun `fromPath reports an unreadable image`() {
        assertFailsWith<IOException> {
            WindowIcon.fromPath(Path.of("missing-cursor-image.png"))
        }
    }

    @Test
    fun `window icon copies pixels and validates dimensions`() {
        val source = intArrayOf(0xFF102030.toInt())
        val icon = WindowIcon.of(1, 1, source)
        source[0] = 0

        assertContentEquals(intArrayOf(0xFF102030.toInt()), icon.copyPixels())
        assertContentEquals(byteArrayOf(0x10, 0x20, 0x30, 0xFF.toByte()), icon.rgba)
        assertFailsWith<IllegalArgumentException> { WindowIcon.of(0, 1, IntArray(0)) }
        assertFailsWith<IllegalArgumentException> { WindowIcon.of(2, 2, IntArray(3)) }
    }

    @Test
    fun `window icon decodes PNG pixels`() {
        val icon = WindowIcon.fromPath(fixture())

        assertEquals(2, icon.width)
        assertEquals(2, icon.height)
        assertContentEquals(
            intArrayOf(
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
                0x80FFFFFF.toInt(),
            ),
            icon.copyPixels(),
        )
    }

    private fun fixture(): Path {
        val candidates =
            listOf(
                Path.of("src/test/resources/images/rgba-2x2.png"),
                Path.of("library/src/test/resources/images/rgba-2x2.png"),
            )
        return candidates.firstOrNull { Files.isRegularFile(it) }
            ?: error("missing image fixture; tried ${candidates.joinToString()}")
    }
}
