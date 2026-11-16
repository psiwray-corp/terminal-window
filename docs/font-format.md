# terminal-window bitmap font format (`.twf`), version 1

Status: normative. This document defines the on-disk format. It does not define a
parser implementation, an atlas layout, or rendering behavior.

A `.twf` file is a UTF-8 text file. It declares one fixed cell size and a set of
monochrome glyphs. Each glyph is a rectangular mask; `.` is background and `#`
is foreground. Row 0 is the top row and column 0 is the left column.

The format is deliberately line-oriented: every directive and every pixel row
occupies one logical line, and errors can be reported at an exact line and
column. It is intended to be reviewable in a text diff and safe to parse from
untrusted input.

## Worked example

```text
// terminal-window bitmap font
version 1
cell-width 8
cell-height 16

glyph U+0041
....##..
...####.
..#..#..
.#....#.
.######.
.#....#.
.#....#.
........
........
........
........
........
........
........
........
........
```

This is the exact content of the normative `minimal.twf` fixture. It declares one
8 by 16 glyph, `U+0041`. It intentionally omits `fallback`: a fallback must name
a defined glyph, and this fixture has no other glyph to name. The asymmetric top
row makes horizontal and vertical flips observable.

The full ASCII fixture declares `fallback U+003F` and defines every code point
from `U+0020` through `U+007E`, including `U+003F`.

## Lexical model

### Encoding and line endings

- The file MUST be valid UTF-8.
- A UTF-8 byte-order mark (`EF BB BF`) MAY appear at byte offset 0. It is
  ignored. A BOM anywhere else is a `TW-001` error.
- Lines end with LF (`\n`) or CRLF (`\r\n`). The final line MAY omit a line
  ending.
- A `\r` immediately before `\n` is part of the line ending and is removed. A
  bare `\r` is not a line ending and is a `TW-001` error.
- Except for tab (`U+0009`) used as horizontal whitespace, C0 control characters
  are not permitted. NUL, vertical tab, form feed, and other controls are
  `TW-001` errors.
- A logical line longer than 1024 Unicode scalar values, excluding its line
  ending, is a `TW-001` error. The cap bounds work for comments and malformed
  input; it is much larger than the largest possible pixel row (256).

### Lines, whitespace, and comments

- Horizontal whitespace is ASCII space (`U+0020`) or tab (`U+0009`).
- A blank line contains only horizontal whitespace.
- A comment line is a line whose first non-whitespace characters are `//`. The
  rest of the line is ignored. Comments do not nest and run to the line ending.
- A trailing comment may follow a directive or a glyph header. It begins at the
  first `//` on that logical line and runs to the line ending. No whitespace is
  required between the last token and `//`.
- A pixel row is never comment-stripped. `//` in a pixel row is two illegal
  pixel characters, not a comment. `#` is a foreground pixel and is never a
  comment marker.
- Blank lines and comment lines may appear anywhere: before the header, between
  header directives, between glyph headers and their pixel rows, between pixel
  rows, and after the last glyph. They do not count as header declarations or
  glyph pixel rows.
- Whitespace is significant in pixel rows. A row MUST start at column 1 with a
  pixel character. It is not trimmed.

### Tokens

- Directive keywords are case-sensitive: `version`, `cell-width`, `cell-height`,
  `fallback`, and `glyph` MUST be written in lowercase.
- The `U+` prefix of a code point is case-sensitive. The hexadecimal digits are
  case-insensitive.
- A directive line MAY be indented. After a trailing comment is removed,
  leading and trailing horizontal whitespace is ignored and the remaining
  tokens are separated by one or more spaces or tabs. Pixel rows are never
  treated this way.
- Decimal integers consist of one or more ASCII digits (`0` through `9`). A
  sign, decimal point, exponent, or other character is an error. Leading zeros
  are accepted.
- Directives separate their keyword and arguments with one or more spaces or
  tabs. Trailing horizontal whitespace is permitted before a trailing comment or
  the line ending.

## Grammar

The following EBNF describes the accepted shape. `EOF` is end of file. `hspace`
and `comment` are defined below. The semantic constraints in the next sections
are part of conformance; EBNF cannot express them all.

