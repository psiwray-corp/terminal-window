# ADR-0006: Font format

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** project owner
- **Supersedes:** none

## Context

The renderer needs a monochrome bitmap font: a glyph is a rectangle of pixels,
each on or off, with a code point and metrics. The atlas is baked from that
data at load time. Fonts are untrusted input: a malformed file must not crash
the process, over-allocate, or produce a silently wrong atlas.

Version 1 had no documented, versioned font format. Version 2 needs one that is
reviewable in a diff, editable by hand, and strict enough to give precise
errors. Binary formats require endianness decisions, opaque diffs, and tooling
before the first glyph can be drawn.

## Decision

We will use a text format named `.twf`, defined as **version 1**:

- UTF-8 text, one declaration per line.
- The first non-comment line is `version 1`. Other versions are rejected with a
  diagnostic; there is no silent best-effort parse.
- `//` starts a comment that runs to end of line.
- Glyph pixels are `.` (off) and `#` (on). Any other pixel character is an
  error.
- Every glyph declares its dimensions and code point. Every pixel row must have
  exactly the declared width; short and long rows are both errors.
- An optional `fallback` declaration names the glyph used when a requested code
  point is absent.
- Errors report `file:line:column` and the expected versus actual value.

The normative grammar, directives, metrics, and worked examples live in
section 07. This ADR fixes only the format family, the version marker, the pixel
alphabet, and the strictness rule.

Parsing and atlas baking happen at load time, never per frame. The parsed font
is immutable; the atlas is an internal `GL_R8` texture produced from it.

## Consequences

### Positive

- Fonts review and merge as text; a glyph change is a readable diff.
- Hand-authoring a small font or a test font requires no external tool.
- No endianness, alignment, or size-prefix ambiguity.
- Strict row-length and code-point validation makes malformed fonts fail at the
  exact character, satisfying the untrusted-input invariant.
- The version marker allows a future format revision without guessing.

### Negative

- Text is larger than a packed bitmap and slower to parse; acceptable at load
  time only.
- Large fonts (thousands of glyphs) are awkward to edit by hand and need a
  generator.
- The format cannot represent color, grayscale antialiasing, or variable
  advance widths beyond what the metric fields allow.
- No third-party tool reads `.twf`; converting BMFont or TTF requires a
  project-supplied converter.

### Neutral

- The atlas layout is not part of the file format; the baker decides packing.
- Font licensing is the font author's concern; the format itself is
  unencumbered.
- TTF/OTF loading and shaping are out of scope for 2.0.

## Alternatives

| Alternative | Why rejected |
|---|---|
| BMFont text format | Wider tool support, but a larger grammar with unused features (kerning, channels, pages) and weaker line-level diagnostics. |
| BMFont binary | Compact, but opaque diffs and endianness handling for no runtime benefit. |
| PNG atlas plus metrics file | Two files that can disagree; the atlas becomes an untrusted image decoder dependency. |
| Embedded TTF with runtime rasterization | Adds a rasterizer, hinting, shaping, and font licensing; out of scope. |
| Hardcoded 8x16 font in source | No customization, no Unicode coverage, and a rebuilt JAR for every glyph change. |
| JSON or TOML | Requires a parser dependency and escapes pixel rows into strings, making diffs worse. |

## References

- Section 07, `docs/guide/07-font-format.md`, for the normative grammar.
- ADR-0005 for how the baked atlas is sampled.
