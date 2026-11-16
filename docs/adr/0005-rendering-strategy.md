# ADR-0005: Rendering strategy

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** project owner
- **Supersedes:** none

## Context

A terminal renders a dense grid of small, mostly static glyph cells. The cost
driver is per-cell CPU and driver overhead, not fragment shading: a 200x60
terminal is 12,000 cells and 48,000 vertices if emitted naively. The library
also promises a fixed budget: one instance upload and one draw call per frame,
with zero per-frame heap allocation after warm-up.

The atlas is a mask: glyphs are monochrome bitmaps, one byte per pixel. Color
comes from the cell's foreground and background, not from the glyph texture.
Neither transparency nor overlap is required to draw a terminal: each cell
paints its background and then its foreground, and cells tile the window.

OpenGL 3.3 core is the floor. `gl_VertexID` is core in 3.0, but
`glVertexAttribDivisor` is core only in 3.3; OpenGL 3.0 would require
`ARB_instanced_arrays` or per-vertex expansion. macOS exposes at most 4.1 core,
so 3.3 is the portable floor.

## Decision

We will render the cell grid with instanced quads and no per-cell geometry:

1. **One draw call per window per frame:** `glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4,
   cellCount)`. The vertex shader derives the unit quad from `gl_VertexID`; no
   vertex buffer and no index buffer exist.
2. **One instance buffer:** one VBO with divisor 1 on each per-instance
   attribute. Exactly one upload per rendered frame, regardless of how many
   cells changed. Mutations coalesce into the buffer; there is never one GL call
   per cell.
3. **16 bytes per instance**, in this fixed layout:

   | Offset | Type | Field |
   |---:|---|---|
   | 0 | `u16` | cell column |
   | 2 | `u16` | cell row |
   | 4 | `u16` | glyph index in the atlas |
   | 6 | `u16` | padding (written as zero) |
   | 8 | 4 x `u8` | foreground, RGBA8 |
   | 12 | 4 x `u8` | background, RGBA8 |

   The buffer is a direct `ByteBuffer` in native byte order; multibyte fields
   are read by GL as host-endian `u16`. Fields are written by indexed loops,
   without boxing or per-cell objects.
4. **One `GL_R8` atlas:** 1 byte per texel, glyph index maps to an atlas slot.
   The fragment shader reads the mask with `texelFetch` and selects foreground
   or background: mask at or above the half-intensity value selects foreground,
   otherwise background. Filtering is `GL_NEAREST`; no mipmaps are generated or
   sampled.
5. **No blending, no depth, no stencil, no multisampling** in the library's
   draw path. Each cell writes an opaque color; cells do not overlap.
6. **OpenGL 3.3 core, GLSL `#version 330`.** Context creation tries 3.3 core,
   then 4.1 core, then fails with a diagnostic containing the requested
   versions and the error text from `glfwGetError`.

Exact upload mechanics (orphaning versus `glBufferSubData`, persistent mapped
buffers) and the shader source are owned by sections 15 and 16.

## Consequences

### Positive

- Draw submission is constant: one upload and one draw call per frame,
  independent of cell count.
- The instance buffer is 16 B/cell; a 200x60 grid is 192 KB.
- No vertex/index buffer means no geometry upload and no topology bookkeeping.
- The R8 atlas is 1 B/px, a quarter of RGBA8, and matches the monochrome glyph
  model exactly.
- `gl_VertexID` removes the need to store quad corners per instance.

### Negative

- The whole grid is redrawn each frame; there is no damage tracking. Static
  content still costs the same fill rate.
- Cells are opaque. Translucent foregrounds, background images, and compositing
  over existing framebuffer content are impossible in 2.0.
- There is no antialiasing or subpixel coverage; glyphs are hard-edged at 1:1
  scale.
- Color glyphs (emoji), grayscale antialiasing data, and multiple atlas formats
  do not fit the single-channel mask.
- The window must be fully covered by cells; the library must not leave gaps,
  because there is no clear or background pass.

### Neutral

- Alpha bytes are carried in the instance layout for future use but have no
  effect: the default framebuffer is opaque and blending is disabled.
- A 3.3 core context is required; OpenGL 3.0, ES, and compatibility profiles are
  not supported.
- Frame pacing and vsync are separate concerns (section 14); this ADR fixes only
  the draw path.

## Alternatives

| Alternative | Why rejected |
|---|---|
| One draw call per glyph | Thousands of driver calls per frame; dominates CPU time. |
| Non-instanced VBO with 4 vertices per cell | 4x the instance traffic and a second buffer; no visual gain. |
| Per-cell textured quads with a color texture | Adds texture bandwidth and another format; color comes from vertex attributes for free. |
| SDF or TTF rasterization | Out of scope for 2.0; adds shaping and font licensing complexity. |
| Blending for subpixel antialiasing | Requires ordered drawing and a background pass; terminal cells are opaque. |
| OpenGL 3.0 + `ARB_instanced_arrays` | Extension detection and fallback paths for no functional gain; macOS supports 3.3. |
| Vulkan or EGL | Out of scope for 2.0; the instanced path does not need a second graphics API. |

## References

- OpenGL 3.3 core specification: `glVertexAttribDivisor`, `gl_VertexID`.
- `docs/architecture.md` for the instance layout in the frame pipeline.
- ADR-0006 for the glyph source that feeds the atlas.