```ebnf
twf             = [ BOM ] , { ignored-line } ,
                  version-line ,
                  { ignored-line } ,
                  width-line ,
                  { ignored-line } ,
                  height-line ,
                  { ignored-line } ,
                  [ fallback-line , { ignored-line } ] ,
                  { glyph-block , { ignored-line } } ,
                  EOF ;

version-line    = hspace* , "version" , hspace+ , "1" ,
                  hspace* , [ "//" , comment ] , line-end ;
width-line      = hspace* , "cell-width" , hspace+ , decimal ,
                  hspace* , [ "//" , comment ] , line-end ;
height-line     = hspace* , "cell-height" , hspace+ , decimal ,
                  hspace* , [ "//" , comment ] , line-end ;
fallback-line   = hspace* , "fallback" , hspace+ , code-point ,
                  hspace* , [ "//" , comment ] , line-end ;

glyph-block     = glyph-line ,
                  { ignored-line } ,
                  pixel-row ,
                  { { ignored-line } , pixel-row } ;
                  (* exactly cell-height pixel-row productions *)

glyph-line      = hspace* , "glyph" , hspace+ , code-point ,
                  hspace* , [ "//" , comment ] , line-end ;

pixel-row       = pixel , { pixel } , line-end ;
                  (* exactly cell-width pixel characters in total *)

ignored-line    = blank-line | comment-line ;
blank-line      = hspace* , line-end ;
comment-line    = hspace* , "//" , comment , line-end ;

code-point      = "U+" , hex-digit , hex-digit , hex-digit , hex-digit ,
                  [ hex-digit , [ hex-digit ] ] ;
decimal         = digit , { digit } ;
pixel           = "." | "#" ;
hex-digit       = digit | "A" | "B" | "C" | "D" | "E" | "F"
                | "a" | "b" | "c" | "d" | "e" | "f" ;
digit           = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7"
                | "8" | "9" ;
hspace          = " " | "\t" ;
comment         = { ? any Unicode scalar except CR and LF ? } ;
line-end        = "\n" | "\r\n" | EOF ;
```

The `pixel-row` production contains exactly the declared `cell-width`
characters, which may be 1 through 256. The `glyph-block` row count is exactly
the declared `cell-height`, which may be 1 through 256.

## Header

The header declarations are required in this order:

1. `version 1`
2. `cell-width <decimal>`
3. `cell-height <decimal>`
4. optional `fallback <code-point>`
5. zero or more glyph blocks

`version`, `cell-width`, and `cell-height` MUST each appear exactly once.
`fallback` MUST appear at most once and MUST appear before the first glyph block.

A file MAY contain zero glyph blocks. Such a font has an empty glyph set and
must not declare `fallback`; a fallback with no glyph to name is `TW-009`.

The version value is the literal decimal integer `1`. Any other value, including
`01`, `1.0`, or an absent `version` line, is unsupported.

`cell-width` and `cell-height` are decimal integers in the inclusive range 1
through 256. Dimensions do not need to be powers of two. Their product is the
number of mask pixels in every glyph.

If `fallback` is absent, a missing glyph is rendered as a blank cell: all
background pixels. If `fallback` is present, it names the glyph to use when a
requested code point is absent. The named glyph MUST be defined by a later
`glyph` block. A fallback line whose code point is not defined is `TW-009`; this
is checked after the whole file has been read, so a forward reference is legal.

## Glyph blocks

A glyph block starts with a `glyph <code-point>` line. Its code point:

- MUST have 4, 5, or 6 hexadecimal digits after `U+`;
- MUST be in the inclusive range `U+0020` through `U+10FFFF`;
- MUST NOT be a surrogate (`U+D800` through `U+DFFF`);
- MUST NOT be `U+0000`.

Code points below `U+0020`, surrogates, and values above `U+10FFFF` are invalid.
Code points are compared numerically: `U+0041`, `U+041`, and `U+00041` are the
same scalar value, but only the 4–6 digit spellings above are syntactically
valid. Duplicate scalar values are `TW-008`, regardless of spelling.

After the header, a glyph block contains exactly `cell-height` pixel rows.
Blank lines and comment lines may be interleaved with those rows and do not
count toward the row count. Each pixel row:

- starts at column 1;
- contains exactly `cell-width` characters;
- contains only `.` (background) and `#` (foreground).

Row 0 is the top row, column 0 is the left column. There is no grayscale,
antialiasing, color, kerning, or variable advance. Every glyph uses the same
cell dimensions; the advance width is the cell width.

A glyph header that repeats an already defined scalar is a duplicate error at
the second header, before its rows are read.

## Limits

- `cell-width` and `cell-height` are each in `1..256`, inclusive.
- A glyph has exactly `cell-width * cell-height` mask pixels.
- A file has at most 65535 glyph blocks. The 65536th block is `TW-010`.
- Atlas slot `0xFFFF` is reserved as the "no glyph" sentinel. Therefore at most
  65535 glyphs can be assigned slots (`0x0000` through `0xFFFE`); code point
  `U+FFFF` itself is a legal glyph code point and does not have to use slot
  `0xFFFF`.
