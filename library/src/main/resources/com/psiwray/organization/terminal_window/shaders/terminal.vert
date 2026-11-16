#version 330 core

// Per-instance attributes. One instance is one matrix cell.
layout(location = 0) in uvec2 iCell;   // cell column and row
layout(location = 1) in uint  iGlyph;  // atlas slot; 0xFFFF means background only
layout(location = 2) in vec4  iFg;     // foreground RGBA8, normalized
layout(location = 3) in vec4  iBg;     // background RGBA8, normalized

// Per-draw uniforms.
uniform vec2 uViewportPx;  // framebuffer size in pixels
uniform vec2 uCellPx;      // cell size in framebuffer pixels
uniform vec2 uAtlasCellPx; // cell size in atlas texels
uniform vec2 uAtlasGrid;   // atlas columns and rows
uniform vec2 uAtlasTexels; // atlas size in texels

// Values passed to the fragment shader.
out vec2 vUv;               // atlas UV
flat out uint vGlyph;       // integer varying: one provoking-vertex value per primitive
out vec4 vFg;               // foreground color
out vec4 vBg;               // background color

void main() {
    // gl_VertexID 0..3 selects the four corners of the cell quad:
    // (0,0), (1,0), (0,1), (1,1). No vertex buffer is used.
    vec2 corner = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));

    // Cell-local pixels -> framebuffer pixels -> clip space.
    // Cell (0,0) is top-left, so negate NDC y.
    vec2 px = (vec2(iCell) + corner) * uCellPx;
    vec2 ndc = px / uViewportPx * 2.0 - 1.0;
    gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);

    // Decode the row-major atlas slot into grid coordinates, then build the
    // UV for this corner. Atlas data row 0 is the glyph top and maps to v=0;
    // the surface top also uses v=0, so no vertical flip is needed.
    float sx = float(iGlyph % uint(uAtlasGrid.x));
    float sy = float(iGlyph / uint(uAtlasGrid.x));
    vUv = (vec2(sx, sy) * uAtlasCellPx + corner * uAtlasCellPx) / uAtlasTexels;

    // Integer and color attributes pass through unchanged.
    vGlyph = iGlyph;
    vFg = iFg;
    vBg = iBg;
}