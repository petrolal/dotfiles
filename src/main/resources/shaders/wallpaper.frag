#version 330 core
out vec4 FragColor;

in vec2 v_uv;

uniform float u_time;
uniform float u_timeOfDay;
uniform vec2 u_resolution;
uniform vec4 u_mouse; // x, y, leftDown, rightDown

// Screen-locker state (see dev.petrolal.dotfiles.locker.ScreenLocker). Left
// at GL's default zero for the ordinary desktop wallpaper daemon, which
// never sets these -- so none of the reactions below ever trigger there.
uniform int u_charCount;        // characters currently typed into the password buffer
uniform float u_authError;      // 1.0 right after a failed attempt, decaying to 0.0 over ~1.2s
uniform float u_isAuthenticating; // 1.0 while a PAM check is in flight
uniform vec2 u_mousePos;        // normalized [0,1] cursor position, for the lock UI's own parallax
uniform float u_lockerActive;   // 1.0 only while ScreenLocker is running -- gates the persistent
                                 // input-slot/cursor so the ordinary desktop wallpaper never shows it
// The locker's single window spans the WHOLE virtual desktop (every
// monitor at once, so the input grab/background covers all of them), but
// the 3D scene itself should be centered on one physical monitor -- without
// this, on a multi-monitor setup the monitor object and camera center land
// on the bezel boundary between two screens instead of being visible on
// either one. Only meaningful (and only set by ScreenLocker) when
// u_lockerActive is 1.0; ignored otherwise.
uniform vec2 u_primaryOffsetPx; // primary monitor's bottom-left corner, in GL window (bottom-left-origin) pixels
uniform vec2 u_primaryResPx;    // primary monitor's own width/height in pixels
uniform sampler2D u_loginTexture; // baked Win98 "Enter Password" dialog chrome (see LoginPromptTexture.java)
uniform vec4 u_passwordFieldUv;   // (u0,v0,u1,v1) rect of that texture's blank password field

// Palette
const vec3 C_BG_DEEP    = vec3(0.04, 0.01, 0.06); // #0a0210
const vec3 C_BLOOD_RED  = vec3(0.85, 0.05, 0.20); // #d90d33
const vec3 C_CRIMSON    = vec3(0.55, 0.02, 0.15); // #8c0526
const vec3 C_PURPLE     = vec3(0.35, 0.05, 0.40); // #590d66
const vec3 C_SICK_GREEN = vec3(0.18, 0.38, 0.15); // #2e6126
const vec3 C_CYBER_ACID = vec3(0.20, 0.95, 0.30); // #33f24d (cyber acid neon)
const vec3 C_GLITCH_MAGENTA = vec3(0.95, 0.08, 0.85); // #f215d9 -- paired with C_CYBER_ACID
// for an RGB-channel-split "VHS glitch" look (magenta = R+B, green = G alone)
// instead of flat solid-color blocks.

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