- The baked atlas MUST fit within `GL_MAX_TEXTURE_SIZE` on the target context.
  The atlas layout and the exact check belong to section 09. This is a
  bake-time constraint, not a `.twf` syntax error.
- A logical line is limited to 1024 Unicode scalar values as stated above.

## Semantics

- The font is a fixed-cell monochrome bitmap font. A glyph is a `cell-width` by
  `cell-height` mask, not an image with colors.
- `.` means background; `#` means foreground. No other pixel value exists.
- The renderer may color the foreground and background at draw time. Those
  colors are not stored in the font.
- The cell origin is the top-left of the mask. Vertical flipping, row reversal,
  or bottom-left texture origin handling must not change the decoded mask.
- A requested code point that has no glyph uses the fallback glyph if one was
  declared, otherwise a blank cell.
- Glyph order in the file does not affect lookup, except that the fallback
  declaration must precede the glyphs and may point forward.

## Errors

Every syntax, lexical, or consistency error is reported as:

```text
path:line:column: TW-xxx: message
```

`path` is the file path supplied to the parser. For an anonymous
`InputStream` or `Reader`, it is `<input>`. `line` and `column` are 1-based.
Columns count Unicode scalar values, not bytes; a tab counts as one column. At
end of file, the position is the next scalar position: line 1 column 1 for an
empty file, the next line column 1 after a terminating newline, or one column
after the final scalar when the final line has no newline. For malformed UTF-8,
the position is the first byte that cannot start or continue a valid sequence;
preceding scalars count normally.

The first offending construct in file order is reported. For a pixel row, its
length is checked before its alphabet. For a code point token, its lexical shape
is checked before its scalar range. Duplicate and fallback checks happen at the
point described below.

| Code | Condition | Reported at |
|---|---|---|
| `TW-001` | Unexpected character, malformed UTF-8, bare CR, illegal control character, line longer than 1024 scalars, unknown or out-of-order directive, malformed token, or trailing tokens | First offending scalar |
| `TW-002` | Missing, duplicate, or unsupported `version` declaration | The duplicate/unsupported version line, or EOF for a missing version |
| `TW-003` | Missing, duplicate, or out-of-range `cell-width` / `cell-height` | The invalid/duplicate line, or EOF for a missing declaration |
| `TW-004` | Pixel row length is not exactly `cell-width` | First character after the expected width, or the line ending if short |
| `TW-005` | Pixel row has the correct length but contains a character other than `.` or `#` | The illegal character |
| `TW-006` | Glyph has too few rows when another directive or glyph starts, or too many pixel rows after `cell-height` | The interrupting directive, or the first extra row |
| `TW-007` | Code point value is `U+0000`, below `U+0020`, a surrogate, or above `U+10FFFF` | The code point token |
| `TW-008` | Duplicate glyph code point | The repeated code point token |
| `TW-009` | `fallback` appears twice, or names an undefined glyph | The second fallback line, or the unresolved fallback line |
| `TW-010` | More than 65535 glyph blocks | The 65536th `glyph` line |
| `TW-011` | End of file before all `cell-height` pixel rows of a glyph are present | EOF position |

A line that is recognized as a directive but cannot be lexed is `TW-001`, except
where the code above assigns a more specific condition. Examples:

- `glyph U+ZZZZ`: `TW-001` at the first invalid hexadecimal character.
- `glyph U+D800`: `TW-007` at the code point.
- `glyph U+0041` followed immediately by `glyph U+0042`: `TW-006` at the second
  glyph line.
- A 17th pixel-shaped line after a complete glyph: `TW-006` at that extra row.
- End of file after `glyph U+0041` with zero rows: `TW-011` at EOF.

Comments and blank lines never produce errors unless they contain invalid
UTF-8, a bare CR, an illegal control character, or exceed the line limit.

## Fixtures

- `minimal.twf` has exactly one glyph, `U+0041`, with an asymmetric top row so
  orientation bugs are observable.
- `ascii-8x16.twf` defines the printable ASCII range `U+0020..U+007E` in an
  8 by 16 cell and uses `U+003F` as its fallback. Its glyph data is derived from
  the public-domain `font8x8_basic` glyphs by Daniel Hepper, itself based on
  Marcel Sondaar / IBM public-domain VGA glyphs, and is dedicated to CC0-1.0.
  The source bitmap is padded to the 8 by 16 cell; no glyph is scaled.

Both files are UTF-8 with LF line endings and no BOM.

## Related

[Architecture](architecture.md) · [Public API](public-api.md) ·
[Frame contract](frame.md)
