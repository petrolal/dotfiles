#version 330 core

uniform vec2 u_resolution;     // Full multi-monitor desktop resolution (shared panorama uv space)
uniform float u_time;
uniform vec4 u_mouse;          // xy: global cursor pos, z: left click, w: right click
uniform float u_workspace;     // Virtual workspace index
uniform float u_audio;         // Media/playback indicator (0.0 or 1.0)
uniform float u_cpu;           // CPU load (0.0 to 1.0)
uniform float u_ram;           // RAM consumption (0.0 to 1.0)
uniform vec2 u_viewportOffset; // This monitor's pixel offset within the full desktop (bottom-left origin)

out vec4 fragColor;

// 2D Rotation matrix
mat2 rot(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}

// Pseudo-random noise functions
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    v += 0.500 * noise(p); p *= 2.02;
    v += 0.250 * noise(p); p *= 2.03;
    v += 0.125 * noise(p);
    return v;
}

// SDF Primitives
float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdCylinder(vec3 p, float h, float r) {
    vec2 d = abs(vec2(length(p.xz), p.y)) - vec2(r, h);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

int hitMat = 0;

// Scene Definition: one continuous morgue/examination room spanning the whole
// panorama. Left-hand furniture (anesthesia machine, autopsy bed) sits toward
// -x, the body-locker vault and specimen vat toward +x, with the desk/PC
// shared near the back wall straddling the seam between monitors.
float map(vec3 p) {
    hitMat = 0;
    float d = 1e5;

    // 1. Room Shell
    float floorDist = p.y + 1.5;
    if (floorDist < d) { d = floorDist; hitMat = 1; }

    float backWall = 11.0 - p.z;
    if (backWall < d) { d = backWall; hitMat = 2; }

    float leftWall = p.x + 5.5;
    if (leftWall < d) { d = leftWall; hitMat = 3; }

    float rightWall = 5.5 - p.x;
    if (rightWall < d) { d = rightWall; hitMat = 3; }

    // 2. Medical Anesthesia Machine (far left)
    vec3 machP = p - vec3(-3.9, -0.6, 6.2);
    float machRegion = sdBox(machP, vec3(1.2, 1.1, 0.9));
    if (machRegion - 0.2 < d) {
        machP.xz *= rot(-0.25);

        float cart = sdBox(machP - vec3(0.0, -0.35, 0.0), vec3(0.55, 0.55, 0.45));
        vec3 panelP = machP - vec3(0.0, 0.15, -0.05);
        panelP.yz *= rot(0.18);
        float panel = sdBox(panelP, vec3(0.52, 0.22, 0.42));
        float body = min(cart, panel);

        vec3 monP = machP - vec3(-0.05, 0.6, 0.0);
        float monitor = sdBox(monP, vec3(0.48, 0.28, 0.35));
        float machine = min(body, monitor);

        vec3 screenP = monP - vec3(-0.12, 0.02, -0.36);
        float screen = sdBox(screenP, vec3(0.26, 0.18, 0.03));
        if (screen < d) { d = screen; hitMat = 10; }

        if (machine < d) { d = machine; hitMat = 4; }

        vec3 knobP1 = panelP - vec3(-0.25, 0.0, -0.44);
        vec3 knobP2 = panelP - vec3(0.0, 0.0, -0.44);
        vec3 knobP3 = panelP - vec3(0.25, 0.0, -0.44);
        float knob = min(sdCylinder(knobP1.xzy, 0.04, 0.09),
                     min(sdCylinder(knobP2.xzy, 0.04, 0.09),
                         sdCylinder(knobP3.xzy, 0.04, 0.09)));
        if (knob < d) { d = knob; hitMat = 6; }

        vec3 tankL = machP - vec3(-0.72, -0.3, 0.0);
        vec3 tankR = machP - vec3(0.72, -0.3, 0.0);
        float tanks = min(sdCylinder(tankL, 0.55, 0.15), sdCylinder(tankR, 0.55, 0.15));
        if (tanks < d) { d = tanks; hitMat = 7; }
    } else {
        d = min(d, machRegion);
    }

    // 3. Autopsy Examination Bed (left-of-center)
    vec3 bedP = p - vec3(-0.8, -0.85, 7.8);
    float bedRegion = sdBox(bedP, vec3(0.9, 0.6, 1.6));
    if (bedRegion - 0.2 < d) {
        bedP.xz *= rot(-0.12);
        float bedPad = sdBox(bedP, vec3(0.65, 0.1, 1.4));
        float bedFrame = sdBox(bedP - vec3(0.0, -0.35, 0.0), vec3(0.6, 0.25, 1.35));
        float bed = min(bedPad, bedFrame);
        if (bed < d) { d = bed; hitMat = 5; }
    } else {
        d = min(d, bedRegion);
    }

    // 4. Center Office Desk with Retro White PC (shared seam, back of room)
    vec3 deskP = p - vec3(0.26, -0.75, 9.2);

    float deskRegion = sdBox(deskP - vec3(0.0, 0.0, 0.0), vec3(1.3, 0.9, 0.8));
    if (deskRegion - 0.25 < d) {
        float deskTop = sdBox(deskP - vec3(0.0, 0.0, 0.0), vec3(1.15, 0.04, 0.65));
        float deskLegL = sdBox(deskP - vec3(-1.05, -0.36, 0.0), vec3(0.05, 0.34, 0.55));
        float deskLegR = sdBox(deskP - vec3(1.05, -0.36, 0.0), vec3(0.05, 0.34, 0.55));
        float deskBack = sdBox(deskP - vec3(0.0, -0.30, 0.45), vec3(1.05, 0.28, 0.03));
        float desk = min(min(deskTop, min(deskLegL, deskLegR)), deskBack);
        if (desk < d) { d = desk; hitMat = 8; }

        vec3 pcMonP = deskP - vec3(-0.15, 0.44, 0.10);
        float pcBezel = sdBox(pcMonP, vec3(0.44, 0.34, 0.36));
        float pcBase = sdBox(pcMonP - vec3(0.0, -0.36, 0.0), vec3(0.24, 0.04, 0.24));
        float pcNeck = sdCylinder(pcMonP - vec3(0.0, -0.32, 0.0), 0.04, 0.08);
        float pcMonitorHousing = min(pcBezel, min(pcBase, pcNeck));

        vec3 pcScrP = pcMonP - vec3(0.0, 0.0, -0.365);
        float pcScreen = sdBox(pcScrP, vec3(0.35, 0.26, 0.02));
        if (pcScreen < d) { d = pcScreen; hitMat = 11; }

        vec3 towerP = deskP - vec3(0.70, 0.26, 0.08);
        float tower = sdBox(towerP, vec3(0.24, 0.22, 0.46));

        vec3 kbP = deskP - vec3(-0.15, 0.055, -0.32);
        float keyboard = sdBox(kbP, vec3(0.42, 0.015, 0.15));

        vec3 mouseP = deskP - vec3(0.45, 0.055, -0.30);
        float pcMouse = sdBox(mouseP, vec3(0.05, 0.015, 0.08));

        float retroHardware = min(min(pcMonitorHousing, tower), min(keyboard, pcMouse));
        if (retroHardware < d) { d = retroHardware; hitMat = 9; }
    } else {
        d = min(d, deskRegion);
    }

    // 5-8. Right-hand cluster: morgue vault, specimen vat, IV pole, biohazard
    // bin. Bounding-box culled as one group (like the machine/bed/desk above)
    // so the raymarch only pays for these ~9 sub-primitives when the ray is
    // actually near them, instead of on every step across the whole room.
    vec3 clusterP = p - vec3(2.7, 0.0, 5.4);
    float clusterRegion = sdBox(clusterP, vec3(2.3, 1.7, 2.5));
    if (clusterRegion - 0.3 < d) {
        // 5. Morgue Body Locker Vault wall (right-of-center)
        vec3 vaultP = p - vec3(3.1, -0.1, 7.4);
        float vaultCabinet = sdBox(vaultP, vec3(1.8, 1.35, 0.35));
        if (vaultCabinet < d) { d = vaultCabinet; hitMat = 12; }

        // 6. Cylindrical Specimen Incubation Vat (right, glowing lantern effect)
        vec3 vatP = p - vec3(1.3, -0.1, 5.6);
        float vatBase = sdCylinder(vatP - vec3(0.0, -1.25, 0.0), 0.15, 0.65);
        float vatCap  = sdCylinder(vatP - vec3(0.0, 1.25, 0.0), 0.15, 0.65);
        float vatGlass = sdCylinder(vatP, 1.1, 0.52);
        float vatHardware = min(vatBase, vatCap);

        if (vatGlass < d) { d = vatGlass; hitMat = 13; } // Glowing fluid core
        if (vatHardware < d) { d = vatHardware; hitMat = 7; }

        // 7. Medical IV Drip Pole (far right foreground)
        vec3 poleP = p - vec3(2.9, -0.4, 4.0);
        float poleRod = sdCylinder(poleP, 1.05, 0.02);
        float poleBase = sdBox(poleP - vec3(0.0, -1.05, 0.0), vec3(0.2, 0.02, 0.2));
        float fluidBag = sdBox(poleP - vec3(0.08, 0.75, 0.0), vec3(0.08, 0.18, 0.04));
        float ivStand = min(min(poleRod, poleBase), fluidBag);
        if (ivStand < d) { d = ivStand; hitMat = 14; }

        // 8. Biohazard Disposal Bin (right, near vault)
        vec3 binP = p - vec3(2.3, -1.15, 6.2);
        binP.xz *= rot(0.25);
        float bin = sdBox(binP, vec3(0.3, 0.35, 0.3));
        if (bin < d) { d = bin; hitMat = 15; }
    } else {
        d = min(d, clusterRegion);
    }

    return d;
}

vec3 calcNormal(vec3 p) {
    vec2 e = vec2(0.003, 0.0);
    return normalize(vec3(
        map(p + e.xyy) - map(p - e.xyy),
        map(p + e.yxy) - map(p - e.yxy),
        map(p + e.yyx) - map(p - e.yyx)
    ));
}

void main() {
    // Each monitor now has its own window, so gl_FragCoord is local to it
    // (0..monitor width/height). Add this monitor's desktop position back in
    // to reconstruct one continuous global pixel coordinate across every
    // monitor -- that's what keeps the single wide panorama camera seamless
    // at the bezel, independent of each monitor's own refresh rate.
    vec2 globalPx = gl_FragCoord.xy + u_viewportOffset;

    // Chunky low-res PS1-style pixelation: snap to a block grid *before*
    // generating the camera ray, so every pixel in a block raymarches the
    // identical ray and gets the identical color -- a true blocky low
    // internal resolution, not just a blur or filter over the full-res image.
    const float pixelBlock = 2.0;
    vec2 blockPx = (floor(globalPx / pixelBlock) + 0.5) * pixelBlock;

    vec2 uv = (blockPx - 0.5 * u_resolution) / u_resolution.y;

    // One wide panorama camera shared by the whole desktop, pulled back and
    // with a narrower lens so the room reads as larger/more distant instead
    // of feeling cramped up against the furniture.
    vec3 ro = vec3(0.0, 0.65, -1.2);
    vec3 target = vec3(0.2, -0.15, 8.5);
    vec3 fwd = normalize(target - ro);
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), fwd));
    vec3 up = cross(fwd, right);
    vec3 rd = normalize(fwd + uv.x * right * 0.62 + uv.y * up * 0.62);

    // Raymarching
    float t = 0.05;
    int obj = 0;
    vec3 p;
    bool hit = false;

    for (int i = 0; i < 70; i++) {
        p = ro + rd * t;
        float dist = map(p);
        obj = hitMat;
        if (dist < 0.004) {
            hit = true;
            break;
        }
        t += dist;
        if (t > 24.0) break;
    }

    if (!hit) {
        fragColor = vec4(0.03, 0.02, 0.02, 1.0);
        return;
    }

    vec3 n = calcNormal(p);

    // Dynamic Flashlight: aims smoothly with cursor position (global desktop
    // space, consistent across all monitors) or hovers gently when idle.
    vec2 normMouse = (u_mouse.x >= 0.0) ? (u_mouse.xy / u_resolution.xy - 0.5) : vec2(sin(u_time * 0.4) * 0.15, cos(u_time * 0.3) * 0.1);
    vec3 lightPos = ro + vec3(-0.1, -0.1, 0.0);
    vec3 aimTarget = target + vec3(normMouse.x * 2.2, -normMouse.y * 1.8, 0.0);
    vec3 lightDir = normalize(aimTarget - lightPos);
    vec3 toFrag = normalize(p - lightPos);
    float distToLight = length(p - lightPos);

    float spotCos = dot(lightDir, toFrag);
    float spot = smoothstep(0.65, 0.94, spotCos);

    float atten = 1.0 / (1.0 + 0.12 * distToLight + 0.05 * distToLight * distToLight);
    // Quantized into a handful of flat bands instead of a smooth gradient --
    // PS1 had no per-pixel lighting, just flat-shaded low-poly facets, so a
    // stepped diffuse term reads as that same blocky, faceted look.
    float diffuseRaw = max(dot(n, -toFrag), 0.0);
    float diffuse = floor(diffuseRaw * 5.0) / 5.0;
    float directLight = diffuse * atten * spot * 1.8;
    float ambient = 0.18;

    // --- Procedural Textures & Materials ---
    vec3 albedo = vec3(0.4);

    if (obj == 1) {
        // Checkerboard Tile Floor
        vec2 tileUV = p.xz * 1.05;
        vec2 tileID = floor(tileUV);
        vec2 tileFract = fract(tileUV);
        float check = mod(tileID.x + tileID.y, 2.0);

        float grout = step(0.03, tileFract.x) * step(0.03, tileFract.y) *
                      step(tileFract.x, 0.97) * step(tileFract.y, 0.97);

        vec3 colLight = vec3(0.78, 0.74, 0.65);
        vec3 colDark  = vec3(0.24, 0.26, 0.22);
        vec3 tileColor = mix(colDark, colLight, check);

        float dirt = fbm(p.xz * 3.5);
        tileColor = mix(tileColor, vec3(0.18, 0.14, 0.10), dirt * 0.65);

        float bloodMask = smoothstep(0.70, 0.86, fbm(p.xz * 3.8 + vec2(4.1, -1.8)));
        vec3 bloodCol = vec3(0.36, 0.03, 0.02);
        tileColor = mix(tileColor, bloodCol, bloodMask);

        albedo = tileColor * mix(0.18, 1.0, grout);
    }
    else if (obj == 2 || obj == 3) {
        // Hospital Plaster & Grout Tile Wall
        vec2 wallUV = (obj == 2) ? p.xy : p.zy;
        wallUV *= 2.2;
        vec2 wFract = fract(wallUV);
        float grout = step(0.04, wFract.x) * step(0.04, wFract.y);

        vec3 wallCol = vec3(0.70, 0.67, 0.60);
        float stains = fbm(p.xy * 2.5);
        wallCol = mix(wallCol, vec3(0.32, 0.24, 0.18), stains * 0.7);

        float wallBlood = smoothstep(0.72, 0.88, fbm(p.xy * 3.2 - vec2(0.5, 1.2)));
        wallCol = mix(wallCol, vec3(0.42, 0.04, 0.03), wallBlood);

        albedo = wallCol * mix(0.25, 1.0, grout);
    }
    else if (obj == 4) {
        // Medical Machine Enclosure (Aged metal with rust)
        vec3 metalCol = vec3(0.62, 0.60, 0.54);
        float rust = fbm(p.xy * 7.0 + p.z * 4.0);
        vec3 rustCol = vec3(0.38, 0.20, 0.12);
        albedo = mix(metalCol, rustCol, smoothstep(0.45, 0.75, rust));
    }
    else if (obj == 5) {
        // Autopsy Bed
        if (p.y > -0.85) {
            vec3 mattress = vec3(0.48, 0.44, 0.38);
            float stain = fbm(p.xz * 3.0);
            albedo = mix(mattress, vec3(0.22, 0.16, 0.12), stain * 0.6);
        } else {
            albedo = vec3(0.20, 0.19, 0.18);
        }
    }
    else if (obj == 6) {
        // Machine Dial Knobs
        albedo = vec3(0.22, 0.24, 0.22);
    }
    else if (obj == 7) {
        // Specimen Tube Iron Caps & Hydraulic Collars / Oxygen Gas Cylinders
        float scratches = noise(p.xy * vec2(2.0, 30.0));
        albedo = mix(vec3(0.42, 0.44, 0.42), vec3(0.65), scratches * 0.35);
    }
    else if (obj == 8) {
        // Dark Laminate Rear Desk
        vec3 woodCol = vec3(0.42, 0.36, 0.30);
        float woodGrain = noise(p.xz * vec2(22.0, 2.0));
        woodCol = mix(woodCol, vec3(0.28, 0.22, 0.18), woodGrain * 0.5);
        float stains = fbm(p.xz * 5.0);
        albedo = mix(woodCol, vec3(0.18, 0.14, 0.12), stains * 0.45);
    }
    else if (obj == 9) {
        // Aged 90s Off-White / Beige PC Hardware
        vec3 beigeCol = vec3(0.85, 0.82, 0.72);
        float grime = fbm(p.xy * 12.0 + p.z * 10.0);
        vec3 darkGrime = vec3(0.45, 0.42, 0.34);
        albedo = mix(beigeCol, darkGrime, grime * 0.42);
    }
    else if (obj == 12) {
        // Stainless Steel Morgue Body Locker Wall
        vec2 vaultUV = (p.xy - vec2(3.1, -0.1)) * vec2(1.4, 1.8);
        vec2 cellFract = fract(vaultUV);

        float doorSeam = step(0.04, cellFract.x) * step(0.04, cellFract.y) *
                         step(cellFract.x, 0.96) * step(cellFract.y, 0.96);

        float grain = noise(p.xy * vec2(1.0, 45.0));
        vec3 steelCol = mix(vec3(0.50, 0.52, 0.54), vec3(0.68, 0.70, 0.72), grain * 0.35);

        float rust = fbm(p.xy * 6.0 + vec2(1.5, 0.5));
        vec3 rustCol = vec3(0.38, 0.20, 0.12);
        steelCol = mix(steelCol, rustCol, smoothstep(0.52, 0.78, rust));

        float handle = step(abs(cellFract.x - 0.5), 0.18) * step(abs(cellFract.y - 0.5), 0.05);
        steelCol = mix(steelCol, vec3(0.85, 0.88, 0.90), handle);

        albedo = steelCol * mix(0.20, 1.0, doorSeam);
    }
    else if (obj == 14) {
        // IV Stand & Fluid Bag
        albedo = vec3(0.55, 0.58, 0.55);
    }
    else if (obj == 15) {
        // Biohazard Disposal Bin (Hazard yellow/orange paint stained with grime)
        vec3 binCol = vec3(0.72, 0.55, 0.18);
        float grime = fbm(p.xz * 8.0);
        albedo = mix(binCol, vec3(0.22, 0.16, 0.12), grime * 0.6);
    }

    vec3 col = albedo * (ambient + directLight * 1.25);

    // Front-Facing Medical CRT Screen (anesthesia machine)
    if (obj == 10) {
        vec3 machScreenOrigin = vec3(-3.9, -0.6, 6.2);
        vec3 localP = p - machScreenOrigin;
        localP.xz *= rot(-0.25);

        vec2 scrUV = localP.xy - vec2(-0.17, 0.62);
        float heartX = fract(scrUV.x * 2.0 - u_time * 0.35);
        float beat = sin(heartX * 25.0) * exp(-abs(heartX - 0.5) * 12.0) * 0.08;
        float ecg = smoothstep(0.015, 0.002, abs(scrUV.y - beat));

        vec3 phosphor = vec3(0.72, 0.98, 0.70);
        vec3 screenCol = phosphor * (0.85 + ecg * 0.45);
        screenCol *= mix(0.85, 1.0, step(0.3, fract(p.y * 120.0)));
        col = screenCol;
    }

    // Rear Retro PC Amber/DOS Terminal Screen (shared desk)
    if (obj == 11) {
        vec3 pcScreenPos = vec3(0.11, -0.31, 9.28);
        vec2 scrUV = (p.xy - pcScreenPos.xy);

        float textLines = step(0.45, fract(scrUV.y * 24.0)) * step(0.12, hash(floor(scrUV * vec2(2.0, 24.0))));
        float cursorBlink = step(0.5, fract(u_time * 1.5));
        float cursorBox = step(abs(scrUV.x - 0.10), 0.022) * step(abs(scrUV.y + 0.04), 0.015) * cursorBlink;

        vec3 amberCol = vec3(1.0, 0.72, 0.20);
        vec3 screenCol = mix(vec3(0.08, 0.05, 0.02), amberCol, clamp(textLines + cursorBox, 0.0, 1.0));
        screenCol *= mix(0.80, 1.0, step(0.35, fract(p.y * 130.0)));
        col = screenCol;
    }

    // Glowing Biohazard Specimen Incubation Fluid (lantern effect)
    if (obj == 13) {
        vec3 vatLocal = p - vec3(1.3, -0.1, 5.6);
        vec2 bCoord = vatLocal.xy * vec2(4.0, 8.0) - vec2(0.0, u_time * 1.2);
        float bubbles = step(0.82, hash(floor(bCoord)));

        float specimenCore = smoothstep(0.35, 0.05, length(vatLocal.xz));

        float pulse = 0.95 + 0.12 * sin(u_time * 2.0) + u_audio * 0.35;
        vec3 fluidGlow = vec3(0.25, 0.88, 0.42) * pulse; // Formaldehyde neon green
        vec3 fluidCol = mix(fluidGlow * 0.5, fluidGlow * 1.15, specimenCore);
        fluidCol += vec3(0.3, 0.4, 0.2) * bubbles;

        col = fluidCol;
    }

    // Emissive Spill from Medical Screen
    vec3 medScreenWorld = vec3(-3.9, 0.0, 6.2);
    float distToMedScreen = length(p - medScreenWorld);
    if (obj != 10 && distToMedScreen < 1.6) {
        float medGlow = (1.6 - distToMedScreen) / 1.6;
        col += vec3(0.12, 0.35, 0.15) * medGlow * medGlow * 0.65;
    }

    // Emissive Amber Spill from Rear PC Screen
    vec3 pcScreenWorld = vec3(0.11, -0.31, 9.28);
    float distToPcScreen = length(p - pcScreenWorld);
    if (obj != 11 && distToPcScreen < 1.4) {
        float pcGlow = (1.4 - distToPcScreen) / 1.4;
        col += vec3(0.42, 0.22, 0.04) * pcGlow * pcGlow * 0.75;
    }

    // Specimen tube emissive light spilling onto the floor & morgue vault
    vec3 vatWorldPos = vec3(1.3, -0.1, 5.6);
    float distToVat = length(p - vatWorldPos);
    if (obj != 13 && distToVat < 2.5) {
        float vatSpill = (2.5 - distToVat) / 2.5;
        float pulse = 0.95 + 0.12 * sin(u_time * 2.0) + u_audio * 0.35;
        col += vec3(0.08, 0.34, 0.14) * vatSpill * vatSpill * 0.70 * pulse;
    }

    // Distance fog
    col *= exp(-0.035 * t);

    // --- Post-process only (room/materials/lighting above are untouched):
    // PS1-era retro look (Buckshot Roulette's style) -- the chunky low-res
    // pixelation already happened above by snapping the camera ray itself to
    // blockPx; this is just the classic ordered-dither color banding that
    // comes with it, kept gentle so it doesn't darken or muddy the image.

    // 4x4 Bayer ordered-dither matrix, indexed by pixel BLOCK (not raw pixel)
    // so the dither pattern matches the chunky resolution instead of fighting it.
    int bx = int(mod(blockPx.x / pixelBlock, 4.0));
    int by = int(mod(blockPx.y / pixelBlock, 4.0));
    float bayer[16] = float[16](
         0.0,  8.0,  2.0, 10.0,
        12.0,  4.0, 14.0,  6.0,
         3.0, 11.0,  1.0,  9.0,
        15.0,  7.0, 13.0,  5.0
    );
    float dither = (bayer[by * 4 + bx] + 0.5) / 16.0 - 0.5;

    // Limited color depth per channel, broken up by the dither pattern
    // instead of flat banding -- the defining PS1 look, without crushing
    // brightness the way a contrast curve or vignette would.
    float levels = 20.0;
    col = floor(col * levels + dither + 0.5) / levels;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
