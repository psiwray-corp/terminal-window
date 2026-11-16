# 08 — Font parser

**Phase:** assets (pure Kotlin) · **Depends on:** 07 · **Blocks:** 09

## Goal

Parse `.twf` into an immutable `BitmapFont` with strict validation, precise errors, and no GL/LWJGL imports.

## Deliverables

- `library/src/main/kotlin/com/psiwray/organization/terminal_window/font/BitmapFont.kt`
- `.../font/Glyph.kt` (`internal class Glyph(val codePoint: Int, val mask: ByteArray)` — no `data class`, `ByteArray` equality is identity)
- `.../font/FontFormatException.kt`
- `.../font/BitmapFontParser.kt` (`internal object`, `parse` delegates)
- `library/src/test/kotlin/.../font/BitmapFontParserTest.kt`
- Fixtures under `src/test/resources/fonts/{valid,invalid}/`.

## Steps

1. **API.**
   ```kotlin
   class BitmapFont internal constructor(
       val cellWidth: Int,
       val cellHeight: Int,
       val fallbackCodePoint: Int?,
       internal val glyphs: Map<Int, Glyph>,
   ) {
       val glyphCount: Int get() = glyphs.size
       val codePoints: Set<Int> get() = glyphs.keys
       fun hasGlyph(codePoint: Int): Boolean = glyphs.containsKey(codePoint)
       companion object {
           @JvmStatic fun parse(path: Path): BitmapFont
           @JvmStatic fun parse(input: InputStream): BitmapFont
           @JvmStatic fun parse(reader: Reader): BitmapFont
       }
   }
   ```
   `parse(Path)` opens and closes the stream; `IOException` propagates; syntax errors throw `FontFormatException`.
2. **Error type.** `class FontFormatException(val code: String, val path: String?, val line: Int, val column: Int, message: String) : IllegalArgumentException("$path:$line:$column: $code: $message")`.
3. **Decoding.** Use `Charsets.UTF_8.newDecoder()` with `CodingErrorAction.REPORT` so malformed bytes are `TW-001`, not ``. Strip a leading BOM.
4. **Scanner.** Single pass over logical lines; track absolute line number and column at the error; no regex per line; hard cap line length (e.g. 1024).
5. **State machine.** Header → glyph blocks; header order is strict (from 07). A glyph block reads exactly `cellHeight` rows, validating length and alphabet before storing.
6. **Mask representation.** `ByteArray(cellWidth * cellHeight)` per glyph with 0/1. At 8×16 that is 128 B/glyph; 95 glyphs ≤ 12 KB. No bit packing yet; section 09 consumes it.
7. **Errors.** One factory per TW code; message includes a truncated offending line for row errors. Duplicate code point → `TW-008`; fallback resolution after all glyphs → `TW-009`.
8. **Tests.** Table-driven: every TW code has at least one fixture; plus CRLF, BOM, trailing directive comment, blank lines, max dimensions (256×256), glyph-limit boundary (generated in memory, not a huge fixture), and malformed UTF-8.

## Acceptance

- [x] `parse` accepts the two valid fixtures from 07 and rejects every `invalid/` fixture with the expected TW code and line.
- [x] No LWJGL/GLFW imports anywhere in `font`.
- [x] A 95-glyph 8×16 font parses in under ~1 ms (measured once; no flaky CI threshold).
- [x] Duplicate `fallback` and undefined fallback both fail with `TW-009`.
- [x] `parse(null as Path?)` is a Kotlin compile error for Kotlin callers; Java callers get a null-check exception.
- [x] Every TW branch is covered by tests.

## Pitfalls

- `BufferedReader.readLine()` hides whether the last line had a newline; the error column for EOF-inside-glyph must still be meaningful (`TW-011`).
- `String[i]` breaks surrogate pairs; validate scalars with `Character.isValidCodePoint` and explicit surrogate rejection.
- Do not lowercase the whole line: pixel rows are case-sensitive.
- `Glyph` should not be a `data class`: `ByteArray` equality/hashCode would be reference-based and misleading.
- Internal visibility is not encryption: `internal` members are still public in bytecode; do not put secrets there.

## Plan seed

> Plan section 08 only: implement a strict, streaming `.twf` parser in Kotlin returning an immutable `BitmapFont` with line/column errors (TW-001..TW-011), plus table-driven tests. No GL, no atlas, no rendering.
