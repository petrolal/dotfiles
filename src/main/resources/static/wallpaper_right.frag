#version 330 core

uniform vec2 u_resolution;
uniform float u_time;
uniform vec4 u_mouse;       // xy: cursor pos, z: left click, w: right click
uniform float u_workspace;  // Virtual workspace index
uniform float u_audio;      // Media/playback indicator (0.0 or 1.0)
uniform float u_cpu;        // CPU load (0.0 to 1.0)
uniform float u_ram;        // RAM consumption (0.0 to 1.0)

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

// Scene Definition: Morgue Lockers & Specimen Incubation Chamber
float map(vec3 p) {
    hitMat = 0;
    float d = 1e5;

    // 1. Room Shell (Matching primary screen floor elevation at y = -1.5)
    float floorDist = p.y + 1.5;
    if (floorDist < d) { d = floorDist; hitMat = 1; }

    float backWall = 6.2 - p.z;
    if (backWall < d) { d = backWall; hitMat = 2; }

    float rightWall = 3.6 - p.x;
    if (rightWall < d) { d = rightWall; hitMat = 3; }

    // 2. Morgue Body Storage Vault (Center-Right Wall)
    // Wall of cold-rolled steel body lockers (3 columns x 3 rows)
    vec3 vaultP = p - vec3(1.1, -0.1, 5.8);
    float vaultCabinet = sdBox(vaultP, vec3(1.8, 1.35, 0.35));
    if (vaultCabinet < d) { d = vaultCabinet; hitMat = 4; }

    // 3. Cylindrical Specimen Incubation Vat (Left Bay)
    vec3 vatP = p - vec3(-1.8, -0.1, 4.2);
    // Base pedestal & top cap
    float vatBase = sdCylinder(vatP - vec3(0.0, -1.25, 0.0), 0.15, 0.65);
    float vatCap  = sdCylinder(vatP - vec3(0.0, 1.25, 0.0), 0.15, 0.65);
    // Glass cylinder containing preservation fluid
    float vatGlass = sdCylinder(vatP, 1.1, 0.52);
    float vatHardware = min(vatBase, vatCap);

    if (vatGlass < d) { d = vatGlass; hitMat = 10; } // Glowing fluid core
    if (vatHardware < d) { d = vatHardware; hitMat = 7; }

    // 4. Medical IV Drip Pole (Foreground Left)
    vec3 poleP = p - vec3(-0.7, -0.4, 2.2);
    float poleRod = sdCylinder(poleP, 1.05, 0.02);
    float poleBase = sdBox(poleP - vec3(0.0, -1.05, 0.0), vec3(0.2, 0.02, 0.2));
    float fluidBag = sdBox(poleP - vec3(0.08, 0.75, 0.0), vec3(0.08, 0.18, 0.04));
    float ivStand = min(min(poleRod, poleBase), fluidBag);
    if (ivStand < d) { d = ivStand; hitMat = 5; }

    // 5. Biohazard Disposal Bin (Under morgue doors)
    vec3 binP = p - vec3(-0.1, -1.15, 4.6);
    binP.xz *= rot(0.25);
    float bin = sdBox(binP, vec3(0.3, 0.35, 0.3));
    if (bin < d) { d = bin; hitMat = 6; }

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
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution.xy) / u_resolution.y;

    // Flashlight targeting tracking mouse cursor
    vec2 mouseNorm = vec2(0.0);
    if (u_resolution.x > 0.0 && u_resolution.y > 0.0) {
        mouseNorm = (u_mouse.xy / u_resolution.xy) - 0.5;
        mouseNorm.y = -mouseNorm.y;
    }

    // Camera angle looking down into the morgue/specimen bay
    vec3 ro = vec3(0.2, 0.45, 0.6);
    vec3 target = vec3(0.2 + mouseNorm.x * 0.8, -0.1 + mouseNorm.y * 0.6, 3.8);
    vec3 fwd = normalize(target - ro);
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), fwd));
    vec3 up = cross(fwd, right);
    vec3 rd = normalize(fwd + uv.x * right * 0.8 + uv.y * up * 0.8);

    // Raymarching
    float t = 0.05;
    int obj = 0;
    vec3 p;
    bool hit = false;

    for (int i = 0; i < 48; i++) {
        p = ro + rd * t;
        float dist = map(p);
        obj = hitMat;
        if (dist < 0.004) {
            hit = true;
            break;
        }
        t += dist;
        if (t > 14.0) break;
    }

    if (!hit) {
        fragColor = vec4(0.03, 0.02, 0.02, 1.0);
        return;
    }

    vec3 n = calcNormal(p);

    // Constant, stable Flashlight (Zero blinking/strobing)
    vec3 lightPos = ro + vec3(-0.1, -0.1, 0.0);
    vec3 lightDir = normalize(target - lightPos);
    vec3 toFrag = normalize(p - lightPos);
    float distToLight = length(p - lightPos);

    float spotCos = dot(lightDir, toFrag);
    float spot = smoothstep(0.70, 0.94, spotCos);

    float atten = 1.0 / (1.0 + 0.15 * distToLight + 0.08 * distToLight * distToLight);
    float diffuse = max(dot(n, -toFrag), 0.0);
    float directLight = diffuse * atten * spot * 1.35;
    float ambient = 0.06; // Calm baseline ambient

    // --- Procedural Textures & Materials ---
    vec3 albedo = vec3(0.4);

    if (obj == 1) {
        // Checkerboard Tile Floor (matches primary monitor geometry exactly)
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

        // Dripping drag trail / bloody stain on floor
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

        albedo = wallCol * mix(0.25, 1.0, grout);
    }
    else if (obj == 4) {
        // Stainless Steel Morgue Body Locker Wall
        vec2 vaultUV = (p.xy - vec2(1.1, -0.1)) * vec2(1.4, 1.8);
        vec2 cellFract = fract(vaultUV);
        
        // Drawer door seams
        float doorSeam = step(0.04, cellFract.x) * step(0.04, cellFract.y) *
                         step(cellFract.x, 0.96) * step(cellFract.y, 0.96);
        
        // Stainless steel brushed horizontal grain
        float grain = noise(p.xy * vec2(1.0, 45.0));
        vec3 steelCol = mix(vec3(0.50, 0.52, 0.54), vec3(0.68, 0.70, 0.72), grain * 0.35);

        // Rust dripping from drawer handles and card holders
        float rust = fbm(p.xy * 6.0 + vec2(1.5, 0.5));
        vec3 rustCol = vec3(0.38, 0.20, 0.12);
        steelCol = mix(steelCol, rustCol, smoothstep(0.52, 0.78, rust));

        // Chrome door pull handles
        float handle = step(abs(cellFract.x - 0.5), 0.18) * step(abs(cellFract.y - 0.5), 0.05);
        steelCol = mix(steelCol, vec3(0.85, 0.88, 0.90), handle);

        albedo = steelCol * mix(0.20, 1.0, doorSeam);
    }
    else if (obj == 5) {
        // IV Stand & Fluid Bag
        albedo = vec3(0.55, 0.58, 0.55);
    }
    else if (obj == 6) {
        // Biohazard Disposal Bin (Hazard yellow/orange paint stained with grime)
        vec3 binCol = vec3(0.72, 0.55, 0.18);
        float grime = fbm(p.xz * 8.0);
        albedo = mix(binCol, vec3(0.22, 0.16, 0.12), grime * 0.6);
    }
    else if (obj == 7) {
        // Specimen Tube Iron Caps & Hydraulic Collars
        albedo = vec3(0.28, 0.30, 0.32);
    }

    vec3 col = albedo * (ambient + directLight * 1.25);

    // 6. Glowing Biohazard Specimen Incubation Fluid
    if (obj == 10) {
        vec3 vatLocal = p - vec3(-1.8, -0.1, 4.2);
        // Continuous gentle bubbles rising in fluid
        vec2 bCoord = vatLocal.xy * vec2(4.0, 8.0) - vec2(0.0, u_time * 1.2);
        float bubbles = step(0.82, hash(floor(bCoord)));

        // Murky suspended shadow / silhouette inside
        float specimenCore = smoothstep(0.35, 0.05, length(vatLocal.xz));

        vec3 fluidGlow = vec3(0.25, 0.85, 0.40); // Formaldehyde green
        vec3 fluidCol = mix(fluidGlow * 0.5, fluidGlow * 1.1, specimenCore);
        fluidCol += vec3(0.3, 0.4, 0.2) * bubbles;

        col = fluidCol;
    }

    // Specimen tube emissive light spilling onto the floor & morgue vault
    vec3 vatWorldPos = vec3(-1.8, -0.1, 4.2);
    float distToVat = length(p - vatWorldPos);
    if (obj != 10 && distToVat < 2.5) {
        float vatSpill = (2.5 - distToVat) / 2.5;
        col += vec3(0.08, 0.32, 0.12) * vatSpill * vatSpill * 0.65;
    }

    // Distance fog
    col *= exp(-0.06 * t);

    // Identical film tone grading for multi-monitor color matching
    col = pow(col, vec3(0.92, 0.95, 1.02));

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}