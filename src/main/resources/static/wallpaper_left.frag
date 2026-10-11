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

// Multi-octave dirt/grime noise
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

// Scene Definition matching the medical horror room
float map(vec3 p) {
    hitMat = 0;
    float d = 1e5;

    // 1. Room Shell (Floor y=-1.5, Back wall z=6.5, Left wall x=-3.2)
    float floorDist = p.y + 1.5;
    if (floorDist < d) { d = floorDist; hitMat = 1; }

    float backWall = 6.5 - p.z;
    if (backWall < d) { d = backWall; hitMat = 2; }

    float leftWall = p.x + 3.2;
    if (leftWall < d) { d = leftWall; hitMat = 3; }

    // 2. Central Anesthesia / Medical Monitoring Machine
    vec3 machP = p - vec3(0.0, -0.6, 3.4);
    
    // Base Cabinet / Cart
    float cart = sdBox(machP - vec3(0.0, -0.35, 0.0), vec3(0.55, 0.55, 0.45));
    
    // Slanted Front Panel
    vec3 panelP = machP - vec3(0.0, 0.15, -0.05);
    panelP.yz *= rot(0.18);
    float panel = sdBox(panelP, vec3(0.52, 0.22, 0.42));
    float body = min(cart, panel);

    // CRT Monitor on top
    vec3 monP = machP - vec3(-0.05, 0.6, 0.0);
    float monitor = sdBox(monP, vec3(0.48, 0.28, 0.35));
    float machine = min(body, monitor);

    // CRT Screen Face (Inset)
    vec3 screenP = monP - vec3(-0.12, 0.02, -0.36);
    float screen = sdBox(screenP, vec3(0.26, 0.18, 0.03));
    if (screen < d) { d = screen; hitMat = 10; }

    if (machine < d) { d = machine; hitMat = 4; }

    // Dials / Gauge Knobs on slanted panel
    vec3 knobP1 = panelP - vec3(-0.25, 0.0, -0.44);
    vec3 knobP2 = panelP - vec3(0.0, 0.0, -0.44);
    vec3 knobP3 = panelP - vec3(0.25, 0.0, -0.44);
    float knob = min(sdCylinder(knobP1.xzy, 0.04, 0.09),
                 min(sdCylinder(knobP2.xzy, 0.04, 0.09),
                     sdCylinder(knobP3.xzy, 0.04, 0.09)));
    if (knob < d) { d = knob; hitMat = 6; }

    // Gas / Oxygen Cylinders mounted to the cart sides
    vec3 tankL = machP - vec3(-0.72, -0.3, 0.0);
    vec3 tankR = machP - vec3(0.72, -0.3, 0.0);
    float tanks = min(sdCylinder(tankL, 0.55, 0.15), sdCylinder(tankR, 0.55, 0.15));
    if (tanks < d) { d = tanks; hitMat = 7; }

    // 3. Autopsy / Stretcher Examination Bed (Right Background)
    vec3 bedP = p - vec3(1.9, -0.85, 4.8);
    bedP.xz *= rot(-0.08);
    float bedPad = sdBox(bedP, vec3(0.65, 0.1, 1.4));
    float bedFrame = sdBox(bedP - vec3(0.0, -0.35, 0.0), vec3(0.6, 0.25, 1.35));
    float bed = min(bedPad, bedFrame);
    if (bed < d) { d = bed; hitMat = 5; }

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

    // Flashlight aim direction linked smoothly to mouse cursor
    vec2 mouseNorm = vec2(0.0);
    if (u_resolution.x > 0.0 && u_resolution.y > 0.0) {
        mouseNorm = (u_mouse.xy / u_resolution.xy) - 0.5;
        mouseNorm.y = -mouseNorm.y;
    }

    // Camera view
    vec3 ro = vec3(0.0, 0.45, 0.7);
    vec3 target = vec3(mouseNorm.x * 0.8, -0.1 + mouseNorm.y * 0.6, 3.4);
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

    // Steady, stable Flashlight (Zero flickering/strobing)
    vec3 lightPos = ro + vec3(0.1, -0.1, 0.0);
    vec3 lightDir = normalize(target - lightPos);
    vec3 toFrag = normalize(p - lightPos);
    float distToLight = length(p - lightPos);

    // Soft spotlight cone
    float spotCos = dot(lightDir, toFrag);
    float spot = smoothstep(0.70, 0.94, spotCos);

    float atten = 1.0 / (1.0 + 0.15 * distToLight + 0.08 * distToLight * distToLight);
    float diffuse = max(dot(n, -toFrag), 0.0);
    float directLight = diffuse * atten * spot * 1.35; // Constant solid brightness
    float ambient = 0.06; // Raised slightly so background is comfortably visible

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

        float bloodMask = smoothstep(0.68, 0.85, fbm(p.xz * 4.0 + vec2(1.2, 3.4)));
        vec3 bloodCol = vec3(0.38, 0.03, 0.02);
        tileColor = mix(tileColor, bloodCol, bloodMask);

        albedo = tileColor * mix(0.18, 1.0, grout);
    }
    else if (obj == 2 || obj == 3) {
        // Tiled Hospital Wall
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
        // Medical Machine Enclosure
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
        // Dial Knobs
        albedo = vec3(0.22, 0.24, 0.22);
    }
    else if (obj == 7) {
        // Oxygen Tanks
        float scratches = noise(p.xy * vec2(2.0, 30.0));
        albedo = mix(vec3(0.42, 0.44, 0.42), vec3(0.65), scratches * 0.35);
    }

    vec3 col = albedo * (ambient + directLight * 1.25);

    // Constant, stable CRT Monitor Screen
    if (obj == 10) {
        vec2 scrUV = p.xy - vec3(-0.05, 0.6, 0.0).xy;
        // Smooth, constant ECG pulse line
        float heartX = fract(scrUV.x * 2.0 - u_time * 0.35);
        float beat = sin(heartX * 25.0) * exp(-abs(heartX - 0.5) * 12.0) * 0.08;
        float ecg = smoothstep(0.015, 0.002, abs(scrUV.y - beat));

        vec3 phosphor = vec3(0.72, 0.98, 0.70);
        vec3 screenCol = phosphor * (0.85 + ecg * 0.45);
        screenCol *= mix(0.85, 1.0, step(0.3, fract(p.y * 120.0)));
        col = screenCol;
    }

    // Steady green emissive spill from monitor
    vec3 screenWorldPos = vec3(-0.05, 0.6, 3.05);
    float distToScreen = length(p - screenWorldPos);
    if (obj != 10 && distToScreen < 1.2) {
        float glowAtten = (1.2 - distToScreen) / 1.2;
        col += vec3(0.12, 0.35, 0.15) * glowAtten * glowAtten * 0.6;
    }

    // Gentle distance fog
    col *= exp(-0.06 * t);

    // Warm, steady horror tone mapping (no noisy grain/strobe)
    col = pow(col, vec3(0.92, 0.95, 1.02));

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}