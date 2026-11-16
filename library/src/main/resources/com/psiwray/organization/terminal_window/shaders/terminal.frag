#version 330 core

// Interpolated from the vertex stage.
in vec2 vUv;          // atlas UV
flat in uint vGlyph;  // integer varying: one value per primitive; 0xFFFF = no glyph
in vec4 vFg;          // foreground color
in vec4 vBg;          // background color

// R8 glyph mask; bound to texture unit 0.
uniform sampler2D uAtlas;

out vec4 oColor;

void main() {
    // Slot 0xFFFF has no glyph, so its mask is zero. Otherwise sample the
    // single-channel atlas; nearest filtering yields 0 or 1.
    float mask = (vGlyph == 0xFFFFu) ? 0.0 : texture(uAtlas, vUv).r;

    // Select background or foreground. GL blending is disabled; this writes
    // the final opaque color.
    oColor = mix(vBg, vFg, mask);
}