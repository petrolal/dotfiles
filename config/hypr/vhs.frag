// CRT screen shader for Hyprland / RetroVHSSucks
// Pure static CRT simulation: barrel curvature, scanlines, chromatic aberration, and tube vignette.
// Uses NO 'time' uniform to keep GPU utilization and power consumption at 0% idle.

#version 300 es
precision mediump float;

in vec2 v_texcoord;
layout(location = 0) out vec4 fragColor;
uniform sampler2D tex;

// CRT Curvature (smaller value = more curvature, 4.0 - 6.0 is sweet spot)
const float curvature = 5.5;

// Vignette strength around edges
const float vignette_strength = 24.0;

// Scanline intensity (0.0 to 1.0)
const float scanline_strength = 0.08;

// RGB chromatic aberration offset
const float rgb_aberration = 0.0012;

// Barrel curve transformation for authentic tube glass shape
vec2 curve(vec2 uv) {
    uv = (uv - 0.5) * 2.0;
    uv.x *= 1.0 + pow((abs(uv.y) / curvature), 2.0);
    uv.y *= 1.0 + pow((abs(uv.x) / curvature), 2.0);
    uv = (uv / 2.0) + 0.5;
    return uv;
}

void main() {
    vec2 uv = curve(v_texcoord);

    // Black border outside the curved CRT glass
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Chromatic aberration (RGB shift simulating CRT electron beam alignment)
    vec4 col;
    col.r = texture(tex, vec2(uv.x + rgb_aberration, uv.y)).r;
    col.g = texture(tex, uv).g;
    col.b = texture(tex, vec2(uv.x - rgb_aberration, uv.y)).b;
    col.a = 1.0;

    // Scanlines (horizontal phosphor scanning beam)
    float scanline = sin(uv.y * 900.0 * 3.14159265);
    col.rgb -= col.rgb * (scanline * 0.5 + 0.5) * scanline_strength;

    // Vignette (darker tube corners)
    vec2 vigUV = uv * (1.0 - uv.yx);
    float vig = vigUV.x * vigUV.y * vignette_strength;
    vig = clamp(pow(vig, 0.18), 0.0, 1.0);
    col.rgb *= vig;

    fragColor = col;
}
