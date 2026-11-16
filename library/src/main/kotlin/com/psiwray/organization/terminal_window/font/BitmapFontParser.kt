package com.psiwray.organization.terminal_window.font

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path

internal object BitmapFontParser {
    private const val MAX_LINE_SCALARS: Int = 1024
    private const val MAX_DIMENSION: Int = 256
    private const val MAX_GLYPHS: Int = 65535
    private const val NO_PATH: String = "<input>"

    internal fun parse(path: Path): BitmapFont = Files.newInputStream(path).use { parse(it, path.toString()) }

    internal fun parse(input: InputStream): BitmapFont = parse(input, NO_PATH)

    internal fun parse(reader: Reader): BitmapFont =
        Parser(NO_PATH, LineReader(NO_PATH, BufferedReader(reader))).parse()

    private fun parse(
        input: InputStream,
        path: String,
    ): BitmapFont {
        val decoder =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val stream = OneByteReadInputStream(BufferedInputStream(input))
        return Parser(path, LineReader(path, InputStreamReader(stream, decoder))).parse()
    }

    /**
     * Keeps the UTF-8 decoder from consuming bytes past a logical line. Without
     * this, a malformed byte after a newline is reported on the previous line.
     */
    private class OneByteReadInputStream(
        private val delegate: InputStream,
    ) : InputStream() {
        override fun read(): Int = delegate.read()

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (length == 0) {
                return 0
            }
            return delegate.read(buffer, offset, 1)
        }
    }

    private enum class State {
        VERSION,
        WIDTH,
        HEIGHT,
        HEADER_END,
        GLYPH_ROWS,
        AFTER_GLYPH,
    }

    private data class Line(
        val number: Int,
        val text: String,
        val scalarLength: Int,
    )

    private data class Token(
        val text: String,
        val column: Int,
    )

    private class LineReader(
        private val path: String,
        private val reader: Reader,
    ) {
        private var lineNumber: Int = 1
        private var pending: Int = -1
        private var eof: Boolean = false
        private var firstCodePoint: Boolean = true

        var eofLine: Int = 1
            private set

        var eofColumn: Int = 1
            private set

        fun nextLine(): Line? {
            if (eof) {
                return null
            }

            val text = StringBuilder()
            var column = 1
            var scalarLength = 0
            var started = false

            while (true) {
                val first = readChar(column)
                if (first == -1) {
                    eof = true
                    eofLine = lineNumber
                    eofColumn = column
                    return if (started) Line(lineNumber, text.toString(), scalarLength) else null
                }

                if (firstCodePoint) {
                    firstCodePoint = false
                    if (first == 0xFEFF) {
                        continue
                    }
                }

                if (first == '\n'.code) {
                    return finishLine(lineNumber, text, scalarLength)
                }
                if (first == '\r'.code) {
                    val next = readChar(column)
                    if (next == '\n'.code) {
                        return finishLine(lineNumber, text, scalarLength)
                    }
                    if (next != -1) {
                        pending = next
                    }
                    throw FontFormatException("TW-001", path, lineNumber, column, "bare carriage return")
                }
                if (first == 0xFEFF) {
                    throw FontFormatException(
                        "TW-001",
                        path,
                        lineNumber,
                        column,
                        "byte-order mark is only allowed at the start of the file",
                    )
                }
                if (first < 0x20 && first != '\t'.code) {
                    throw FontFormatException(
                        "TW-001",
                        path,
                        lineNumber,
                        column,
                        "illegal control character U+${first.toString(16).uppercase()}",
                    )
                }

                if (Character.isHighSurrogate(first.toChar())) {
                    val low = readChar(column)
                    if (low == -1 || !Character.isLowSurrogate(low.toChar())) {
                        throw FontFormatException(
                            "TW-001",
                            path,
                            lineNumber,
                            column,
                            "unpaired high surrogate",
                        )
                    }
                    text.append(first.toChar()).append(low.toChar())
                } else if (Character.isLowSurrogate(first.toChar())) {
                    throw FontFormatException("TW-001", path, lineNumber, column, "unpaired low surrogate")
                } else {
                    text.append(first.toChar())
                }

                scalarLength++
                started = true
                if (scalarLength > MAX_LINE_SCALARS) {
                    throw FontFormatException(
                        "TW-001",
                        path,
                        lineNumber,
                        column,
                        "line exceeds $MAX_LINE_SCALARS Unicode scalars",
                    )
                }
                column++
            }
        }

        private fun finishLine(
            number: Int,
            text: StringBuilder,
            scalarLength: Int,
        ): Line {
            lineNumber++
            eofLine = lineNumber
            eofColumn = 1
            return Line(number, text.toString(), scalarLength)
        }

        private fun readChar(column: Int): Int {
            if (pending != -1) {
                val value = pending
                pending = -1
                return value
            }
            return try {
                reader.read()
            } catch (exception: CharacterCodingException) {
                throw FontFormatException("TW-001", path, lineNumber, column, "malformed UTF-8")
            }
        }
    }

    private class Parser(
        private val path: String,
        private val reader: LineReader,
    ) {
        private var state: State = State.VERSION
        private var cellWidth: Int = 0
        private var cellHeight: Int = 0
        private var fallbackCodePoint: Int? = null
        private var fallbackLine: Int = 0
        private var fallbackColumn: Int = 0
        private var currentCodePoint: Int = 0
        private var row: Int = 0
        private var mask: ByteArray = ByteArray(0)
        private val glyphs: LinkedHashMap<Int, Glyph> = LinkedHashMap()

        fun parse(): BitmapFont {
            while (true) {
                val line = reader.nextLine() ?: return finishAtEof()

                if (state == State.GLYPH_ROWS) {
                    if (isIgnoredLine(line)) {
                        continue
                    }
                    if (isDirectiveLine(line)) {
                        tw006(
                            line.number,
                            firstTokenColumn(line.text),
                            "glyph U+${currentCodePoint.toString(16).uppercase()} has only $row of $cellHeight rows",
                        )
                    }
                    readPixelRow(line)
                    continue
                }

                if (isIgnoredLine(line)) {
                    continue
                }

                val tokens = directiveTokens(line)
                val keyword = tokens[0]
                when (state) {
                    State.VERSION -> {
                        if (keyword.text != "version") {
                            tw001(line.number, keyword.column, "expected 'version', found '${keyword.text}'")
                        }
                        parseVersion(line, tokens)
                        state = State.WIDTH
                    }

                    State.WIDTH -> {
                        when (keyword.text) {
                            "cell-width" -> {
                                parseDimension(line, tokens, "cell-width")
                                state = State.HEIGHT
                            }

                            "version" -> {
                                tw002(line.number, keyword.column, "duplicate version declaration")
                            }

                            else -> {
                                tw001(line.number, keyword.column, "expected 'cell-width', found '${keyword.text}'")
                            }
                        }
                    }

                    State.HEIGHT -> {
                        when (keyword.text) {
                            "cell-height" -> {
                                parseDimension(line, tokens, "cell-height")
                                state = State.HEADER_END
                            }

                            "version" -> {
                                tw002(line.number, keyword.column, "duplicate version declaration")
                            }

                            "cell-width" -> {
                                tw003(line.number, keyword.column, "duplicate cell-width declaration")
                            }

                            else -> {
                                tw001(line.number, keyword.column, "expected 'cell-height', found '${keyword.text}'")
                            }
                        }
                    }

                    State.HEADER_END -> {
                        when (keyword.text) {
                            "fallback" -> {
                                parseFallback(line, tokens)
                            }

                            "glyph" -> {
                                startGlyph(line, tokens)
                            }

                            "version" -> {
                                tw002(line.number, keyword.column, "duplicate version declaration")
                            }

                            "cell-width" -> {
                                tw003(line.number, keyword.column, "duplicate cell-width declaration")
                            }

                            "cell-height" -> {
                                tw003(line.number, keyword.column, "duplicate cell-height declaration")
                            }

                            else -> {
                                tw001(
                                    line.number,
                                    keyword.column,
                                    "unknown or out-of-order directive '${keyword.text}'",
                                )
                            }
                        }
                    }

                    State.AFTER_GLYPH -> {
                        when (keyword.text) {
                            "glyph" -> {
                                startGlyph(line, tokens)
                            }

                            "version" -> {
                                tw002(line.number, keyword.column, "duplicate version declaration")
                            }

                            "cell-width" -> {
                                tw003(line.number, keyword.column, "duplicate cell-width declaration")
                            }

                            "cell-height" -> {
                                tw003(line.number, keyword.column, "duplicate cell-height declaration")
                            }

                            "fallback" -> {
                                tw001(
                                    line.number,
                                    keyword.column,
                                    "fallback must appear before the first glyph",
                                )
                            }

                            else -> {
                                if (line.text.isNotEmpty() && (line.text[0] == '.' || line.text[0] == '#')) {
                                    tw006(
                                        line.number,
                                        1,
                                        "extra pixel row after glyph U+${currentCodePoint.toString(
                                            16,
                                        ).uppercase()}: ${preview(line.text)}",
                                    )
                                } else {
                                    tw001(
                                        line.number,
                                        keyword.column,
                                        "unknown or out-of-order directive '${keyword.text}'",
                                    )
                                }
                            }
                        }
                    }

                    State.GLYPH_ROWS -> {
                        error("unreachable")
                    }
                }
            }
        }

        private fun finishAtEof(): BitmapFont {
            when (state) {
                State.VERSION -> {
                    tw002(reader.eofLine, reader.eofColumn, "missing version declaration")
                }

                State.WIDTH -> {
                    tw003(reader.eofLine, reader.eofColumn, "missing cell-width declaration")
                }

                State.HEIGHT -> {
                    tw003(reader.eofLine, reader.eofColumn, "missing cell-height declaration")
                }

                State.GLYPH_ROWS -> {
                    tw011(
                        reader.eofLine,
                        reader.eofColumn,
                        "unexpected end of file in glyph U+${currentCodePoint.toString(
                            16,
                        ).uppercase()}: found $row of $cellHeight rows",
                    )
                }

                State.HEADER_END,
                State.AFTER_GLYPH,
                -> {
                    val fallback = fallbackCodePoint
                    if (fallback != null && !glyphs.containsKey(fallback)) {
                        tw009(
                            fallbackLine,
                            fallbackColumn,
                            "fallback U+${fallback.toString(
                                16,
                            ).uppercase().padStart(4, '0')} does not name a defined glyph",
                        )
                    }
                    return BitmapFont(cellWidth, cellHeight, fallbackCodePoint, glyphs.toMap())
                }
            }
        }

        private fun parseVersion(
            line: Line,
            tokens: List<Token>,
        ) {
            val keyword = tokens[0]
            if (tokens.size < 2) {
                tw001(line.number, keyword.column, "missing version value")
            }
            val value = tokens[1]
            if (!isDecimal(value.text)) {
                tw001(line.number, value.column, "version must be 1")
            }
            if (value.text != "1") {
                tw002(line.number, value.column, "unsupported version '${value.text}'")
            }
            requireNoMore(line, tokens, 2)
        }

        private fun parseDimension(
            line: Line,
            tokens: List<Token>,
            name: String,
        ) {
            val keyword = tokens[0]
            if (tokens.size < 2) {
                tw001(line.number, keyword.column, "missing $name value")
            }
            val valueToken = tokens[1]
            if (!isDecimal(valueToken.text)) {
                tw001(line.number, valueToken.column, "$name must be a decimal integer")
            }
            val value = parseCappedDecimal(valueToken.text)
            if (value !in 1..MAX_DIMENSION) {
                tw003(
                    line.number,
                    valueToken.column,
                    "$name must be between 1 and $MAX_DIMENSION, was '${valueToken.text}'",
                )
            }
            requireNoMore(line, tokens, 2)
            if (name == "cell-width") {
                cellWidth = value
            } else {
                cellHeight = value
            }
        }

        private fun parseFallback(
            line: Line,
            tokens: List<Token>,
        ) {
            val keyword = tokens[0]
            if (fallbackCodePoint != null) {
                tw009(line.number, keyword.column, "duplicate fallback declaration")
            }
            if (tokens.size < 2) {
                tw001(line.number, keyword.column, "missing fallback code point")
            }
            val codePoint = parseCodePoint(line, tokens[1])
            requireNoMore(line, tokens, 2)
            fallbackCodePoint = codePoint
            fallbackLine = line.number
            fallbackColumn = tokens[1].column
        }

        private fun startGlyph(
            line: Line,
            tokens: List<Token>,
        ) {
            val keyword = tokens[0]
            if (glyphs.size >= MAX_GLYPHS) {
                tw010(line.number, keyword.column, "more than $MAX_GLYPHS glyph blocks")
            }
            if (tokens.size < 2) {
                tw001(line.number, keyword.column, "missing glyph code point")
            }
            val codePointToken = tokens[1]
            val codePoint = parseCodePoint(line, codePointToken)
            requireNoMore(line, tokens, 2)
            if (glyphs.containsKey(codePoint)) {
                tw008(line.number, codePointToken.column, "duplicate glyph U+${codePoint.toString(16).uppercase()}")
            }

            currentCodePoint = codePoint
            row = 0
            mask = ByteArray(cellWidth * cellHeight)
            state = State.GLYPH_ROWS
        }

        private fun readPixelRow(line: Line) {
            if (line.scalarLength != cellWidth) {
                val column = if (line.scalarLength < cellWidth) line.scalarLength + 1 else cellWidth + 1
                tw004(
                    line.number,
                    column,
                    "pixel row has ${line.scalarLength} characters, expected $cellWidth: ${preview(line.text)}",
                )
            }

            var column = 0
            while (column < line.text.length) {
                val character = line.text[column]
                when (character) {
                    '.' -> {
                        mask[(row * cellWidth) + column] = 0
                    }

                    '#' -> {
                        mask[(row * cellWidth) + column] = 1
                    }

                    else -> {
                        val errorColumn = line.text.codePointCount(0, column) + 1
                        tw005(
                            line.number,
                            errorColumn,
                            "illegal pixel character at column $errorColumn: ${preview(line.text)}",
                        )
                    }
                }
                column++
            }
            row++
            if (row == cellHeight) {
                glyphs[currentCodePoint] = Glyph(currentCodePoint, mask)
                state = State.AFTER_GLYPH
            }
        }

        private fun parseCodePoint(
            line: Line,
            token: Token,
        ): Int {
            val text = token.text
            if (!text.startsWith("U+")) {
                tw001(line.number, token.column, "code point must start with 'U+'")
            }
            val digits = text.substring(2)
            if (digits.length !in 4..6) {
                tw001(line.number, token.column, "code point must have 4 to 6 hexadecimal digits")
            }

            var value = 0
            for (index in digits.indices) {
                val digit = hexValue(digits[index])
                if (digit < 0) {
                    tw001(
                        line.number,
                        token.column + 2 + index,
                        "invalid hexadecimal digit '${digits[index]}'",
                    )
                }
                value = (value * 16) + digit
            }
            if (value < 0x20 || value > 0x10FFFF || value in 0xD800..0xDFFF) {
                tw007(line.number, token.column, "invalid code point U+${value.toString(16).uppercase()}")
            }
            return value
        }

        private fun requireNoMore(
            line: Line,
            tokens: List<Token>,
            index: Int,
        ) {
            if (index < tokens.size) {
                tw001(line.number, tokens[index].column, "unexpected trailing token '${tokens[index].text}'")
            }
        }

        private fun directiveTokens(line: Line): List<Token> {
            val text = line.text
            val commentStart = text.indexOf("//")
            val end = if (commentStart >= 0) commentStart else text.length
            val tokens = ArrayList<Token>()
            var index = 0
            while (index < end) {
                while (index < end && isHorizontalSpace(text[index])) {
                    index++
                }
                if (index >= end) {
                    break
                }
                val start = index
                while (index < end && !isHorizontalSpace(text[index])) {
                    index++
                }
                tokens.add(Token(text.substring(start, index), text.codePointCount(0, start) + 1))
            }
            return tokens
        }

        private fun isIgnoredLine(line: Line): Boolean {
            val text = line.text
            var index = 0
            while (index < text.length && isHorizontalSpace(text[index])) {
                index++
            }
            if (index == text.length) {
                return true
            }
            return index + 1 < text.length && text[index] == '/' && text[index + 1] == '/'
        }

        private fun isDirectiveLine(line: Line): Boolean {
            var index = 0
            while (index < line.text.length && isHorizontalSpace(line.text[index])) {
                index++
            }
            return startsWithDirective(line.text, index)
        }

        private fun startsWithDirective(
            text: String,
            start: Int,
        ): Boolean =
            keywordAt(text, start, "version") ||
                keywordAt(text, start, "cell-width") ||
                keywordAt(text, start, "cell-height") ||
                keywordAt(text, start, "fallback") ||
                keywordAt(text, start, "glyph")

        private fun keywordAt(
            text: String,
            start: Int,
            keyword: String,
        ): Boolean {
            if (!text.startsWith(keyword, start)) {
                return false
            }
            val end = start + keyword.length
            return end >= text.length ||
                isHorizontalSpace(text[end]) ||
                (text[end] == '/' && end + 1 < text.length && text[end + 1] == '/')
        }

        private fun firstTokenColumn(text: String): Int {
            var index = 0
            while (index < text.length && isHorizontalSpace(text[index])) {
                index++
            }
            return index + 1
        }

        private fun hexValue(character: Char): Int =
            when (character) {
                in '0'..'9' -> character - '0'
                in 'a'..'f' -> character - 'a' + 10
                in 'A'..'F' -> character - 'A' + 10
                else -> -1
            }

        private fun isDecimal(text: String): Boolean {
            if (text.isEmpty()) {
                return false
            }
            for (character in text) {
                if (character !in '0'..'9') {
                    return false
                }
            }
            return true
        }

        private fun parseCappedDecimal(text: String): Int {
            var value = 0
            for (character in text) {
                value = (value * 10) + (character - '0')
                if (value > MAX_DIMENSION) {
                    return MAX_DIMENSION + 1
                }
            }
            return value
        }

        private fun isHorizontalSpace(character: Char): Boolean = character == ' ' || character == '\t'

        private fun preview(text: String): String {
            val escaped = text.replace("\t", "\\t")
            return if (escaped.length <= 60) escaped else escaped.take(60) + "..."
        }

        private fun tw001(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-001", path, line, column, message)

        private fun tw002(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-002", path, line, column, message)

        private fun tw003(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-003", path, line, column, message)

        private fun tw004(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-004", path, line, column, message)

        private fun tw005(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-005", path, line, column, message)

        private fun tw006(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-006", path, line, column, message)

        private fun tw007(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-007", path, line, column, message)

        private fun tw008(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-008", path, line, column, message)

        private fun tw009(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-009", path, line, column, message)

        private fun tw010(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-010", path, line, column, message)

        private fun tw011(
            line: Int,
            column: Int,
            message: String,
        ): Nothing = throw FontFormatException("TW-011", path, line, column, message)
    }
}
