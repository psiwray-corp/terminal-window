package com.psiwray.organization.terminal_window.font

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class AtlasBakerTest {
    @Test
    fun `bakes the minimal fixture with exact bytes`() {
        val font = BitmapFont.parse(fixturePath("valid/minimal.twf"))
        val atlas = AtlasBaker.bake(font, 4096)

        assertEquals(1, atlas.layout.columns)
        assertEquals(1, atlas.layout.rows)
        assertEquals(8, atlas.layout.pixelWidth)
        assertEquals(16, atlas.layout.pixelHeight)
        assertEquals(mapOf(0x41 to 0), atlas.layout.slots)
        assertContentEquals(raster(8, 16, minimalRows()), atlas.pixels)
        assertEquals(8 * 16, atlas.pixels.size)
    }

    @Test
    fun `bakes a 4x6 glyph in row-major top-down order`() {
        val rows =
            listOf(
                "....",
                "##..",
                ".##.",
                "....",
                "#.#.",
                "....",
            )
        val font = font(4, 6, 0x41 to mask(4, 6, rows))
        val atlas = AtlasBaker.bake(font, 4096)

        assertEquals(4, atlas.layout.pixelWidth)
        assertEquals(6, atlas.layout.pixelHeight)
        assertContentEquals(raster(4, 6, rows), atlas.pixels)
    }

    @Test
    fun `keeps an asymmetric glyph top row first`() {
        val rows = listOf("#.", "##", ".#")
        val font = font(2, 3, 0x42 to mask(2, 3, rows))
        val atlas = AtlasBaker.bake(font, 4096)

        assertContentEquals(
            byteArrayOf(
                0xFF.toByte(),
                0,
                0xFF.toByte(),
                0xFF.toByte(),
                0,
                0xFF.toByte(),
            ),
            atlas.pixels,
        )
    }

    @Test
    fun `assigns slots in ascending code point order`() {
        val font =
            font(
                1,
                1,
                0x43 to mask(1, 1, listOf("#")),
                0x41 to mask(1, 1, listOf(".")),
                0x42 to mask(1, 1, listOf("#")),
            )
        val atlas = AtlasBaker.bake(font, 4096)

        assertEquals(
            listOf(0x41, 0x42, 0x43),
            atlas.layout.slots.keys
                .toList(),
        )
        assertEquals(mapOf(0x41 to 0, 0x42 to 1, 0x43 to 2), atlas.layout.slots)
        assertContentEquals(byteArrayOf(0, 0xFF.toByte(), 0xFF.toByte()), atlas.pixels)
    }

    @Test
    fun `uses no gutter and leaves unused slots zero`() {
        val font =
            font(
                2,
                2,
                0x41 to mask(2, 2, listOf("##", "##")),
                0x42 to mask(2, 2, listOf("##", "##")),
                0x43 to mask(2, 2, listOf("#.", "..")),
            )
        val atlas = AtlasBaker.bake(font, 4)

        assertEquals(2, atlas.layout.columns)
        assertEquals(2, atlas.layout.rows)
        assertEquals(4, atlas.layout.pixelWidth)
        assertEquals(4, atlas.layout.pixelHeight)
        assertContentEquals(
            byteArrayOf(
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            ),
            atlas.pixels,
        )
    }

    @Test
    fun `chooses deterministic grids for one two ninety five and 65535 glyphs`() {
        val expected = listOf(1 to 1, 2 to 1, 95 to 1, 65535 to 17)
        for ((count, expectedColumns) in expected) {
            val atlas = AtlasBaker.bake(fontN(count, 1, 1), 4096)
            assertEquals(expectedColumns, atlas.layout.columns, "count=$count")
            assertEquals((count + expectedColumns - 1) / expectedColumns, atlas.layout.rows)
            assertEquals(count, atlas.layout.slots.size)
            assertEquals(atlas.layout.pixelWidth * atlas.layout.pixelHeight, atlas.pixels.size)
        }
    }

    @Test
    fun `minimizes area for one through 512 glyphs`() {
        for (count in 1..512) {
            val atlas = AtlasBaker.bake(fontN(count, 8, 16), 4096)
            val expected = expectedGrid(count, 8, 16, 4096)
            assertEquals(expected.columns, atlas.layout.columns, "count=$count")
            assertEquals(expected.rows, atlas.layout.rows, "count=$count")
            assertEquals(expected.columns * 8, atlas.layout.pixelWidth, "count=$count")
            assertEquals(expected.rows * 16, atlas.layout.pixelHeight, "count=$count")
            assertEquals(
                atlas.layout.pixelWidth.toLong() * atlas.layout.pixelHeight.toLong(),
                expected.area,
                "count=$count",
            )
            assertEquals(atlas.layout.pixelWidth * atlas.layout.pixelHeight, atlas.pixels.size, "count=$count")
            assertEquals(count * 8 * 16, atlas.pixels.size, "count=$count")
        }
    }

    @Test
    fun `shipped ASCII font atlas is glyphs times cell area`() {
        val font = BitmapFont.parse(fixturePath("valid/ascii-8x16.twf"))
        val atlas = AtlasBaker.bake(font, 4096)

        assertEquals(95, font.glyphCount)
        assertEquals(12_160, font.glyphCount * font.cellWidth * font.cellHeight)
        assertEquals(12_160, atlas.pixels.size)
    }

    @Test
    fun `throws when no grid fits the texture limit`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                AtlasBaker.bake(fontN(95, 8, 16), 64)
            }
        assertTrue(exception.message?.contains("8x1520") == true, exception.message)
        assertTrue(exception.message?.contains("maxTextureSize=64") == true, exception.message)
    }

    @Test
    fun `rejects a non-positive texture limit`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                AtlasBaker.bake(font(1, 1, 0x41 to mask(1, 1, listOf("#"))), 0)
            }
        assertTrue(exception.message?.contains("maxTextureSize must be positive") == true, exception.message)
    }

    @Test
    fun `bakes an empty font to an empty atlas`() {
        val atlas = AtlasBaker.bake(BitmapFont(1, 1, null, emptyMap()), 4096)

        assertEquals(0, atlas.layout.columns)
        assertEquals(0, atlas.layout.rows)
        assertEquals(0, atlas.layout.pixelWidth)
        assertEquals(0, atlas.layout.pixelHeight)
        assertTrue(atlas.layout.slots.isEmpty())
        assertEquals(0, atlas.pixels.size)
    }

    @Test
    fun `is deterministic across repeated bakes`() {
        val font = fontN(95, 8, 16)
        val first = AtlasBaker.bake(font, 4096)
        val second = AtlasBaker.bake(font, 4096)

        assertEquals(first.layout, second.layout)
        assertContentEquals(first.pixels, second.pixels)
    }

    private data class ExpectedGrid(
        val columns: Int,
        val rows: Int,
        val area: Long,
    )

    private fun expectedGrid(
        count: Int,
        cellWidth: Int,
        cellHeight: Int,
        maxTextureSize: Int,
    ): ExpectedGrid {
        var bestColumns = 0
        var bestRows = 0
        var bestWidth = 0
        var bestArea = Long.MAX_VALUE
        for (columns in 1..count) {
            val rows = (count + columns - 1) / columns
            val width = columns * cellWidth
            val height = rows * cellHeight
            if (width > maxTextureSize || height > maxTextureSize) {
                continue
            }
            val area = width.toLong() * height.toLong()
            if (area < bestArea || (area == bestArea && width < bestWidth)) {
                bestColumns = columns
                bestRows = rows
                bestWidth = width
                bestArea = area
            }
        }
        require(bestColumns > 0) { "no grid fits" }
        return ExpectedGrid(bestColumns, bestRows, bestArea)
    }

    private fun font(
        cellWidth: Int,
        cellHeight: Int,
        vararg glyphs: Pair<Int, ByteArray>,
    ): BitmapFont {
        val map = LinkedHashMap<Int, Glyph>(glyphs.size)
        for ((codePoint, mask) in glyphs) {
            map[codePoint] = Glyph(codePoint, mask)
        }
        return BitmapFont(cellWidth, cellHeight, null, map)
    }

    private fun fontN(
        count: Int,
        cellWidth: Int,
        cellHeight: Int,
    ): BitmapFont {
        val map = LinkedHashMap<Int, Glyph>(count)
        var codePoint = 0
        while (codePoint < count) {
            map[codePoint] = Glyph(codePoint, ByteArray(cellWidth * cellHeight))
            codePoint++
        }
        return BitmapFont(cellWidth, cellHeight, null, map)
    }

    private fun mask(
        width: Int,
        height: Int,
        rows: List<String>,
    ): ByteArray {
        val result = ByteArray(width * height)
        for (y in 0 until height) {
            val row = rows[y]
            require(row.length == width)
            for (x in 0 until width) {
                if (row[x] == '#') {
                    result[(y * width) + x] = 1
                }
            }
        }
        return result
    }

    private fun raster(
        width: Int,
        height: Int,
        rows: List<String>,
    ): ByteArray {
        val result = ByteArray(width * height)
        for (y in 0 until height) {
            val row = rows[y]
            require(row.length == width)
            for (x in 0 until width) {
                if (row[x] == '#') {
                    result[(y * width) + x] = 0xFF.toByte()
                }
            }
        }
        return result
    }

    private fun minimalRows(): List<String> =
        listOf(
            "....##..",
            "...####.",
            "..#..#..",
            ".#....#.",
            ".######.",
            ".#....#.",
            ".#....#.",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
        )

    private fun fixturePath(name: String): Path {
        val resource = requireNotNull(javaClass.getResource("/fonts/$name")) { "missing /fonts/$name" }
        return Path.of(resource.toURI())
    }
}
