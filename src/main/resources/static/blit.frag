#version 330 core

// Upscales the low-resolution offscreen raymarch target to the real window
// size. The texture is sampled with GL_NEAREST (set at creation in
// X11Canvas.createRenderTarget), so this blocky upscale IS the retro-pixel
// look -- not a post-process fake of it.
uniform sampler2D u_texture;

in vec2 v_uv;
out vec4 fragColor;

void main() {
    fragColor = texture(u_texture, v_uv);
}
