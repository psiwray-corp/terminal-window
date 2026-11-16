package com.psiwray.organization.terminal_window.font

import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path

/**
 * Immutable parsed `.twf` font.
 *
 * The glyph map is read-only and every glyph mask has exactly
 * `cellWidth * cellHeight` entries. Mask values are `0` for background and `1`
 * for foreground.
 *
 * @since 2.0.0
 */
public class BitmapFont internal constructor(
    /** Width of every glyph cell in pixels. @since 2.0.0 */
    public val cellWidth: Int,
    /** Height of every glyph cell in pixels. @since 2.0.0 */
    public val cellHeight: Int,
    /** Code point used when a requested code point is absent, or `null` for blank cells. @since 2.0.0 */
    public val fallbackCodePoint: Int?,
    internal val glyphs: Map<Int, Glyph>,
) {
    /** Number of glyphs in the font. @since 2.0.0 */
    public val glyphCount: Int get() = glyphs.size

    /** Code points defined by this font. @since 2.0.0 */
    public val codePoints: Set<Int> get() = glyphs.keys

    /** Returns whether this font defines [codePoint]. @since 2.0.0 */
    public fun hasGlyph(codePoint: Int): Boolean = glyphs.containsKey(codePoint)

    /** Parsers for `.twf` files, byte streams, and character readers. @since 2.0.0 */
    public companion object {
        /**
         * Parses [path], closing the opened stream before returning.
         *
         * @throws IOException if the path cannot be opened or read.
         * @throws FontFormatException if the bytes are not a valid `.twf` font.
         * @since 2.0.0
         */
        @JvmStatic
        @Throws(IOException::class)
        public fun parse(path: Path): BitmapFont = BitmapFontParser.parse(path)

        /**
         * Parses UTF-8 bytes from [input] without closing the caller's stream.
         *
         * @throws IOException if the stream cannot be read.
         * @throws FontFormatException if the bytes are not a valid `.twf` font.
         * @since 2.0.0
         */
        @JvmStatic
        @Throws(IOException::class)
        public fun parse(input: InputStream): BitmapFont = BitmapFontParser.parse(input)

        /**
         * Parses characters from [reader] without closing the caller's reader.
         *
         * @throws IOException if the reader cannot be read.
         * @throws FontFormatException if the characters are not a valid `.twf` font.
         * @since 2.0.0
         */
        @JvmStatic
        @Throws(IOException::class)
        public fun parse(reader: Reader): BitmapFont = BitmapFontParser.parse(reader)
    }
}
