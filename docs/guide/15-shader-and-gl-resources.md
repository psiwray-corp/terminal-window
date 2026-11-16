# 15 — Shader and GL resources

**Phase:** rendering · **Depends on:** 06, 09, 12 · **Blocks:** 16

## Goal

One program, one VAO, one instance VBO, one R8 atlas texture. Correct attribute layout and exact shader math, with compile/link diagnostics and no per-frame allocation.

## Deliverables

- `library/src/main/resources/com/psiwray/organization/terminal_window/shaders/terminal.vert`
- `library/src/main/resources/com/psiwray/organization/terminal_window/shaders/terminal.frag`
- `.../internal/ShaderProgram.kt` (compile/link/log/uniform cache)
- `.../internal/GlyphAtlas.kt` (R8 upload + params + delete)
- `.../internal/InstanceFormat.kt` (`object` with stride/offsets + attribute setup)
- `library/src/integrationTest/kotlin/.../ShaderProgramTest.kt`, `GlyphAtlasTest.kt`

## Instance layout (must not drift)

| loc | GLSL | bytes | GL type | offset | divisor |
|---|---|---|---|---|---|
| 0 | `uvec2 iCell` | 2×u16 | `GL_UNSIGNED_SHORT`, integer pointer | 0 | 1 |
| 1 | `uint iGlyph` | u16 | `GL_UNSIGNED_SHORT`, integer pointer | 4 | 1 |
| 2 | `vec4 iFg` | 4×u8 | `GL_UNSIGNED_BYTE`, normalized | 8 | 1 |
| 3 | `vec4 iBg` | 4×u8 | `GL_UNSIGNED_BYTE`, normalized | 12 | 1 |

Stride 16. Bytes 6–7 reserved (zero). Slot `0xFFFF` means "no glyph" (background only).

## Shader (contract)

`terminal.vert`:

```glsl
#version 330 core
layout(location = 0) in uvec2 iCell;
layout(location = 1) in uint  iGlyph;
layout(location = 2) in vec4  iFg;
layout(location = 3) in vec4  iBg;
uniform vec2 uViewportPx;
uniform vec2 uCellPx;
uniform vec2 uAtlasCellPx;
uniform vec2 uAtlasGrid;
uniform vec2 uAtlasTexels;
out vec2 vUv;
flat out uint vGlyph;
out vec4 vFg;
out vec4 vBg;
void main() {
    vec2 corner = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));
    vec2 px = (vec2(iCell) + corner) * uCellPx;
    vec2 ndc = px / uViewportPx * 2.0 - 1.0;
    gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);
    float sx = float(iGlyph % uint(uAtlasGrid.x));
    float sy = float(iGlyph / uint(uAtlasGrid.x));
    vUv = (vec2(sx, sy) * uAtlasCellPx + corner * uAtlasCellPx) / uAtlasTexels;
    vGlyph = iGlyph;
    vFg = iFg;
    vBg = iBg;
}
```

`terminal.frag`:

```glsl
#version 330 core
in vec2 vUv;
flat in uint vGlyph;
in vec4 vFg;
in vec4 vBg;
uniform sampler2D uAtlas;
out vec4 oColor;
void main() {
    float mask = (vGlyph == 0xFFFFu) ? 0.0 : texture(uAtlas, vUv).r;
    oColor = mix(vBg, vFg, mask);
}
```

The identifier and uniform contract is documented in
[shaders.md](../shaders.md).

## Steps

1. **Load.** UTF-8 classpath resource loader; fail with a message naming the missing path.
2. **Compile/link.** `glCreateShader`, source, compile, fetch log (always, not only on failure), throw `GlException` with log + source name; link; detach/delete shaders; cache uniform locations in a small `class Uniforms`; `glGetError` after setup.
3. **Texture.** `GL_R8`, `GL_RED`, `GL_UNSIGNED_BYTE`; `glPixelStorei(GL_UNPACK_ALIGNMENT, 1)` before upload and restore to 4; `GL_NEAREST` min/mag; `GL_CLAMP_TO_EDGE`; no mipmaps; `glUniform1i(uAtlas, 0)` and always bind `GL_TEXTURE0`.
4. **Byte transfer.** `Atlas.pixels` is a `ByteArray`; wrap once at creation: `ByteBuffer.allocateDirect(size).put(pixels).flip()` (or LWJGL `MemoryUtil`), upload, discard the buffer. No per-frame copy.
5. **VAO.** Create once: bind VBO, set 4 attributes per the table, enable them, `glVertexAttribDivisor(loc, 1)`, unbind. Upload is section 16's job; the VAO stores the binding.
6. **State.** At setup: disable depth test, blend, cull face, dither. Do not set clear color here.
7. **Cleanup.** Idempotent `close()`: delete texture, VBO, VAO, program; null handles; no GL calls after close.
8. **Tests.** Program links; `glGetActiveAttrib` reports the 4 attributes with expected types; all uniforms present; texture params as specified; deleting twice is safe; shader source contains `#version 330 core`.

## Acceptance

- [ ] Program compiles/links on llvmpipe and on a real driver (manual check).
- [x] Attribute introspection matches the table exactly (name, type, location).
- [x] A deliberately broken shader produces a `GlException` containing the driver log and the shader name.
- [x] Texture upload happens exactly once at creation; no allocation in later frames.
- [x] `glGetError() == GL_NO_ERROR` after creation and after each test.

## Pitfalls

- `GL_UNPACK_ALIGNMENT` defaults to 4; an 8-pixel-wide R8 row is fine, but a 10-pixel row is not. Set to 1 and restore.
- `glVertexAttribPointer` vs `glVertexAttribIPointer` matters: integer attributes read with the float variant are silently wrong.
- The atlas is baked top-down with data row 0 at texture `v=0`; the cell top also samples `v=0`. Adding a vertical flip renders every glyph upside down (the asymmetry test in 16 catches it).
- `gl_VertexID` requires a VAO bound and no position attribute; do not add a dummy vertex buffer.
- Kotlin `ByteArray` → `ByteBuffer` copies once; do not wrap the array with `ByteBuffer.wrap` for GL (heap buffers are not direct).

## Plan seed

> Plan section 15 only: add the GLSL 330 vertex/fragment pair, a Kotlin shader program wrapper with logging, an R8 atlas texture object, and the 16-byte instanced attribute layout (locations 0–3, divisor 1), plus GL integration tests for introspection and errors. No draw call or upload loop.