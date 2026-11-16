# Shader identifiers and uniform contract

The terminal renderer uses one GLSL 330 program:

- `library/src/main/resources/com/psiwray/organization/terminal_window/shaders/terminal.vert`
- `library/src/main/resources/com/psiwray/organization/terminal_window/shaders/terminal.frag`

`ShaderProgram` loads both resources, caches the uniform locations, and
rejects a program whose interface does not match this contract. `InstanceFormat`
is the Kotlin source of truth for attribute locations and GL types. The byte
layout itself remains normative in ADR-0005; this page documents the shader
identifiers that cross the Kotlin/GLSL boundary.

## Vertex attributes

`gl_VertexID` selects one of four quad corners. It is a GLSL built-in, not a
vertex attribute, and needs no vertex buffer.

| Location | GLSL identifier | GLSL type | GL type | Normalized | Divisor | Meaning |
|---:|---|---|---|---|---|---|
| 0 | `iCell` | `uvec2` | `GL_UNSIGNED_SHORT` | no | 1 | Cell column and row; one instance per matrix cell. |
| 1 | `iGlyph` | `uint` | `GL_UNSIGNED_SHORT` | no | 1 | Atlas slot; `0xFFFFu` means background only. |
| 2 | `iFg` | `vec4` | `GL_UNSIGNED_BYTE` | yes | 1 | Foreground RGBA bytes in `R,G,B,A` order. |
| 3 | `iBg` | `vec4` | `GL_UNSIGNED_BYTE` | yes | 1 | Background RGBA bytes in `R,G,B,A` order. |

`iCell` and `iGlyph` use `glVertexAttribIPointer`; the color attributes use
the normalized `glVertexAttribPointer` form. The stride is 16 bytes, with
bytes 6-7 reserved and written as zero. Kotlin callers should use the constants
in `InstanceFormat` rather than repeating the location numbers.

## Vertex shader outputs

| Identifier | GLSL type | Interpolation | Meaning |
|---|---|---|---|
| `vUv` | `vec2` | smooth | Atlas UV for the quad corner. Atlas data row 0 maps to `v=0`, and the surface top also uses `v=0`, so no vertical flip is applied. |
| `vGlyph` | `uint` | flat | Atlas slot copied from `iGlyph`; integer varyings are flat, and every fragment of a cell sees the same slot. The fragment shader tests it against `0xFFFFu`. |
| `vFg` | `vec4` | smooth | Foreground color copied from `iFg`. |
| `vBg` | `vec4` | smooth | Background color copied from `iBg`. |
| `gl_Position` | `vec4` | - | Clip-space quad position. The shader negates NDC y so cell `(0,0)` is top-left. |

## Uniforms

All locations are cached once in `ShaderProgram.Uniforms`. A missing uniform is
a link-time failure, not a draw-time fallback.

| GLSL identifier | GLSL type | Set by | Units and contract |
|---|---|---|---|
| `uViewportPx` | `vec2` | Renderer, per draw | Framebuffer width and height in pixels. Must be positive when drawing. |
| `uCellPx` | `vec2` | Renderer, per draw | Cell width and height in framebuffer pixels. |
| `uAtlasCellPx` | `vec2` | Renderer, per draw | Atlas cell width and height in texels. This may differ from `uCellPx` when display scaling is not 1:1. |
| `uAtlasGrid` | `vec2` | Renderer, per draw | Atlas column and row counts. The shader casts `.x` to `uint` for slot decoding. Must be positive when drawing. |
| `uAtlasTexels` | `vec2` | Renderer, per draw | Atlas width and height in texels. Must be positive when drawing. |
| `uAtlas` | `sampler2D` | `ShaderProgram` once | Sampler bound to texture unit 0. `GlyphAtlas.bind()` makes unit 0 active and binds the R8 texture. |

The vertex shader converts atlas slots with
`(iGlyph % uAtlasGrid.x, iGlyph / uAtlasGrid.x)`. The fragment shader samples
the R8 mask and uses it to mix `vBg` and `vFg`. GL blending is disabled; the
fragment shader writes the final opaque color.

## Coordination rules

- Attribute locations are fixed by `layout(location = ...)` in `terminal.vert`
  and mirrored by `InstanceFormat`.
- Attribute 0 and 1 are integer attributes; using the float pointer form would
  silently corrupt cell coordinates or glyph slots.
- The VAO must be bound for `gl_VertexID`; no position attribute or dummy vertex
  buffer exists.
- The atlas is uploaded top-down, and its data row 0 maps to texture `v=0`.
  The vertex shader maps the top of each cell to `v=0`; adding a vertical flip
  would render glyphs upside down.
- Slot `0xFFFF` is reserved for "no glyph" and must never be assigned by
  `AtlasBaker`.
- The sampler stays on unit 0; do not bind the glyph texture to another unit
  without also changing `uAtlas`.
- `ShaderProgram` captures compile and link logs even on success. Compilation,
  link, missing-resource, and missing-uniform failures throw `GlException` with
  the relevant source name and driver log.

## Related

[Architecture](architecture.md) · [Frame contract](frame.md) ·
[Performance](performance.md)