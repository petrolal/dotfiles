// CRT / VHS post-process effect for Qt Quick 3D Retro Room
// Curvature, scanlines, chromatic aberration, PS1 Bayer dithering, and tube vignette.
// Retro indie game visual aesthetic ("Tache Noire" / PS1 software-rendering style).

const float curvature = 5.5;
const float vignette_strength = 24.0;
const float scanline_strength = 0.09;
const float rgb_aberration = 0.0015;

vec2 curve(vec2 uv) {
    uv = (uv - 0.5) * 2.0;
    uv.x *= 1.0 + pow((abs(uv.y) / curvature), 2.0);
    uv.y *= 1.0 + pow((abs(uv.x) / curvature), 2.0);
    uv = (uv / 2.0) + 0.5;
    return uv;
}

// 4x4 Bayer Ordered Dither Matrix for PS1 software-rendering aesthetic
float bayer4x4(vec2 p) {
    int x = int(mod(p.x, 4.0));
    int y = int(mod(p.y, 4.0));
    int index = x + y * 4;

    float pattern[16] = float[16](
         0.0 / 16.0,  8.0 / 16.0,  2.0 / 16.0, 10.0 / 16.0,
        12.0 / 16.0,  4.0 / 16.0, 14.0 / 16.0,  6.0 / 16.0,
         3.0 / 16.0, 11.0 / 16.0,  1.0 / 16.0,  9.0 / 16.0,
        15.0 / 16.0,  7.0 / 16.0, 13.0 / 16.0,  5.0 / 16.0
    );
    return pattern[index] - 0.5;
}

void MAIN() {
    vec2 uv = curve(INPUT_UV);

    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        FRAGCOLOR = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec4 col;
    col.r = texture(INPUT, vec2(uv.x + rgb_aberration, uv.y)).r;
    col.g = texture(INPUT, uv).g;
    col.b = texture(INPUT, vec2(uv.x - rgb_aberration, uv.y)).b;
    col.a = 1.0;

    // Scanlines
    float scanline = sin(uv.y * 960.0 * 3.14159265);
    col.rgb -= col.rgb * (scanline * 0.5 + 0.5) * scanline_strength;

    // Tube Vignette
    vec2 vigUV = uv * (1.0 - uv.yx);
    float vig = vigUV.x * vigUV.y * vignette_strength;
    vig = clamp(pow(vig, 0.20), 0.0, 1.0);
    col.rgb *= vig;

    // Deep inky shadows & high-contrast chiaroscuro curve
    col.rgb = pow(col.rgb, vec3(1.15));

    // PS1 15-bit RGB555 quantization with Bayer dithering
    float dither = bayer4x4(FRAGCOORD.xy);
    col.rgb += (dither / 32.0) * 0.9;
    col.rgb = floor(col.rgb * 32.0 + 0.5) / 32.0;

    FRAGCOLOR = col;
}
