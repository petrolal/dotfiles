#version 330 core
out vec4 FragColor;

in vec2 v_uv;

uniform float u_time;
uniform float u_timeOfDay; // 0.0 - 24.0
uniform vec2 u_resolution;

// Theme Colors
const vec3 C_BG_DEEP    = vec3(0.04, 0.01, 0.06); // #0a0210
const vec3 C_BLOOD_RED  = vec3(0.85, 0.05, 0.20); // #d90d33
const vec3 C_CRIMSON    = vec3(0.55, 0.02, 0.15); // #8c0526
const vec3 C_PURPLE     = vec3(0.35, 0.05, 0.40); // #590d66
const vec3 C_SICK_GREEN = vec3(0.18, 0.38, 0.15); // #2e6126
const vec3 C_CYBER_ACID = vec3(0.20, 0.95, 0.30); // Wireframe grid

// Noise / Hash Functions
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
            mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// 3D Terrain Heightfield (FBM)
float terrainHeight(vec2 p) {
    float h = 0.0;
    mat2 rot = mat2(0.8, -0.6, 0.6, 0.8);
    vec2 q = p * 0.15;
    h += 2.2 * noise(q); q = rot * q * 2.02;
    h += 1.1 * noise(q); q = rot * q * 2.03;
    h += 0.4 * noise(q);
    return h - 1.5;
}

// Raymarching Terrain Intersection
float map(vec3 p) {
    return p.y - terrainHeight(p.xz);
}

vec3 calcNormal(vec3 p) {
    float eps = 0.01;
    float d = map(p);
    return normalize(vec3(
            map(vec3(p.x + eps, p.y, p.z)) - d,
            eps,
            map(vec3(p.x, p.y, p.z + eps)) - d
    ));
}

void main() {
    vec2 res = (u_resolution.x > 1.0 && u_resolution.y > 1.0) ? u_resolution : vec2(1920.0, 1080.0);
    vec2 uv = (gl_FragCoord.xy - 0.5 * res) / res.y;

    float hour = (u_timeOfDay >= 0.0 && u_timeOfDay <= 24.0) ? u_timeOfDay : 12.0;

    // --- 1. Realistic Celestial Orbit (Sun & Moon) ---
    // Angle in radians: 06:00 = Horizon East, 12:00 = Top Zenith, 18:00 = Horizon West
    float sunAngle = (hour - 6.0) * (3.14159265 / 12.0);
    vec3 sunDir = normalize(vec3(cos(sunAngle), sin(sunAngle), -0.6));
    vec3 moonDir = -sunDir; // Moon sits diametrically opposite to the sun

    float isDay = smoothstep(-0.1, 0.2, sunDir.y);
    float isNight = 1.0 - isDay;

    // Glitch effect on midnight
    float glitchPulse = step(0.94 - (0.04 * isNight), sin(u_time * 3.0) * cos(u_time * 7.0));
    float slice = (hash(vec2(floor(gl_FragCoord.y * 0.08), floor(u_time * 16.0))) - 0.5) * 0.04 * glitchPulse;
    uv.x += slice;

    // --- 2. Camera Setup ---
    vec3 ro = vec3(0.0, 1.8, 4.0); // Eye camera origin
    vec3 ta = vec3(0.0, 0.5, -4.0); // Target view
    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = normalize(cross(uu, ww));
    vec3 rd = normalize(uv.x * uu + uv.y * vv + 1.2 * ww); // Ray direction

    // --- 3. Sky Rendering (Day vs Night Horror) ---
    vec3 daySky = mix(vec3(0.25, 0.05, 0.35), vec3(0.65, 0.15, 0.45), max(rd.y, 0.0));
    vec3 nightSky = mix(C_BG_DEEP, C_PURPLE * 0.5, max(rd.y, 0.0));
    vec3 skyColor = mix(nightSky, daySky, isDay);

    // Render Sun (Day)
    float sunDiff = max(dot(rd, sunDir), 0.0);
    float sunDisc = smoothstep(0.996, 0.998, sunDiff);
    vec3 sunVisual = vec3(1.0, 0.9, 0.7) * sunDisc * 2.0 + vec3(1.0, 0.2, 0.4) * pow(sunDiff, 16.0);

    // Render Blood Moon (Night)
    float moonDiff = max(dot(rd, moonDir), 0.0);
    float moonDisc = smoothstep(0.993, 0.995, moonDiff);
    vec3 moonVisual = C_BLOOD_RED * moonDisc * 2.5 + C_CRIMSON * pow(moonDiff, 24.0);

    skyColor += sunVisual * isDay;
    skyColor += moonVisual * isNight;

    // --- 4. 3D Raymarching Loop ---
    float t = 0.1;
    float tMax = 35.0;
    bool hit = false;
    vec3 p = ro;

    for (int i = 0; i < 70; i++) {
        p = ro + rd * t;
        float h = map(p);
        if (h < 0.01) {
            hit = true;
            break;
        }
        t += h * 0.65;
        if (t > tMax) break;
    }

    // --- 5. Surface Shading & 3D Wireframe ---
    vec3 col = skyColor;

    if (hit) {
        vec3 nor = calcNormal(p);
        vec3 lightDir = isDay > 0.5 ? sunDir : moonDir;
        float dif = clamp(dot(nor, lightDir), 0.1, 1.0);

        // Terrain color blend
        vec3 groundCol = mix(C_SICK_GREEN, C_CRIMSON * 0.7, clamp((1.0 - p.y) * 0.5, 0.0, 1.0));
        groundCol = mix(C_BG_DEEP, groundCol, isDay * 0.5 + 0.5);
        col = groundCol * dif;

        // 3D Perspective Cyber-Wireframe Grid on Terrain
        vec2 grid = abs(fract(p.xz * 1.5 - 0.5) - 0.5) / fwidth(p.xz * 1.5);
        float line = min(grid.x, grid.y);
        float gridMask = 1.0 - min(line, 1.0);
        col = mix(col, C_CYBER_ACID, gridMask * 0.85);

        // Distance fog into sky
        float fog = exp(-0.04 * t * t);
        col = mix(skyColor, col, fog);
    }

    // --- 6. CRT Scanline & Glitch Post-Processing ---
    float scanline = sin(gl_FragCoord.y * 1.4) * (0.04 + 0.04 * isNight);
    col -= scanline;

    if (glitchPulse > 0.5 && isNight > 0.5) {
        col.r += hash(vec2(u_time, uv.y)) * 0.18; // Red channel distortion
    }

    vec2 v = v_uv * (1.0 - v_uv.yx);
    float vignette = clamp(pow(v.x * v.y * 15.0, 0.35), 0.0, 1.0);
    col *= vignette;

    FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}