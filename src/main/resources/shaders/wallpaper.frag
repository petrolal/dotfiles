#version 330 core
out vec4 FragColor;

in vec2 v_uv;

uniform float u_time;
uniform float u_timeOfDay;
uniform vec2 u_resolution;
uniform vec4 u_mouse; // x, y, leftDown, rightDown

// Palette
const vec3 C_BG_DEEP    = vec3(0.04, 0.01, 0.06); // #0a0210
const vec3 C_BLOOD_RED  = vec3(0.85, 0.05, 0.20); // #d90d33
const vec3 C_CRIMSON    = vec3(0.55, 0.02, 0.15); // #8c0526
const vec3 C_PURPLE     = vec3(0.35, 0.05, 0.40); // #590d66
const vec3 C_SICK_GREEN = vec3(0.18, 0.38, 0.15); // #2e6126
const vec3 C_CYBER_ACID = vec3(0.20, 0.95, 0.30); // #33f24d (cyber acid neon)

// Iconic Windows XP Bliss Green Grass Tone
const vec3 C_BLISS_GREEN = vec3(0.22, 0.62, 0.18); 

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float hash1(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float terrainHeight(vec2 p) {
    float h = 0.0;
    mat2 rot = mat2(0.8, -0.6, 0.6, 0.8);
    vec2 q = p * 0.15;
    h += 2.2 * noise(q); q = rot * q * 2.02;
    h += 1.1 * noise(q); q = rot * q * 2.03;
    h += 0.4 * noise(q);
    return h - 1.5;
}

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

    // --- Diurnal Solar / Lunar Trajectory ---
    float sunAngle = (hour - 6.0) * (3.14159265 / 12.0);
    vec3 sunDir = normalize(vec3(cos(sunAngle), sin(sunAngle), -0.6));
    vec3 moonDir = -sunDir;

    float isDay = smoothstep(-0.1, 0.2, sunDir.y);
    float isNight = 1.0 - isDay;

    // --- Dynamic Glitch Intensity Curve by Hour of Day ---
    float midnightFactor = smoothstep(20.0, 23.0, hour) + (1.0 - smoothstep(3.5, 6.0, hour));
    float duskFactor = smoothstep(16.5, 19.5, hour) * (1.0 - smoothstep(20.5, 22.5, hour));
    float hourGlitchScale = 0.20 + 0.35 * duskFactor + 0.70 * midnightFactor; // range [0.20, 1.25]

    // --- Unpredictable Asymmetric Glitch Timing ("Randomized Sleeps") ---
    float epochDuration = mix(0.18, 0.35, isDay);
    float epoch = floor(u_time * epochDuration);
    float epochRand = hash1(epoch);
    float sleepThreshold = mix(0.40, 0.65, isDay);
    float activityGate = step(sleepThreshold, epochRand);

    float erraticRate = mix(8.0, 3.5, isDay);
    float erraticTime = u_time * (3.0 + erraticRate * hash1(floor(u_time * 1.5)));
    float microPulse = step(0.93 - (0.05 * hourGlitchScale), sin(erraticTime) * cos(erraticTime * 2.37));
    float suddenSpike = step(0.985 - (0.02 * hourGlitchScale), hash1(floor(u_time * 12.0)));
    float glitchPulse = max(microPulse * activityGate, suddenSpike) * hourGlitchScale;

    // Horizontal slice shift with scale proportional to hour of day
    float sliceBlock = floor(gl_FragCoord.y * (0.04 + 0.12 * hash1(floor(u_time * 8.0))));
    float sliceRand = hash(vec2(sliceBlock, floor(u_time * 18.0))) - 0.5;
    float sliceMagnitude = (0.015 + 0.08 * hash1(floor(u_time * 10.0))) * hourGlitchScale;
    float slice = sliceRand * sliceMagnitude * min(glitchPulse, 1.0);
    uv.x += slice;

    // Jittered Camera Parallax & Subtle Tremor on Glitch
    vec2 mNorm = clamp(u_mouse.xy / res - 0.5, vec2(-0.5), vec2(0.5));
    float quake = (hash(vec2(u_time * 30.0, gl_FragCoord.y)) - 0.5) * 0.035 * min(glitchPulse, 1.0);
    vec3 ro = vec3(mNorm.x * 0.8 + quake, 1.8 - mNorm.y * 0.4, 4.0);
    vec3 ta = vec3(mNorm.x * 0.4 - quake * 0.5, 0.5 - mNorm.y * 0.2, -4.0);
    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = normalize(cross(uu, ww));
    vec3 rd = normalize(uv.x * uu + uv.y * vv + 1.2 * ww);

    vec3 daySky = mix(vec3(0.25, 0.05, 0.35), vec3(0.65, 0.15, 0.45), max(rd.y, 0.0));
    vec3 nightSky = mix(C_BG_DEEP, C_PURPLE * 0.5, max(rd.y, 0.0));
    vec3 skyColor = mix(nightSky, daySky, isDay);

    float sunDiff = max(dot(rd, sunDir), 0.0);
    float sunDisc = smoothstep(0.996, 0.998, sunDiff);
    vec3 sunVisual = vec3(1.0, 0.9, 0.7) * sunDisc * 2.0 + vec3(1.0, 0.2, 0.4) * pow(sunDiff, 16.0);

    float moonDiff = max(dot(rd, moonDir), 0.0);
    float moonDisc = smoothstep(0.993, 0.995, moonDiff);
    vec3 moonVisual = C_BLOOD_RED * moonDisc * 2.5 + C_CRIMSON * pow(moonDiff, 24.0);

    skyColor += sunVisual * isDay;
    skyColor += moonVisual * isNight;

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

    vec3 col = skyColor;

    if (hit) {
        vec3 nor = calcNormal(p);
        vec3 lightDir = isDay > 0.5 ? sunDir : moonDir;
        float dif = clamp(dot(nor, lightDir), 0.2, 1.0);

        // --- Recognizable Windows XP Bliss Rolling Green Hills ---
        // Blend iconic vibrant Bliss green with atmospheric depth & twilight shadow
        vec3 blissTerrain = mix(C_SICK_GREEN, C_BLISS_GREEN, 0.65 + 0.35 * isDay);
        // Cast subtle shadow into valleys
        float valleyShadow = clamp((p.y + 1.2) * 0.5, 0.25, 1.0);
        blissTerrain *= valleyShadow;
        col = blissTerrain * dif;

        // --- Cyber-Acid Neon Grid Lines (Consistent Green, No Multi-Colors) ---
        // Clear, crisp wireframe so the hill contours and 3D terrain shape pop vividly
        vec2 gridCoord = p.xz * 1.5;
        vec2 grid = abs(fract(gridCoord - 0.5) - 0.5) / fwidth(gridCoord);
        float gridMask = 1.0 - min(min(grid.x, grid.y), 1.0);

        // Elevation topo lines that accentuate the undulating Bliss hill slopes
        float topo = abs(fract(p.y * 3.0) - 0.5) / fwidth(p.y * 3.0);
        float topoMask = (1.0 - min(topo, 1.0)) * 0.45;

        // Single focused neon acid-green glow that highlights the wireframe cleanly
        float totalNeonWire = clamp(gridMask * 0.9 + topoMask, 0.0, 1.0);
        col = mix(col, C_CYBER_ACID * 1.25, totalNeonWire * 0.85);

        // --- Blood Red Terror Glitches Across the Ground ---
        // 1. Sprawling geometric blood fissures tearing through the green hills
        vec2 floorTile = floor(p.xz * 1.2);
        float floorTileRand = hash(floorTile + floor(u_time * 8.0) * 11.7);
        float bloodPatchTrigger = step(0.82 - (0.28 * min(glitchPulse, 1.0)), floorTileRand);

        if (bloodPatchTrigger > 0.5) {
            float patchType = hash1(floorTileRand * 70.0);
            vec3 bloodGlitchCol = (patchType < 0.6) ? C_BLOOD_RED : C_CRIMSON;
            float veinNoise = noise(p.xz * 12.0 + u_time * 2.0);
            col = mix(col, bloodGlitchCol * 1.4, clamp(veinNoise * 1.4, 0.45, 1.0));
        }

        // 2. High-frequency blood scanline pulses ripping across terrain during active glitches
        if (glitchPulse > 0.3) {
            float floorRip = step(0.92, sin(p.z * 18.0 + u_time * 25.0) * cos(p.x * 6.0));
            col = mix(col, C_BLOOD_RED * 1.3, floorRip * min(glitchPulse, 1.0) * 0.85);
        }

        // Distance atmospheric fog into horizon
        float fog = exp(-0.035 * t * t);
        col = mix(skyColor, col, fog);
    }

    // --- CRT Scanlines ---
    float scanline = sin(gl_FragCoord.y * 1.4) * (0.03 + 0.04 * isNight * hourGlitchScale);
    col -= scanline;

    // --- Cyberpunk / Retro Terror Edge Square Glitches (Horror Palette) ---
    vec2 edgeDist = min(v_uv, 1.0 - v_uv);
    float borderReach = mix(0.12, 0.28, hourGlitchScale);
    float borderFactor = 1.0 - smoothstep(0.0, borderReach, min(edgeDist.x, edgeDist.y));

    if (borderFactor > 0.01) {
        float tileSize = mix(18.0, 28.0, midnightFactor);
        vec2 blockPos = floor(gl_FragCoord.xy / tileSize);
        float blockHash = hash(blockPos + floor(u_time * 12.0) * 19.3);
        float blockTriggerThreshold = mix(0.88, 0.55, min(glitchPulse, 1.0));
        float blockTrigger = step(blockTriggerThreshold, blockHash);

        if (blockTrigger > 0.5) {
            float blockType = hash1(blockHash * 100.0);
            vec3 glitchBlockCol;
            if (blockType < 0.35) {
                // Inverted corrupted block
                glitchBlockCol = 1.0 - col;
            } else if (blockType < 0.70) {
                // Blood Red / Crimson horror data tile
                glitchBlockCol = mix(C_BLOOD_RED, C_CRIMSON, blockType * 2.0);
            } else {
                // Crushed dark block
                glitchBlockCol = C_BG_DEEP * 1.8;
            }
            float blockOpacity = borderFactor * (0.55 + 0.45 * min(glitchPulse, 1.0));
            col = mix(col, glitchBlockCol, blockOpacity);
        }
    }

    // Vignette
    vec2 v = v_uv * (1.0 - v_uv.yx);
    float vignette = clamp(pow(v.x * v.y * 15.0, 0.35), 0.0, 1.0);
    col *= vignette;

    FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}