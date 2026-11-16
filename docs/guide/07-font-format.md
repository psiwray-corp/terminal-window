# 07 — Bitmap font format (`.twf`) specification

**Phase:** assets · **Depends on:** 01 · **Blocks:** 08, 09

## Goal

A normative, versioned text format that defines cell size and per-code-point pixels, with zero ambiguity and line/column errors. No parser code here.

## Deliverables

- `docs/font-format.md`: grammar, semantics, limits, error catalogue, worked example.
- `src/test/resources/fonts/valid/minimal.twf`: the normative example used by 08.
- `src/test/resources/fonts/valid/ascii-8x16.twf`: a full CC0 ASCII font (content work, one-time).
- Update the section table in `GUIDE.md`.

## Format (normative shape)

```text
// terminal-window bitmap font
version 1
cell-width 8
cell-height 16
fallback U+003F

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

Rules to write down:

1. UTF-8; optional BOM accepted and ignored; LF or CRLF accepted; trailing `\r` stripped.
2. Comments: a line whose first non-space characters are `//`. Trailing `//` comments are allowed **after directives only**, never after pixel rows.
3. Required order: `version 1`, `cell-width`, `cell-height`, then zero or more `glyph` blocks. Blank lines allowed anywhere.
4. `cell-width`/`cell-height`: decimal integers, 1..256.
5. `glyph U+XXXX`: scalar value in the range `U+0020..U+10FFFF`, excluding surrogates `D800..DFFF`; hex digits case-insensitive; 4–6 digits. `U+0000` is reserved and rejected.
6. Each glyph is exactly `cell-height` rows; each row is exactly `cell-width` characters from `.` (background) and `#` (foreground). Any other character is an error.
7. Duplicate code points are an error.
8. Optional `fallback U+XXXX` (once) must name a glyph defined later; default behavior when absent is a blank cell (background only).
9. Limits: at most 65535 glyphs (slot `0xFFFF` is reserved for "no glyph"); the baked atlas must fit `GL_MAX_TEXTURE_SIZE` at bake time (section 09 computes it; the spec states the constraint).

## Error catalogue (fix these codes now)

| Code | Condition |
|---|---|
| `TW-001` | Unexpected character / unterminated line |
| `TW-002` | Missing or unsupported `version` |
| `TW-003` | Missing/duplicate/out-of-range cell dimension |
| `TW-004` | Row shorter or longer than `cell-width` |
| `TW-005` | Illegal pixel character |
| `TW-006` | Glyph row count mismatch |
| `TW-007` | Invalid scalar value (surrogate, > `10FFFF`, `0000`) |
| `TW-008` | Duplicate code point |
| `TW-009` | `fallback` names an undefined glyph or appears twice |
| `TW-010` | Glyph limit exceeded |
| `TW-011` | Unexpected EOF inside a glyph block |

Every error message must be `path:line:column: TW-xxx: message`.

## Acceptance

- [x] Grammar is expressible as EBNF in the doc and matches the example.
- [x] All comments/ordering/limits decisions are stated, not implied.
- [x] The minimal example has exactly one glyph with a distinguishable asymmetric pixel (so 09 can prove orientation).
- [x] The full ASCII font uses only code points 0x20–0x7E plus fallback, and has no duplicate glyphs.
- [x] `docs/font-format.md` contains no `TBD`.

## Pitfalls

- `#` is a pixel, so `#` cannot start a comment. Use `//`.
- Strict row-length checking is the whole point: do not trim whitespace from pixel rows.
- Do not require power-of-two cell sizes or texture dimensions; GL 3.3 supports NPOT.
- A fallback glyph defined *after* its use is legal; do not enforce ordering beyond the header.

## Plan seed

> Plan section 07 only: write the normative `.twf` v1 spec (grammar, exact rules above, error catalogue, limits), plus the minimal and full ASCII fixture files. No parser code.
