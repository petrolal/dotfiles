#version 330 core

layout(location = 0) in vec2 position;

out vec2 v_uv;

void main() {
    // Zero-overhead clip-space fullscreen quad pass-through
    gl_Position = vec4(position, 0.0, 1.0);
    v_uv = position * 0.5 + 0.5;
}