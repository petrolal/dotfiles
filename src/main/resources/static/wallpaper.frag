#version 330 core

uniform vec2 u_resolution;
uniform float u_time;
uniform float u_cpu; // Normalized 0.0 to 1.0
uniform float u_ram; // Normalized 0.0 to 1.0

out vec4 fragColor;

mat2 rot(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}

float sdOctahedron(vec3 p, float s) {
    p = abs(p);
    return (p.x + p.y + p.z - s) * 0.57735027;
}

float map(vec3 p) {
    vec3 cp = p;
    // Rotate faster as CPU usage spikes
    float rotSpeed = u_time * (0.4 + u_cpu * 2.2);
    cp.xz *= rot(rotSpeed);
    cp.xy *= rot(rotSpeed * 0.6);

    // Surface warp distortion driven by CPU
    float displacement = sin(cp.x * 6.0 + u_time * 2.0) 
                       * sin(cp.y * 6.0) 
                       * sin(cp.z * 6.0) 
                       * (0.04 + 0.12 * u_cpu);

    float core = sdOctahedron(cp, 1.25) + displacement;
    float floorPlane = p.y + 1.8;

    return min(core, floorPlane);
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution) / u_resolution.y;
    vec3 ro = vec3(0.0, 0.5, -4.5);
    vec3 rd = normalize(vec3(uv, 1.0));

    float t = 0.0;
    float glow = 0.0;

    for (int i = 0; i < 56; i++) {
        vec3 p = ro + rd * t;
        float d = map(p);
        glow += 0.015 / (0.025 + abs(d));

        if (d < 0.001 || t > 20.0) break;
        t += d;
    }

    vec3 col = vec3(0.01, 0.01, 0.03);

    // Render floor plane and grid
    if (t < 20.0) {
        vec3 p = ro + rd * t;
        if (p.y < -1.75) {
            vec2 grid = abs(fract(p.xz * 1.5 - vec2(0.0, u_time * 0.4)) - 0.5) / fwidth(p.xz * 1.5);
            float line = 1.0 - min(min(grid.x, grid.y), 1.0);
            col += vec3(0.05, 0.25, 0.6) * line * exp(-0.15 * t);
        }
    }

    // Dynamic color transition: Cyan (idle) to Crimson (100% CPU load)
    vec3 idleColor = vec3(0.05, 0.75, 0.95);
    vec3 loadColor = vec3(1.0, 0.15, 0.2);
    vec3 coreColor = mix(idleColor, loadColor, clamp(u_cpu, 0.0, 1.0));

    col += coreColor * glow * (0.035 + 0.035 * u_ram);

    fragColor = vec4(col, 1.0);
}