float fbmClouds(vec2 p) {
    float v = 0.0;
    mat2 rot = mat2(0.8, -0.6, 0.6, 0.8);
    v += 0.500 * noise(p); p = rot * p * 2.02;
    v += 0.250 * noise(p); p = rot * p * 2.03;
    v += 0.125 * noise(p); p = rot * p * 2.01;
    v += 0.062 * noise(p);
    return v;
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

// --- Screen-locker 3D UI (phase 2): a hovering, corrupted retro CRT monitor
// -- Windows-98-cased, matching this dotfiles setup's existing Imp98/Chicago95
// theme -- raymarched as a real SDF object in the world, not a 2D overlay.
// Only part of the scene while u_lockerActive is set (see ScreenLocker); the
// ordinary desktop wallpaper daemon never adds this term, so map() degrades
// to exactly the plain terrain heightfield for it.
// Proportions matched to LoginPromptTexture's 440x320 canvas (aspect
// 1.375:1), facing the camera flat-on (no yaw) like a real XFCE/Windows
// login dialog -- not tilted like a prop sitting at an angle in the scene.
const vec3 MONITOR_CENTER = vec3(0.0, 1.0, -0.6);
const vec3 MONITOR_HALF   = vec3(0.88, 0.64, 0.08);
const float MONITOR_YAW = 0.0;

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

/** Coverage [0,1] of one thin line segment through the origin, ANGLE
 * degrees from horizontal -- used by asteriskMask() below to build a '*'
 * out of three crossed strokes instead of a plain round password dot. */
float lineSeg(vec2 p, float angleDeg, float halfLen, float thickness) {
    float a = radians(angleDeg);
    mat2 rot = mat2(cos(a), -sin(a), sin(a), cos(a));
    vec2 q = rot * p;
    float d = max(abs(q.x) - halfLen, abs(q.y) - thickness);
    return smoothstep(0.0025, 0.0, d);
}

/** Password-mask glyph: a 3-stroke asterisk ('*'), not a round dot --
 * matches how real password fields mask input. P is local offset from the
 * glyph's center. */
float asteriskMask(vec2 p, float halfLen, float thickness) {
    float m = lineSeg(p, 0.0, halfLen, thickness);
    m = max(m, lineSeg(p, 60.0, halfLen, thickness));
    m = max(m, lineSeg(p, 120.0, halfLen, thickness));
    return m;
}

/** Box-local (rotated) coordinates for a world-space point -- shared by
 * monitorDist() (raymarch distance) and the hit-shading code below (face
 * selection/UV), so both always agree on where the box actually is this
 * frame. Deliberately has NO mouse-driven offset: the login dialog must
 * read as a fixed, centered real login prompt, not an object drifting
 * around the screen -- see also main()'s camera setup, which freezes ro/ta
 * (no mouse parallax at all) while u_lockerActive is set, so the dialog
 * can't drift from camera movement either. */
vec3 monitorLocal(vec3 p) {
    vec3 lp = p - MONITOR_CENTER;
    float ca = cos(MONITOR_YAW), sa = sin(MONITOR_YAW);
    lp.xz = mat2(ca, -sa, sa, ca) * lp.xz;
    return lp;
}

float monitorDist(vec3 p) {
    return sdBox(monitorLocal(p), MONITOR_HALF);
}

float map(vec3 p) {
    float dTerrain = p.y - terrainHeight(p.xz);
    if (u_lockerActive > 0.5) {
        return min(dTerrain, monitorDist(p));
    }
    return dTerrain;
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

    // The locker's one window spans every monitor at once (see
    // u_primaryOffsetPx/u_primaryResPx above); recenter the camera's pixel
    // space on just the primary monitor so the scene -- and the 3D monitor
    // object -- land in the middle of one real screen instead of straddling
    // the bezel between two. On a single-monitor setup u_primaryResPx
    // equals the full resolution and this is a no-op. The ordinary desktop
    // wallpaper daemon never sets either uniform (both read back as zero),
    // so it always takes the original whole-resolution path unchanged.
    vec2 camRes = res;
    vec2 camOffset = vec2(0.0);
    if (u_lockerActive > 0.5 && u_primaryResPx.x > 1.0 && u_primaryResPx.y > 1.0) {
        camRes = u_primaryResPx;
        camOffset = u_primaryOffsetPx;
    }
    vec2 uv = (gl_FragCoord.xy - camOffset - 0.5 * camRes) / camRes.y;

    float hour = (u_timeOfDay >= 0.0 && u_timeOfDay <= 24.0) ? u_timeOfDay : 12.0;

    // --- Diurnal Solar / Lunar Trajectory ---
    float sunAngle = (hour - 6.0) * (3.14159265 / 12.0);
    vec3 sunDir = normalize(vec3(cos(sunAngle), sin(sunAngle), -0.6));
    vec3 moonDir = -sunDir;

    float isDay = smoothstep(-0.1, 0.2, sunDir.y);
    float isNight = 1.0 - isDay;

    // --- Dynamic Glitch Intensity Curve by Hour of Day ---
    // Smooth at day, ramping up at dusk and midnight, reaching maximum intensity in demon hours (00:00 - 04:00)
    float midnightFactor = smoothstep(20.0, 23.0, hour) + (1.0 - smoothstep(3.5, 6.0, hour));
    float demonHours = smoothstep(23.0, 24.0, hour) + (1.0 - smoothstep(0.0, 4.0, hour));
    float duskFactor = smoothstep(16.5, 19.5, hour) * (1.0 - smoothstep(20.5, 22.5, hour));
    float hourGlitchScale = 0.25 + 0.35 * duskFactor + 0.65 * midnightFactor + 0.45 * demonHours;

    // --- Unpredictable Asymmetric Glitch Timing ("Randomized Sleeps") ---
    float epochDuration = mix(0.18, 0.35, isDay);
    float epoch = floor(u_time * epochDuration);
    float epochRand = hash1(epoch);
    float sleepThreshold = mix(0.35, 0.65, isDay);
    float activityGate = step(sleepThreshold, epochRand);

    float erraticRate = mix(8.0, 3.5, isDay);
    float erraticTime = u_time * (3.0 + erraticRate * hash1(floor(u_time * 1.5)));
    float microPulse = step(0.92 - (0.05 * hourGlitchScale), sin(erraticTime) * cos(erraticTime * 2.37));
    float suddenSpike = step(0.982 - (0.02 * hourGlitchScale), hash1(floor(u_time * 14.0)));
    float glitchPulse = max(microPulse * activityGate, suddenSpike) * hourGlitchScale;

    // --- Cyberpunk / Matrix Digital Slice Glitches (Sharp, discrete, zero waves) ---
    float sliceY1 = floor(gl_FragCoord.y / 14.0);
    float sliceTime = floor(u_time * 24.0);
    float sliceRand1 = hash(vec2(sliceY1 * 0.31, sliceTime * 0.77));
    float sliceTrigger1 = step(0.93 - (0.06 * hourGlitchScale), sliceRand1);
    float sliceOffset1 = (hash(vec2(sliceY1 * 0.83, sliceTime + 3.1)) - 0.5) * 0.065 * sliceTrigger1 * min(glitchPulse, 1.4);

    float sliceY2 = floor(gl_FragCoord.y / 4.0);
    float sliceRand2 = hash(vec2(sliceY2 * 0.19, sliceTime * 1.31));
    float sliceTrigger2 = step(0.96 - (0.03 * hourGlitchScale), sliceRand2);
    float sliceOffset2 = (hash(vec2(sliceY2 * 0.67, sliceTime + 7.9)) - 0.5) * 0.030 * sliceTrigger2 * min(glitchPulse, 1.2);

    float totalTear = sliceOffset1 + sliceOffset2;
    uv.x += totalTear;

    // Jittered Camera Parallax & Subtle Tremor on Glitch -- disabled while
    // the locker is active so the login dialog reads as a fixed, centered
    // prompt rather than something that drifts around with the mouse (see
    // monitorLocal() above).
    vec2 mNorm = u_lockerActive > 0.5 ? vec2(0.0) : clamp(u_mouse.xy / res - 0.5, vec2(-0.5), vec2(0.5));
    float quake = (hash(vec2(u_time * 30.0, gl_FragCoord.y)) - 0.5) * 0.025 * min(glitchPulse, 1.0);
    // While the locker is active, the camera must be perfectly level (ro.y
    // == ta.y, both equal to MONITOR_CENTER.y) and look straight down -z --
    // any vertical pitch perspective-foreshortens the login dialog box
    // unevenly between its top/bottom edges, which reads as the dialog
    // having the wrong aspect ratio even though MONITOR_HALF itself already
    // matches LoginPromptTexture's true 440x320 aspect.
    vec3 ro = u_lockerActive > 0.5 ? vec3(0.0, MONITOR_CENTER.y, 4.0) : vec3(mNorm.x * 0.8 + quake, 1.8 - mNorm.y * 0.4, 4.0);
    vec3 ta = u_lockerActive > 0.5 ? vec3(0.0, MONITOR_CENTER.y, -4.0) : vec3(mNorm.x * 0.4 - quake * 0.5, 0.5 - mNorm.y * 0.2, -4.0);
    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = normalize(cross(uu, ww));
    vec3 rd = normalize(uv.x * uu + uv.y * vv + 1.2 * ww);

    // Baseline fixed celestial direction for sky, sun, moon, and clouds
    // (Completely independent of mouse parallax so moving the mouse does NOT move the sky or clouds!)
    vec3 fixedRd = normalize(vec3(uv.x, uv.y - 0.15, -1.2));

    // Sky gradient
    vec3 daySky = mix(vec3(0.22, 0.32, 0.46), vec3(0.10, 0.22, 0.42), clamp(fixedRd.y * 2.0, 0.0, 1.0));
    vec3 nightSky = mix(C_BG_DEEP, vec3(0.015, 0.005, 0.025), clamp(fixedRd.y * 1.5, 0.0, 1.0));
    vec3 skyColor = mix(nightSky, daySky, isDay);

    // --- Continually Animated Clouds at Day (Moving Independently of Mouse) ---
    if (fixedRd.y > 0.005) {
        float skyY = max(fixedRd.y, 0.025);
        vec2 cloudPos = (fixedRd.xz / skyY) * 0.7;
        
        // Fluid dual-layer wind animation
        vec2 wind1 = vec2(u_time * 0.045, u_time * 0.012);
        vec2 wind2 = vec2(u_time * -0.025, u_time * 0.020);
        
        float cloudFBM = fbmClouds(cloudPos + wind1) * 0.65 + fbmClouds(cloudPos * 1.8 + wind2) * 0.35;
        float cloudMask = smoothstep(0.38, 0.74, cloudFBM);
        
        vec3 cloudColor = mix(vec3(0.32, 0.38, 0.48), vec3(0.85, 0.88, 0.92), cloudFBM);
        float cloudHorizonFade = smoothstep(0.005, 0.22, fixedRd.y);
        skyColor = mix(skyColor, cloudColor, cloudMask * cloudHorizonFade * isDay * 0.92);
    }

    // Celestial Bodies (anchored to sky dome, independent of mouse parallax)
    float sunDiff = max(dot(fixedRd, sunDir), 0.0);
    float sunDisc = smoothstep(0.996, 0.998, sunDiff);
    vec3 sunVisual = vec3(0.98, 0.95, 0.88) * sunDisc * 2.2 + vec3(0.90, 0.45, 0.25) * pow(sunDiff, 16.0);

    float moonDiff = max(dot(fixedRd, moonDir), 0.0);
    float moonDisc = smoothstep(0.993, 0.995, moonDiff);
    vec3 moonVisual = C_BLOOD_RED * moonDisc * (2.8 + 1.2 * demonHours) + C_BLOOD_RED * pow(moonDiff, 80.0) * 0.55;

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

        // Which SDF surface did the ray actually land on? map() returns the
        // min() of both, so whichever term is smaller at the hit point is
        // the real surface (the other may still be "near" p without being
        // what the ray actually converged onto).
        bool onMonitor = u_lockerActive > 0.5 && monitorDist(p) < (p.y - terrainHeight(p.xz));

        if (onMonitor) {
            // --- Corrupted retro CRT monitor (Windows-98-cased, matching
            // this dotfiles setup's Imp98/Chicago95 desktop theme) --
            // a real 3D SDF object the raymarch actually lands on, not a
            // flat overlay: it catches lightDir/fog/shadow like anything
            // else in the scene. ---
            vec3 lp = monitorLocal(p);
            // Bring the world-space normal into that same rotated local
            // frame (same yaw applied to it as to lp above) so face
            // selection/UV always agree with where the box actually is,
            // regardless of MONITOR_YAW or the mouse parallax offset.
            vec3 norLocal = nor;
            float ca = cos(MONITOR_YAW), sa = sin(MONITOR_YAW);
            norLocal.xz = mat2(ca, -sa, sa, ca) * nor.xz;
            vec3 an = abs(norLocal);
            bool frontFace = an.z >= an.x && an.z >= an.y && norLocal.z > 0.0;

            vec2 faceUv;
            if (frontFace) faceUv = lp.xy / MONITOR_HALF.xy;
            else if (an.x >= an.y) faceUv = lp.zy / MONITOR_HALF.zy * vec2(sign(norLocal.x), 1.0);
            else faceUv = lp.xz / MONITOR_HALF.xz;
            faceUv = faceUv * 0.5 + 0.5; // [-1,1] box-local -> [0,1] face UV

            if (frontFace) {
                // The real Win98 "Enter Password" dialog chrome -- titlebar,
                // logo, labels, listbox, OK/Cancel -- baked as a texture by
                // LoginPromptTexture.java (there's no font rendering in a
                // raymarch shader, so static text/UI is rasterized once on
                // the Java side instead of faked with SDF shapes).
                vec2 texUv = clamp(faceUv, 0.0, 1.0);
                vec3 screenCol = texture(u_loginTexture, texUv).rgb;

                // Live password-dot/caret overlay, precisely aligned to the
                // texture's own blank password field via u_passwordFieldUv
                // (computed from the exact same pixel rect the Java side
                // drew that field at -- see LoginPromptTexture.Layout).
                vec2 fieldMin = u_passwordFieldUv.xy;
                vec2 fieldMax = u_passwordFieldUv.zw;
                if (texUv.x > fieldMin.x && texUv.x < fieldMax.x && texUv.y > fieldMin.y && texUv.y < fieldMax.y) {
                    vec2 fieldSize = fieldMax - fieldMin;
                    vec2 local = (texUv - fieldMin) / fieldSize; // 0..1 within the field
                    float dotY = 0.5;
                    float leftPad = 0.04;

                    int dotCount = min(u_charCount, 28);
                    if (dotCount > 0) {
                        float dotSpacing = min(0.85 / float(max(dotCount, 1)), 0.05);
                        for (int i = 0; i < dotCount; i++) {
                            float dx = local.x - (leftPad + float(i) * dotSpacing + dotSpacing * 0.5);
                            float dy = (local.y - dotY) * (fieldSize.y / fieldSize.x) * 3.5;
                            float star = asteriskMask(vec2(dx, dy), 0.018, 0.0045);
                            screenCol = mix(screenCol, vec3(0.05), star);
                        }
                    } else {
                        float blink = step(0.5, fract(u_time * 1.5));
                        float dx = local.x - leftPad;
                        float dy = (local.y - dotY) * (fieldSize.y / fieldSize.x) * 3.5;
                        float caret = smoothstep(0.012, 0.0, length(vec2(dx - 0.01, dy)))
                                + (1.0 - smoothstep(0.0, 0.03, abs(dx))) * step(abs(dy), 0.25);
                        screenCol = mix(screenCol, vec3(0.1), caret * blink);
                    }
                }

                if (u_isAuthenticating > 0.5) {
                    float verifyStatic = hash(vec2(floor(texUv.x * 60.0), floor(texUv.y * 40.0) + floor(u_time * 24.0) * 7.0));
                    screenCol = mix(screenCol, C_CRIMSON, step(0.88, verifyStatic) * 0.4);
                }
                if (u_authError > 0.0) {
                    // Same magenta channel-gain wash as the full-screen
                    // shockwave below, so the dialog face and the rest of
                    // the scene glitch the same way during a failed attempt.
                    screenCol.r = clamp(screenCol.r * (1.0 + 0.9 * u_authError), 0.0, 1.0);
                    screenCol.b = clamp(screenCol.b * (1.0 + 0.7 * u_authError), 0.0, 1.0);
                    screenCol.g = clamp(screenCol.g * (1.0 - 0.5 * u_authError), 0.0, 1.0);
                }

                // Still a haunted terminal, not a clean modern UI: a sparse
                // corrupting flicker even over the "real" dialog chrome.
                // Fine film-grain corruption speckle, magenta/green tinted
                // like a real channel-split glitch -- not flat red squares.
                float corruptNoise = hash(vec2(floor(texUv.x * 220.0), floor(texUv.y * 160.0) + floor(u_time * 5.0)));
                float corruptFlicker = step(0.985, corruptNoise) * (0.5 + 0.5 * sin(u_time * 45.0));
                vec3 corruptCol = mix(C_GLITCH_MAGENTA, C_CYBER_ACID, hash1(corruptNoise * 17.0));
                screenCol = mix(screenCol, corruptCol, corruptFlicker * 0.5);

                col = screenCol;
            } else {
                // Thin plastic edge -- barely visible face-on, but still a
                // real lit surface rather than a hole in the object.
                vec3 caseColor = vec3(0.3, 0.3, 0.3);
                col = caseColor * dif;
            }

            float fog = exp(-0.035 * t * t);
            col = mix(skyColor, col, fog);
        } else {
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

            // Distance atmospheric fog into horizon
            float fog = exp(-0.035 * t * t);
            col = mix(skyColor, col, fog);
        }
    }

    // --- CRT Scanlines ---
    float scanline = sin(gl_FragCoord.y * 1.4) * (0.02 + 0.03 * isNight * hourGlitchScale);
    col -= scanline;

    // --- Cyberpunk / Matrix Post-Processing Glitches ---
    // 1. Chromatic-split digital tear: real VHS/RGB-split glitch, not a flat
    // solid-color block -- boosting R+B together while cutting G reads as
    // magenta (and vice versa for green), the same split a real channel-
    // offset chromatic aberration produces, so the scene's own detail stays
    // visible through the tint instead of being flattened to one color.
    float activeSlice = sliceTrigger1 + sliceTrigger2;
    if (activeSlice > 0.5 && glitchPulse > 0.15) {
        vec3 cyberTear = col;
        float tearColorType = hash1(sliceY1 + sliceTime);
        if (tearColorType < 0.55) {
            // Magenta channel split (R+B up, G down)
            cyberTear.r = clamp(col.r * 1.5 + 0.18, 0.0, 1.0);
            cyberTear.b = clamp(col.b * 1.5 + 0.16, 0.0, 1.0);
            cyberTear.g = col.g * 0.30;
        } else {
            // Green channel split (G up, R+B down)
            cyberTear.g = clamp(col.g * 1.7 + 0.15, 0.0, 1.0);
            cyberTear.r = col.r * 0.35;
            cyberTear.b = col.b * 0.35;
        }
        col = mix(col, cyberTear, 0.70 * min(glitchPulse, 1.3));
    }

    // 2. Matrix Digital Laser Scanline Drops (Thin 1-2px data lines flashing across screen)
    float laserTrigger = step(0.992 - (0.018 * hourGlitchScale), hash(vec2(floor(gl_FragCoord.y * 0.5), floor(u_time * 28.0))));
    if (laserTrigger > 0.5 && glitchPulse > 0.20) {
        float laserHue = hash1(gl_FragCoord.y * 3.7 + u_time);
        vec3 laserCol = (laserHue < 0.5) ? C_CYBER_ACID * 1.4 : C_GLITCH_MAGENTA * 1.4;
        col = mix(col, laserCol, 0.75 * min(glitchPulse, 1.2));
    }

    // 3. Fine-grained VHS static (film-grain-sized noise, magenta/green
    // tinted like a real channel-split tape glitch -- deliberately NOT the
    // old big flat-colored blocks, which read as "pixelated squares" rather
    // than analog noise).
    if (glitchPulse > 0.35) {
        float digitalStatic = hash(vec2(floor(gl_FragCoord.x), floor(gl_FragCoord.y) + floor(u_time * 24.0) * 19.3));
        float staticMask = step(0.72 - (0.12 * demonHours), digitalStatic) * min(glitchPulse, 1.2) * 0.35;
        vec3 staticCol = mix(C_GLITCH_MAGENTA, C_CYBER_ACID, hash1(digitalStatic * 13.0));
        col = mix(col, col * 0.4 + staticCol * 0.6, staticMask);
    }



    // --- Screen-Locker Reactive UI (phase 2): the password-dot/caret
    // indicator now lives ON the 3D CRT monitor object above (see the
    // onMonitor branch) instead of as a flat 2D overlay. What's left here
    // is the whole-environment reaction to auth state -- a shockwave/static
    // effect legitimately belongs to the whole frame, not just the monitor
    // surface. Both still gate correctly on u_authError/u_isAuthenticating
    // alone, which stay at GL's default zero for the ordinary desktop
    // wallpaper daemon. ---

    // Error shockwave: violent Blood Moon flash + extra chromatic tear,
    // strongest right after a failed attempt and decaying (decay itself
    // happens on the Java side, driving u_authError down over ~1.2s).
    if (u_authError > 0.0) {
        // Channel-gain shockwave instead of a flat color flood: boosting
        // R+B together while cutting G reads as a magenta chromatic-split
        // wash, and the scene's own detail stays visible through it rather
        // than being flattened to one solid color.
        col.r = clamp(col.r * (1.0 + 0.9 * u_authError), 0.0, 1.0);
        col.b = clamp(col.b * (1.0 + 0.7 * u_authError), 0.0, 1.0);
        col.g = clamp(col.g * (1.0 - 0.5 * u_authError), 0.0, 1.0);
        float errorShake = (hash(vec2(u_time * 50.0, gl_FragCoord.y * 0.3)) - 0.5) * 0.04 * u_authError;
        col.r = clamp(col.r + errorShake + 0.1 * u_authError, 0.0, 1.0);
        float errorSlice = step(0.85, hash(vec2(floor(gl_FragCoord.y / 6.0), floor(u_time * 40.0))));
        vec3 errorSliceCol = mix(C_GLITCH_MAGENTA, C_CYBER_ACID, hash1(floor(gl_FragCoord.y / 6.0) * 0.37));
        col = mix(col, errorSliceCol, errorSlice * 0.45 * u_authError);
    }

    // Verifying: fluctuating high-frequency crimson static while PAM is
    // actually checking the password (keeps the screen visibly "alive"
    // during what might be a slow authenticate() round-trip).
    if (u_isAuthenticating > 0.5) {
        float verifyStatic = hash(vec2(floor(gl_FragCoord.x / 2.0), floor(gl_FragCoord.y / 2.0) + floor(u_time * 30.0) * 7.0));
        col = mix(col, C_CRIMSON, step(0.8, verifyStatic) * 0.18);
    }

    // Vignette
    vec2 v = v_uv * (1.0 - v_uv.yx);
    float vignette = clamp(pow(v.x * v.y * 15.0, 0.35), 0.0, 1.0);
    col *= vignette;

    FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}