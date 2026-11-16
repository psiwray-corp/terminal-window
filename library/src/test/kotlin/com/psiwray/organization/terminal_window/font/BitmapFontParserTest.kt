package com.psiwray.organization.terminal_window.font

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class BitmapFontParserTest {
    private data class InvalidFixture(
        val name: String,
        val code: String,
        val line: Int,
        val column: Int,
    )

    private val invalidFixtures =
        listOf(
            InvalidFixture("unknown-directive.twf", "TW-001", 6, 1),
            InvalidFixture("missing-version.twf", "TW-002", 1, 1),
            InvalidFixture("duplicate-version.twf", "TW-002", 2, 1),
            InvalidFixture("unsupported-version.twf", "TW-002", 1, 9),
            InvalidFixture("missing-width.twf", "TW-003", 2, 1),
            InvalidFixture("duplicate-width.twf", "TW-003", 3, 1),
            InvalidFixture("width-out-of-range.twf", "TW-003", 2, 12),
            InvalidFixture("missing-height.twf", "TW-003", 3, 1),
            InvalidFixture("duplicate-height.twf", "TW-003", 4, 1),
            InvalidFixture("height-out-of-range.twf", "TW-003", 3, 13),
            InvalidFixture("pixel-row-short.twf", "TW-004", 7, 8),
            InvalidFixture("pixel-row-long.twf", "TW-004", 7, 9),
            InvalidFixture("pixel-row-illegal.twf", "TW-005", 7, 8),
            InvalidFixture("glyph-too-few-rows.twf", "TW-006", 8, 1),
            InvalidFixture("glyph-too-many-rows.twf", "TW-006", 23, 1),
            InvalidFixture("code-point-below.twf", "TW-007", 6, 7),
            InvalidFixture("code-point-surrogate.twf", "TW-007", 6, 7),
            InvalidFixture("code-point-above.twf", "TW-007", 6, 7),
            InvalidFixture("duplicate-glyph.twf", "TW-008", 23, 7),
            InvalidFixture("duplicate-fallback.twf", "TW-009", 6, 1),
            InvalidFixture("undefined-fallback.twf", "TW-009", 5, 10),
            InvalidFixture("eof-inside-glyph.twf", "TW-011", 8, 1),
        )

    @Test
    fun `parses minimal fixture with exact mask`() {
        val font = parseFixture("valid/minimal.twf")

        assertEquals(8, font.cellWidth)
        assertEquals(16, font.cellHeight)
        assertNull(font.fallbackCodePoint)
        assertEquals(1, font.glyphCount)
        assertEquals(setOf(0x41), font.codePoints)
        assertTrue(font.hasGlyph(0x41))

        val glyph = assertNotNull(font.glyphs[0x41])
        assertEquals(0x41, glyph.codePoint)
        assertEquals(8 * 16, glyph.mask.size)
        assertEquals(0, glyph.mask[0].toInt())
        assertEquals(1, glyph.mask[4].toInt())
        assertEquals(1, glyph.mask[5].toInt())
        assertEquals(1, glyph.mask[(1 * 8) + 3].toInt())
        assertEquals(0, glyph.mask[(15 * 8) + 7].toInt())
    }

    @Test
    fun `parses full ASCII fixture`() {
        val font = parseFixture("valid/ascii-8x16.twf")

        assertEquals(8, font.cellWidth)
        assertEquals(16, font.cellHeight)
        assertEquals(0x3F, font.fallbackCodePoint)
        assertEquals(95, font.glyphCount)
        for (codePoint in 0x20..0x7E) {
            assertTrue(font.hasGlyph(codePoint), "missing U+${codePoint.toString(16)}")
        }
        assertTrue(font.codePoints.none { it < 0x20 || it > 0x7E })
        for (glyph in font.glyphs.values) {
            assertEquals(8 * 16, glyph.mask.size)
        }
    }

    @Test
    fun `parses through path input stream and reader`() {
        val path = fixturePath("valid/minimal.twf")
        val fromPath = BitmapFont.parse(path)
        assertEquals(1, fromPath.glyphCount)

        val fromStream =
            fixtureStream("valid/minimal.twf").use { stream ->
                BitmapFont.parse(stream)
            }
        assertEquals(1, fromStream.glyphCount)

        val fromReader =
            Files.newBufferedReader(path, Charsets.UTF_8).use { reader ->
                BitmapFont.parse(reader)
            }
        assertEquals(1, fromReader.glyphCount)
    }

    @Test
    fun `accepts comments blank lines BOM CRLF and trailing comments`() {
        val text =
            buildString {
                append("\uFEFF// leading comment\n\n")
                append("version 1 // version\n")
                append("cell-width 8\t// width\n\n")
                append("cell-height 16 // height\n\n")
                append("fallback U+0041 // fallback\n\n")
                append("glyph U+0041 // glyph\n")
                minimalRows().forEachIndexed { index, row ->
                    if (index == 3 || index == 10) {
                        append("// interleaved comment\n")
                    }
                    if (index == 5) {
                        append('\n')
                    }
                    append(row).append('\n')
                }
                append("// trailing comment\n")
            }

        val fromReader = BitmapFont.parse(StringReader(text))
        assertEquals(1, fromReader.glyphCount)
        assertEquals(0x41, fromReader.fallbackCodePoint)

        val crlf = text.replace("\n", "\r\n")
        val fromStream = BitmapFont.parse(ByteArrayInputStream(crlf.toByteArray(Charsets.UTF_8)))
        assertEquals(1, fromStream.glyphCount)
        assertEquals(0x41, fromStream.fallbackCodePoint)
    }

    @Test
    fun `accepts a zero glyph font without fallback`() {
        val font = parseText("version 1\ncell-width 1\ncell-height 1\n")

        assertEquals(0, font.glyphCount)
        assertEquals(emptySet(), font.codePoints)
        assertNull(font.fallbackCodePoint)
    }

    @Test
    fun `accepts a forward fallback reference`() {
        val text = "version 1\ncell-width 1\ncell-height 1\nfallback U+0041\nglyph U+0041\n#\n"
        val font = parseText(text)

        assertEquals(0x41, font.fallbackCodePoint)
        assertEquals(1, font.glyphCount)
    }

    @Test
    fun `rejects every invalid fixture with expected code line and column`() {
        for (fixture in invalidFixtures) {
            val path = fixturePath("invalid/${fixture.name}")
            val exception =
                assertFailsWith<FontFormatException>("fixture ${fixture.name}") {
                    BitmapFont.parse(path)
                }
            assertEquals(fixture.code, exception.code, "fixture ${fixture.name}")
            assertEquals(fixture.line, exception.line, "fixture ${fixture.name}")
            assertEquals(fixture.column, exception.column, "fixture ${fixture.name}")
            assertEquals(path.toString(), exception.path, "fixture ${fixture.name}")
            assertTrue(
                exception.message?.startsWith(
                    "$path:${fixture.line}:${fixture.column}: ${fixture.code}: ",
                ) == true,
                "fixture ${fixture.name}: ${exception.message}",
            )
        }
    }

    @Test
    fun `reports header state violations`() {
        assertError("cell-width 8\n", "TW-001", 1, 1)
        assertError("version 1\ncell-height 16\n", "TW-001", 2, 1)
        assertError("version 1\ncell-width 8\nversion 1\n", "TW-002", 3, 1)
        assertError("version 1\ncell-width 8\ncell-height 16\nversion 1\n", "TW-002", 4, 1)
        assertError("version 1\ncell-width 8\ncell-width 8\n", "TW-003", 3, 1)
        assertError("version 1\ncell-width 8\ncell-height 16\ncell-height 16\n", "TW-003", 4, 1)
        assertError("version 1\ncell-width 8\ncell-height 16\nbogus\n", "TW-001", 4, 1)
        assertError("version\n", "TW-001", 1, 1)
        assertError("version 1\ncell-width\n", "TW-001", 2, 1)
        assertError("version 1\ncell-width 8\ncell-height\n", "TW-001", 3, 1)
        assertError("version 1\ncell-width 8\ncell-height 16\nfallback\n", "TW-001", 4, 1)
        assertError("version 1\ncell-width 8\ncell-height 16\nglyph\n", "TW-001", 4, 1)
        assertError("version 1 2\n", "TW-001", 1, 11)
        assertError("version 1\ncell-width 8 9\n", "TW-001", 2, 14)
        assertError("version 1.0\n", "TW-001", 1, 9)
        assertError("version 1\ncell-width 1.0\n", "TW-001", 2, 12)
    }

    @Test
    fun `reports glyph and row violations`() {
        val header = "version 1\ncell-width 8\ncell-height 16\n"

        assertError(header + "glyph U+ZZZZ\n", "TW-001", 4, 9)
        assertError(header + "glyph U+041\n", "TW-001", 4, 7)
        assertError(header + "glyph U+0041x\n", "TW-001", 4, 13)
        assertError(header + "glyph U+0000\n", "TW-007", 4, 7)
        assertError(header + "glyph U+0041\n........\nfallback U+0041\n", "TW-006", 6, 1)
        assertError(header + "glyph U+0041\n#\n", "TW-004", 5, 2)
        assertError(header + "glyph U+0041\n#x......\n", "TW-005", 5, 2)
    }

    @Test
    fun `rejects fallback after glyph and extra rows`() {
        val withGlyph = "version 1\ncell-width 8\ncell-height 16\n" + glyph("U+0041")
        assertError(withGlyph + "fallback U+0041\n", "TW-001", 21, 1)
        assertError(withGlyph + "bogus\n", "TW-001", 21, 1)
        assertError(withGlyph + "........\n", "TW-006", 21, 1)
        assertError(
            "version 1\ncell-width 8\ncell-height 16\n" + glyph("U+0041") + glyph("U+00041"),
            "TW-008",
            21,
            7,
        )
    }

    @Test
    fun `reports malformed UTF-8 and malformed reader scalars`() {
        val prefix = "version 1\ncell-width 8\ncell-height 16\nglyph U+0041\n"
        val malformed = prefix.toByteArray(Charsets.UTF_8) + byteArrayOf(0xFF.toByte())
        val fromStream =
            assertFailsWith<FontFormatException> {
                BitmapFont.parse(ByteArrayInputStream(malformed))
            }
        assertEquals("TW-001", fromStream.code)
        assertEquals(5, fromStream.line)
        assertEquals(1, fromStream.column)

        val malformedRow = (prefix + "........").toByteArray(Charsets.UTF_8) + byteArrayOf(0xFF.toByte())
        val fromRow =
            assertFailsWith<FontFormatException> {
                BitmapFont.parse(ByteArrayInputStream(malformedRow))
            }
        assertEquals("TW-001", fromRow.code)
        assertEquals(5, fromRow.line)
        assertEquals(9, fromRow.column)

        val loneSurrogate = assertError(prefix + "\uD800", "TW-001", 5, 1)
        assertEquals("<input>", loneSurrogate.path)
    }

    @Test
    fun `reports lexical line and BOM failures`() {
        assertError("version 1\r", "TW-001", 1, 10)
        assertError("version 1\n\uFEFFcell-width 8\n", "TW-001", 2, 1)
        assertError("version 1\ncell-width 8\ncell-height 16\n\u0000", "TW-001", 4, 1)
        assertError("//" + "a".repeat(1023), "TW-001", 1, 1025)
    }

    @Test
    fun `parses maximum dimensions`() {
        val row = ".".repeat(256)
        val text =
            "version 1\ncell-width 256\ncell-height 256\nglyph U+0041\n" +
                List(256) { row }.joinToString("\n") + "\n"
        val font = parseText(text)

        assertEquals(256, font.cellWidth)
        assertEquals(256, font.cellHeight)
        assertEquals(65536, assertNotNull(font.glyphs[0x41]).mask.size)
    }

    @Test
    fun `enforces the glyph limit boundary`() {
        val atLimit = generatedFont(65535)
        val font = parseText(atLimit)
        assertEquals(65535, font.glyphCount)

        val overLimit = atLimit + generatedGlyph(65535)
        assertError(overLimit, "TW-010", 131074, 1)
    }

    @Test
    fun `path errors propagate as IOException`() {
        assertFailsWith<IOException> {
            BitmapFont.parse(Path.of("definitely-missing-terminal-window-font.twf"))
        }
    }

    @Test
    fun `java callers get a null check exception for a null path`() {
        val method = BitmapFont::class.java.getMethod("parse", Path::class.java)
        val exception =
            assertFailsWith<InvocationTargetException> {
                method.invoke(null, null)
            }
        assertTrue(exception.targetException is NullPointerException)
    }

    private fun parseFixture(name: String): BitmapFont = BitmapFont.parse(fixturePath(name))

    private fun parseText(text: String): BitmapFont = BitmapFont.parse(StringReader(text))

    private fun assertError(
        text: String,
        code: String,
        line: Int,
        column: Int,
    ): FontFormatException {
        val exception =
            assertFailsWith<FontFormatException> {
                parseText(text)
            }
        assertEquals(code, exception.code, exception.message)
        assertEquals(line, exception.line, exception.message)
        assertEquals(column, exception.column, exception.message)
        return exception
    }

    private fun fixturePath(name: String): Path {
        val resource = requireNotNull(javaClass.getResource("/fonts/$name")) { "missing /fonts/$name" }
        return Path.of(resource.toURI())
    }

    private fun fixtureStream(name: String) =
        requireNotNull(javaClass.getResourceAsStream("/fonts/$name")) { "missing /fonts/$name" }

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

    private fun glyph(codePoint: String): String {
        val builder = StringBuilder("glyph ").append(codePoint).append('\n')
        for (row in minimalRows()) {
            builder.append(row).append('\n')
        }
        return builder.toString()
    }

    private fun generatedGlyph(index: Int): String {
        val codePoint = (0x10000 + index).toString(16).uppercase().padStart(6, '0')
        return "glyph U+$codePoint\n.\n"
    }

    private fun generatedFont(count: Int): String {
        val builder = StringBuilder("version 1\ncell-width 1\ncell-height 1\n")
        for (index in 0 until count) {
            builder.append(generatedGlyph(index))
        }
        return builder.toString()
    }
}
