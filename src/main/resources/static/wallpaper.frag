#version 330 core

uniform vec2 u_resolution;      // Full multi-monitor desktop resolution (shared panorama space)
uniform float u_time;
uniform vec4 u_mouse;           // xy: global cursor pos, z: left click, w: right click
uniform float u_workspace;      // Virtual workspace index
uniform float u_audio;          // Media/playback indicator
uniform float u_cpu;            // CPU load (0.0 to 1.0)
uniform float u_ram;            // RAM consumption (0.0 to 1.0)
uniform vec2 u_viewportOffset;  // This monitor's pixel offset within full desktop

in vec2 v_uv;
out vec4 fragColor;

// 4x4 Bayer ordered-dither matrix (constant file-scope to eliminate per-fragment allocation)
const float bayer[16] = float[16](
     0.0,  8.0,  2.0, 10.0,
    12.0,  4.0, 14.0,  6.0,
     3.0, 11.0,  1.0,  9.0,
    15.0,  7.0, 13.0,  5.0
);

// 2D Rotation matrix
mat2 rot(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}

// Pseudo-random noise functions
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float hash3D(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
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

float sdSphere(vec3 p, float s) {
    return length(p) - s;
}

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / k1;
}

int hitMat = 0;

// Scene SDF: Oppressive Bio-Horror Laboratory Examination Room
float map(vec3 p) {
    hitMat = 0;
    float d = 1e5;

    // 1. Room Shell (Heavy, claustrophobic bunker walls & degraded tiled floor)
    float floorDist = p.y + 1.5;
    if (floorDist < d) { d = floorDist; hitMat = 1; }

    float backWall = 11.0 - p.z;
    if (backWall < d) { d = backWall; hitMat = 2; }

    float leftWall = p.x + 5.5;
    if (leftWall < d) { d = leftWall; hitMat = 3; }

    float rightWall = 5.5 - p.x;
    if (rightWall < d) { d = rightWall; hitMat = 3; }

    // 2. Overhead Infrastructure (Ceiling Pipes, Catenary Cables, Light Fixture)
    // Strictly spatially bounded above y > 0.85 to keep ground-level rays fast
    if (p.y > 0.85) {
        // Main overhead heavy conduit along X at back ceiling
        vec3 pipe1P = p - vec3(0.0, 1.82, 8.5);
        float pipe1 = sdCylinder(vec3(pipe1P.y, pipe1P.z, pipe1P.x), 5.5, 0.085);
        float collarX = mod(p.x + 3.0, 2.5) - 1.25;
        float pipe1Collar = sdCylinder(vec3(pipe1P.y, pipe1P.z, collarX), 0.05, 0.11);
        float conduitSystem = min(pipe1, pipe1Collar);

        // Secondary chemical supply line along X (mid-ceiling)
        vec3 pipe2P = p - vec3(0.0, 1.95, 5.8);
        float pipe2 = sdCylinder(vec3(pipe2P.y, pipe2P.z, pipe2P.x), 5.5, 0.055);
        conduitSystem = min(conduitSystem, pipe2);

        // Cross-conduit along Z on left side
        vec3 pipeZP = p - vec3(-2.8, 1.88, 7.5);
        float pipeZ = sdCylinder(vec3(pipeZP.x, pipeZP.y, pipeZP.z), 3.5, 0.045);
        conduitSystem = min(conduitSystem, pipeZ);

        if (conduitSystem < d) { d = conduitSystem; hitMat = 16; }

        // Hanging Catenary High-Voltage Power Cables (Parabolic Sag: y = y0 - sag * (1 - x^2))
        // Cable 1: Drooping from (-4.2, 1.85, 6.6) across to (1.5, 1.85, 6.6)
        if (p.x >= -4.2 && p.x <= 1.5) {
            float ux = (p.x - (-1.35)) / 2.85;
            float sagY = 1.85 - 0.38 * (1.0 - ux * ux);
            float cable1 = length(vec2(p.y - sagY, p.z - 6.6)) - 0.024;
            if (cable1 < d) { d = cable1; hitMat = 17; }
        }

        // Cable 2: Drooping from (-1.0, 1.88, 5.2) across to (3.8, 1.88, 5.2)
        if (p.x >= -1.0 && p.x <= 3.8) {
            float ux = (p.x - 1.4) / 2.4;
            float sagY = 1.88 - 0.32 * (1.0 - ux * ux);
            float cable2 = length(vec2(p.y - sagY, p.z - 5.2)) - 0.020;
            if (cable2 < d) { d = cable2; hitMat = 17; }
        }

        // Vertical supply cable down left wall to Anesthesia machine
        vec3 drop1P = p - vec3(-3.85, 1.1, 6.2);
        float drop1 = sdCylinder(drop1P, 0.75, 0.02);
        if (drop1 < d) { d = drop1; hitMat = 17; }

        // Overhead Industrial Fluorescent Fixture
        vec3 fixtureP = p - vec3(-0.3, 1.76, 6.8);
        float fixtureHousing = sdBox(fixtureP, vec3(1.15, 0.05, 0.22));
        float fixtureStemL = sdCylinder(p - vec3(-1.1, 1.95, 6.8), 0.15, 0.015);
        float fixtureStemR = sdCylinder(p - vec3(0.5, 1.95, 6.8), 0.15, 0.015);
        float fixture = min(fixtureHousing, min(fixtureStemL, fixtureStemR));
        if (fixture < d) { d = fixture; hitMat = 18; }

        // Fluorescent glass tube diffuser
        vec3 tubeP = fixtureP - vec3(0.0, -0.06, 0.0);
        float fluorTube = sdCylinder(vec3(tubeP.y, tubeP.z, tubeP.x), 1.0, 0.035);
        if (fluorTube < d) { d = fluorTube; hitMat = 19; }
    }

    // 3. Floor Clutter: Thick rubber drainage & hydraulic hoses
    if (p.y < -1.25) {
        // Hose snaking along morgue vault base
        vec3 hose1P = p - vec3(3.0, -1.45, 6.6);
        float zHose = sin(hose1P.x * 2.0) * 0.18;
        float hose1 = length(vec2(hose1P.z - zHose, hose1P.y)) - 0.038;
        if (hose1 < d) { d = hose1; hitMat = 17; }

        // Hose snaking from autopsy bed base toward left wall
        vec3 hose2P = p - vec3(-2.2, -1.45, 7.2);
        float xHose = cos(hose2P.z * 1.8) * 0.20;
        float hose2 = length(vec2(hose2P.x - xHose, hose2P.y)) - 0.035;
        if (hose2 < d) { d = hose2; hitMat = 17; }
    }

    // 4. Medical Anesthesia Machine Cart (far left)
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

    // 5. Autopsy Examination Bed (left-of-center)
    vec3 bedP = p - vec3(-0.8, -0.85, 7.8);
    float bedRegion = sdBox(bedP, vec3(0.9, 0.6, 1.6));
    if (bedRegion - 0.2 < d) {
        bedP.xz *= rot(-0.12);
        float bedPad = sdBox(bedP, vec3(0.65, 0.1, 1.4));
        float bedFrame = sdBox(bedP - vec3(0.0, -0.35, 0.0), vec3(0.6, 0.25, 1.35));
        float bedDrainTrough = sdBox(bedP - vec3(0.0, 0.08, -1.35), vec3(0.55, 0.04, 0.08));
        float bed = min(min(bedPad, bedFrame), bedDrainTrough);
        if (bed < d) { d = bed; hitMat = 5; }
    } else {
        d = min(d, bedRegion);
    }

    // 6. Center Office Desk with Retro White/Beige PC (shared back wall)
    vec3 deskP = p - vec3(0.26, -0.75, 9.2);
    float deskRegion = sdBox(deskP, vec3(1.3, 0.9, 0.8));
    if (deskRegion - 0.25 < d) {
        float deskTop = sdBox(deskP, vec3(1.15, 0.04, 0.65));
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

    // 7-9. Right-Hand Cluster: Morgue Vault, IV Pole, Biohazard Bin
    vec3 clusterP = p - vec3(2.8, 0.0, 5.8);
    float clusterRegion = sdBox(clusterP, vec3(2.0, 1.7, 2.2));
    if (clusterRegion - 0.3 < d) {
        // 7. Morgue Body Locker Vault Wall with OPEN TRAY
        vec3 vaultP = p - vec3(3.1, -0.1, 7.4);
        float vaultCabinet = sdBox(vaultP, vec3(1.8, 1.35, 0.35));

        // Open pull-out body tray extending forward along -Z
        vec3 trayP = vaultP - vec3(0.65, -0.12, -0.38);
        float trayBody = sdBox(trayP, vec3(0.45, 0.04, 0.40));
        float trayRimL = sdBox(trayP - vec3(-0.43, 0.05, 0.0), vec3(0.02, 0.05, 0.40));
        float trayRimR = sdBox(trayP - vec3(0.43, 0.05, 0.0), vec3(0.02, 0.05, 0.40));
        float trayHandle = sdBox(trayP - vec3(0.0, 0.06, -0.42), vec3(0.18, 0.03, 0.02));
        float openTray = min(trayBody, min(min(trayRimL, trayRimR), trayHandle));

        // Hollow wall cavity where the drawer pulled out from
        vec3 cavityP = vaultP - vec3(0.65, -0.12, -0.05);
        float cavity = sdBox(cavityP, vec3(0.46, 0.16, 0.32));
        float vaultStructure = max(vaultCabinet, -cavity);
        float vaultFinal = min(vaultStructure, openTray);
        if (vaultFinal < d) { d = vaultFinal; hitMat = 12; }

        // 8. Medical IV Drip Pole (Tight bounding box to eliminate micro-stepping trap)
        vec3 poleP = p - vec3(2.9, -0.4, 4.0);
        float poleBound = sdBox(poleP, vec3(0.25, 1.15, 0.25));
        if (poleBound < d) {
            float poleRod = sdCylinder(poleP, 1.05, 0.03);
            float poleBase = sdBox(poleP - vec3(0.0, -1.05, 0.0), vec3(0.2, 0.02, 0.2));
            float fluidBag = sdBox(poleP - vec3(0.08, 0.75, 0.0), vec3(0.08, 0.18, 0.04));
            float ivStand = min(min(poleRod, poleBase), fluidBag);
            if (ivStand < d) { d = ivStand; hitMat = 14; }
        }

        // 9. Biohazard Disposal Bin (Tight bounding box)
        vec3 binP = p - vec3(2.3, -1.15, 6.2);
        float binBound = sdBox(binP, vec3(0.48, 0.4, 0.48));
        if (binBound < d) {
            vec3 bP = binP;
            bP.xz *= rot(0.25);
            float bin = sdBox(bP, vec3(0.3, 0.35, 0.3));
            if (bin < d) { d = bin; hitMat = 15; }
        }
    } else {
        d = min(d, clusterRegion);
    }

    return d;
}

// 4-tap tetrahedral normal: reduces map() evaluations by 33% compared to central differences
vec3 calcNormal(vec3 p) {
    const vec2 k = vec2(1.0, -1.0);
    const float h = 0.0035;
    return normalize(
        k.xyy * map(p + k.xyy * h) +
        k.yyx * map(p + k.yyx * h) +
        k.yxy * map(p + k.yxy * h) +
        k.xxx * map(p + k.xxx * h)
    );
}

void main() {
    // Continuous global pixel coordinate across multi-monitor setup
    vec2 globalPx = gl_FragCoord.xy + u_viewportOffset;

    // Chunky low-res PS1-style pixelation: snap camera rays to a 2x2 block grid
    const float pixelBlock = 2.0;
    vec2 blockPx = (floor(globalPx / pixelBlock) + 0.5) * pixelBlock;
    vec2 uv = (blockPx - 0.5 * u_resolution) / u_resolution.y;

    // Wide panorama examination camera
    vec3 ro = vec3(0.0, 0.65, -1.2);
    vec3 target = vec3(0.2, -0.15, 8.5);
    vec3 fwd = normalize(target - ro);
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), fwd));
    vec3 up = cross(fwd, right);
    vec3 rd = normalize(fwd + uv.x * right * 0.62 + uv.y * up * 0.62);

    // Bounded Raymarching
    float t = 0.05;
    int obj = 0;
    vec3 p;
    bool hit = false;

    for (int i = 0; i < 56; i++) {
        p = ro + rd * t;
        float dist = map(p);
        obj = hitMat;
        if (dist < 0.005) {
            hit = true;
            break;
        }
        t += dist;
        if (t > 22.0) break;
    }

    if (!hit) {
        fragColor = vec4(0.015, 0.012, 0.015, 1.0);
        return;
    }

    vec3 n = calcNormal(p);
    vec3 viewDir = normalize(ro - p);

    // =========================================================================
    // LIGHTING PIPELINE: Steady, Atmospheric, Crushed Shadows (NO FLICKERING)
    // =========================================================================

    // 1. Crushed Ambient: Upper corners and far room edges recede into oppressive darkness
    float roomVignette = smoothstep(11.0, 3.5, length(p.xy - vec2(0.0, 0.2)));
    float ambientCoeff = 0.030 * roomVignette;

    // 2. Primary Overhead Fluorescent Light Fixture (Cold industrial hospital hue)
    vec3 fluorPos = vec3(-0.3, 1.70, 6.8);
    vec3 toFluor = fluorPos - p;
    float distFluor = length(toFluor);
    vec3 lFluorDir = normalize(toFluor);
    float attenFluor = 1.0 / (1.0 + 0.16 * distFluor + 0.045 * distFluor * distFluor);
    float diffFluorRaw = max(dot(n, lFluorDir), 0.0);
    float diffFluor = floor(diffFluorRaw * 6.0) / 6.0; // Faceted retro quantization
    vec3 fluorColor = vec3(0.75, 0.82, 0.88);
    vec3 lightFluor = fluorColor * diffFluor * attenFluor * 2.2;

    // 3. Dynamic Cursor Flashlight (Smooth, stable aiming - zero strobe)
    vec2 normMouse = (u_mouse.x >= 0.0)
        ? (u_mouse.xy / u_resolution.xy - 0.5)
        : vec2(sin(u_time * 0.25) * 0.14, cos(u_time * 0.20) * 0.08);
    vec3 flashPos = ro + vec3(-0.1, -0.1, 0.0);
    vec3 aimTarget = target + vec3(normMouse.x * 2.2, -normMouse.y * 1.8, 0.0);
    vec3 lightDirFlash = normalize(aimTarget - flashPos);
    vec3 toFrag = normalize(p - flashPos);
    float distFlash = length(p - flashPos);
    float spotCos = dot(lightDirFlash, toFrag);
    float spot = smoothstep(0.65, 0.94, spotCos);
    float attenFlash = 1.0 / (1.0 + 0.12 * distFlash + 0.05 * distFlash * distFlash);
    float diffFlashRaw = max(dot(n, -toFrag), 0.0);
    float diffFlash = floor(diffFlashRaw * 5.0) / 5.0;
    vec3 lightFlash = vec3(0.95, 0.92, 0.85) * diffFlash * attenFlash * spot * 1.8;

    // Blinn-Phong Specular Vectors
    vec3 halfFluor = normalize(lFluorDir + viewDir);
    vec3 halfFlash = normalize(-toFrag + viewDir);

    // =========================================================================
    // MATERIAL PALETTES & ENVIRONMENTAL FILTH
    // =========================================================================
    vec3 albedo = vec3(0.35);
    float wetness = 0.0; // Drives specular reflectivity and darkened absorption

    if (obj == 1) {
        // Floor: Degraded Hospital Tiles with Blood & Chemical Seepage Puddles
        vec2 tileUV = p.xz * 1.05;
        vec2 tileID = floor(tileUV);
        vec2 tileFract = fract(tileUV);
        float check = mod(tileID.x + tileID.y, 2.0);

        // Broken/chipped grout seams using domain noise
        float chip = noise(p.xz * 14.0) * 0.02;
        float grout = step(0.035 + chip, tileFract.x) * step(0.035 + chip, tileFract.y) *
                      step(tileFract.x, 0.965 - chip) * step(tileFract.y, 0.965 - chip);

        vec3 colLight = vec3(0.55, 0.52, 0.46);
        vec3 colDark  = vec3(0.18, 0.19, 0.17);
        vec3 tileColor = mix(colDark, colLight, check);

        // Accumulated dirt and floor grime
        float dirt = fbm(p.xz * 3.5);
        tileColor = mix(tileColor, vec3(0.12, 0.10, 0.08), dirt * 0.65);

        // Blood stains and chemical spill puddles
        float bloodPuddle = smoothstep(0.68, 0.86, fbm(p.xz * 3.8 + vec2(4.1, -1.8)));
        vec3 bloodCol = vec3(0.24, 0.02, 0.02);
        tileColor = mix(tileColor, bloodCol, bloodPuddle);

        // Wetness mask: puddles reflect direct specular light
        float chemPuddle = smoothstep(0.62, 0.82, fbm(p.xz * 2.8 - vec2(1.2, 3.4)));
        wetness = clamp(bloodPuddle * 1.2 + chemPuddle * 0.8, 0.0, 1.0);

        // Wet surfaces darken naturally
        tileColor *= mix(1.0, 0.45, wetness);
        albedo = tileColor * mix(0.12, 1.0, grout);
    }
    else if (obj == 2 || obj == 3) {
        // Walls: Saltpeter, Water Seepage Streaks & Grout Decay
        vec2 wallUV = (obj == 2) ? p.xy : p.zy;
        wallUV *= 2.2;
        vec2 wFract = fract(wallUV);
        float grout = step(0.04, wFract.x) * step(0.04, wFract.y);

        vec3 wallCol = vec3(0.48, 0.46, 0.42);

        // Vertical seepage streaks running down tiles
        float streaks = fbm(vec2(wallUV.x * 2.5, wallUV.y * 0.35 + fbm(wallUV * 1.5) * 0.3));
        wallCol = mix(wallCol, vec3(0.16, 0.13, 0.10), smoothstep(0.40, 0.82, streaks) * 0.75);

        // Dried wall blood smears
        float wallBlood = smoothstep(0.74, 0.90, fbm(p.xy * 3.2 - vec2(0.5, 1.2)));
        wallCol = mix(wallCol, vec3(0.26, 0.02, 0.02), wallBlood);

        wetness = smoothstep(0.72, 0.92, streaks) * 0.6;
        albedo = wallCol * mix(0.18, 1.0, grout);
    }
    else if (obj == 4) {
        // Anesthesia Machine Cart: Oxidized, rust-pitted medical metal
        vec3 metalCol = vec3(0.42, 0.41, 0.38);
        float rust = fbm(p.xy * 7.0 + p.z * 4.0);
        vec3 rustCol = vec3(0.28, 0.14, 0.08);
        albedo = mix(metalCol, rustCol, smoothstep(0.45, 0.75, rust));
    }
    else if (obj == 5) {
        // Autopsy Examination Bed: Stained rubber pad & drain trough
        if (p.y > -0.85) {
            vec3 mattress = vec3(0.32, 0.30, 0.27);
            float stain = fbm(p.xz * 3.0);
            albedo = mix(mattress, vec3(0.14, 0.10, 0.08), stain * 0.65);
            wetness = smoothstep(0.60, 0.85, stain) * 0.7;
        } else {
            albedo = vec3(0.22, 0.21, 0.20);
        }
    }
    else if (obj == 6) {
        // Machine Dial Knobs
        albedo = vec3(0.15, 0.16, 0.15);
    }
    else if (obj == 7) {
        // Heavy Cast-Iron Flanges, Collars, Pressure Valves & Gas Cylinders
        float scratches = noise(p.xy * vec2(2.0, 30.0));
        vec3 castIron = vec3(0.28, 0.29, 0.28);
        albedo = mix(castIron, vec3(0.42), scratches * 0.35);
    }
    else if (obj == 8) {
        // Dark Laminate Rear Desk (Dirty wood grain)
        vec3 woodCol = vec3(0.28, 0.22, 0.18);
        float woodGrain = noise(p.xz * vec2(22.0, 2.0));
        woodCol = mix(woodCol, vec3(0.18, 0.14, 0.11), woodGrain * 0.5);
        albedo = woodCol;
    }
    else if (obj == 9) {
        // Aged 90s Off-White / Beige PC Hardware
        vec3 beigeCol = vec3(0.70, 0.66, 0.56);
        float grime = fbm(p.xy * 12.0 + p.z * 10.0);
        vec3 darkGrime = vec3(0.32, 0.28, 0.22);
        albedo = mix(beigeCol, darkGrime, grime * 0.45);
    }
    else if (obj == 12) {
        // Stainless Steel Morgue Body Locker Wall & Open Pull-out Tray
        vec2 vaultUV = (p.xy - vec2(3.1, -0.1)) * vec2(1.4, 1.8);
        vec2 cellFract = fract(vaultUV);

        float doorSeam = step(0.04, cellFract.x) * step(0.04, cellFract.y) *
                         step(cellFract.x, 0.96) * step(cellFract.y, 0.96);

        float grain = noise(p.xy * vec2(1.0, 45.0));
        vec3 steelCol = mix(vec3(0.42, 0.44, 0.46), vec3(0.58, 0.60, 0.62), grain * 0.35);

        float rust = fbm(p.xy * 6.0 + vec2(1.5, 0.5));
        steelCol = mix(steelCol, vec3(0.28, 0.14, 0.08), smoothstep(0.52, 0.78, rust));

        float handle = step(abs(cellFract.x - 0.5), 0.18) * step(abs(cellFract.y - 0.5), 0.05);
        steelCol = mix(steelCol, vec3(0.78, 0.80, 0.82), handle);

        // Pulled-out autopsy tray has fluid staining
        if (p.z < 7.1) {
            float trayStain = fbm(p.xz * 5.0);
            steelCol = mix(steelCol, vec3(0.18, 0.04, 0.03), smoothstep(0.45, 0.75, trayStain));
            wetness = smoothstep(0.55, 0.85, trayStain) * 0.8;
        }

        albedo = steelCol * mix(0.15, 1.0, doorSeam);
    }
    else if (obj == 14) {
        // IV Stand & Fluid Bag
        albedo = vec3(0.40, 0.42, 0.40);
    }
    else if (obj == 15) {
        // Biohazard Disposal Bin (Hazard orange/yellow stained with filth)
        vec3 binCol = vec3(0.58, 0.42, 0.12);
        float grime = fbm(p.xz * 8.0);
        albedo = mix(binCol, vec3(0.16, 0.12, 0.08), grime * 0.65);
    }
    else if (obj == 16) {
        // Overhead Industrial Conduits & Steel Pipes
        vec3 pipeCol = vec3(0.32, 0.33, 0.34);
        float rust = fbm(p.xz * 6.0 + p.y * 3.0);
        albedo = mix(pipeCol, vec3(0.22, 0.12, 0.07), smoothstep(0.40, 0.70, rust));
    }
    else if (obj == 17) {
        // Heavy Hanging Catenary Power Cables & Floor Hoses (Matte Rubber)
        albedo = vec3(0.12, 0.12, 0.11);
    }
    else if (obj == 18) {
        // Fluorescent Fixture Housing (Aged sheet metal)
        albedo = vec3(0.45, 0.48, 0.46);
    }
    else if (obj == 19) {
        // Fluorescent Lamp Tube Emitter (Cold white fixture lamp - NO FLICKER)
        albedo = vec3(0.88, 0.94, 0.98);
    }

    // =========================================================================
    // SPECULAR REFLECTIONS (Blinn-Phong Term with Grime Mask)
    // =========================================================================
    float specFluor = pow(max(dot(n, halfFluor), 0.0), 28.0) * wetness * 1.5;
    float specFlash = pow(max(dot(n, halfFlash), 0.0), 32.0) * wetness * spot * 1.8;
    vec3 specularLight = fluorColor * specFluor + vec3(0.95) * specFlash;

    // Compose Diffuse, Ambient and Specular
    vec3 col = albedo * (ambientCoeff + lightFluor + lightFlash) + specularLight;

    // =========================================================================
    // STEADY EMISSIVE SOURCES & RADIANCE BLEED (STRICTLY NO STROBING)
    // =========================================================================

    // Overhead Fluorescent Lamp Tube surface
    if (obj == 19) {
        col = vec3(0.92, 0.96, 1.0);
    }

    // Front-Facing Medical CRT Screen (Steady phosphor green ECG)
    if (obj == 10) {
        vec3 machScreenOrigin = vec3(-3.9, -0.6, 6.2);
        vec3 localP = p - machScreenOrigin;
        localP.xz *= rot(-0.25);

        vec2 scrUV = localP.xy - vec2(-0.17, 0.62);
        float heartX = fract(scrUV.x * 2.0 - u_time * 0.35);
        float beat = sin(heartX * 25.0) * exp(-abs(heartX - 0.5) * 12.0) * 0.08;
        float ecg = smoothstep(0.015, 0.002, abs(scrUV.y - beat));

        vec3 phosphor = vec3(0.45, 0.92, 0.50);
        vec3 screenCol = phosphor * (0.80 + ecg * 0.50);
        screenCol *= mix(0.85, 1.0, step(0.3, fract(p.y * 120.0)));
        col = screenCol;
    }

    // Rear Retro PC Amber/DOS Terminal Screen (Steady phosphor amber scanlines)
    if (obj == 11) {
        vec3 pcScreenPos = vec3(0.11, -0.31, 9.28);
        vec2 scrUV = (p.xy - pcScreenPos.xy);

        float textLines = step(0.45, fract(scrUV.y * 24.0)) * step(0.12, hash(floor(scrUV * vec2(2.0, 24.0))));
        float cursorBlink = step(0.5, fract(u_time * 1.0)); // Stable 1 Hz terminal cursor
        float cursorBox = step(abs(scrUV.x - 0.10), 0.022) * step(abs(scrUV.y + 0.04), 0.015) * cursorBlink;

        vec3 amberCol = vec3(1.0, 0.65, 0.15);
        vec3 screenCol = mix(vec3(0.06, 0.04, 0.02), amberCol, clamp(textLines + cursorBox, 0.0, 1.0));
        screenCol *= mix(0.80, 1.0, step(0.35, fract(p.y * 130.0)));
        col = screenCol;
    }

    // Emissive Bleed 1: Steady CRT Monitor Glow onto anesthesia cart
    vec3 medScreenWorld = vec3(-3.9, 0.0, 6.2);
    float distToMedScreen = length(p - medScreenWorld);
    if (obj != 10 && distToMedScreen < 1.6) {
        float medGlow = (1.6 - distToMedScreen) / 1.6;
        col += vec3(0.08, 0.22, 0.10) * (medGlow * medGlow) * 0.55;
    }

    // Emissive Bleed 2: Steady Amber Glow onto desk
    vec3 pcScreenWorld = vec3(0.11, -0.31, 9.28);
    float distToPcScreen = length(p - pcScreenWorld);
    if (obj != 11 && distToPcScreen < 1.4) {
        float pcGlow = (1.4 - distToPcScreen) / 1.4;
        col += vec3(0.35, 0.18, 0.03) * (pcGlow * pcGlow) * 0.65;
    }

    // Distance fog: peripheral boundaries recede into cold, damp darkness
    col *= exp(-0.038 * t);

    // =========================================================================
    // POST-PROCESS: Retro PS1 Ordered-Dither & Color Quantization
    // =========================================================================
    int bx = int(mod(blockPx.x / pixelBlock, 4.0));
    int by = int(mod(blockPx.y / pixelBlock, 4.0));
    float dither = (bayer[by * 4 + bx] + 0.5) / 16.0 - 0.5;

    // Authentic retro color depth with dither transition
    float levels = 22.0;
    col = floor(col * levels + dither + 0.5) / levels;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
