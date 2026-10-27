#pragma once

// Everything the original astroland_dots' `globals.hpp` kept as free
// globals, minus the scene/physics state (that's all in plugin-native/'s
// Kotlin/Native library now, reached only through astro_native.h). This
// file owns the Hyprland-facing resources the Kotlin side can't touch:
// loaded textures, GIF frame arrays, and the miniaudio engine.

#define MINIAUDIO_IMPLEMENTATION
#include "../vendor/miniaudio.c"

extern "C" {
#include "../vendor/gifdec.h"
}

#include "../astro_native.h"
#include <Compositor.hpp>
#include <array>
#include <cmath>
#include <helpers/time/Timer.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprland/src/render/Texture.hpp>
#include <hyprland/src/state/MonitorQuery.hpp>
#include <hyprland/src/state/MonitorState.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <hyprutils/signal/Listener.hpp>
#include <macros.hpp>
#include <managers/input/InputManager.hpp>
#include <string>
#include <vector>
#include <wayland-server-core.h>

// Must match SpriteKind.kt's ordinals exactly -- see astro_native.h's class doc.
enum eSpriteKind {
    SPRITE_SHIP = 0,
    SPRITE_MOON_0,
    SPRITE_MOON_1,
    SPRITE_MOON_2,
    SPRITE_BULLET,
    SPRITE_CHAR,
    SPRITE_EXPLODE,
    SPRITE_VAX,
    SPRITE_BACKGROUND,
    SPRITE_KIND_COUNT,
};

// Must match AstroGlobals.kt's SoundKind ordinals exactly.
enum eSoundKind {
    SOUND_SHOOT = 0,
    SOUND_EXPLODE,
};

#ifndef ASTROLAND_ASSETS_DIR
#define ASTROLAND_ASSETS_DIR "." // overridden by CMake's -DASTROLAND_ASSETS_DIR, see CMakeLists.txt
#endif

inline const double DEG2RAD         = M_PI / 180.0;
inline const int    MS_BETWEEN_TICKS = 16;
inline const char*  ASSETS_DIR      = ASTROLAND_ASSETS_DIR;

inline ma_engine g_ma;

inline std::array<SP<Render::ITexture>, SPRITE_KIND_COUNT> g_staticTextures;
inline std::array<std::vector<SP<Render::ITexture>>, SPRITE_KIND_COUNT> g_gifFrames; // only SPRITE_EXPLODE/SPRITE_VAX are populated

inline std::string assetPath(const std::string& fileName) {
    return std::string(ASSETS_DIR) + "/" + fileName;
}

inline SP<Render::ITexture> loadPngTexture(const std::string& fullPath) {
    const auto crSurface = cairo_image_surface_create_from_png(fullPath.c_str());
    if (!crSurface) {
        Log::logger->log(Log::ERR, "astroland: failed to load {} (corrupt / inaccessible / not png)", fullPath);
        return nullptr;
    }
    auto tex = g_pHyprRenderer->createTexture(crSurface);
    cairo_surface_destroy(crSurface);
    return tex;
}

// GIFs are decoded once up front via gifdec (same decoder the original
// used), and every frame uploaded as its own cairo-backed texture -- the
// Kotlin/Native engine only ever references a frame *index* into this
// array (see NativeAnimatedSprite.kt's class doc on why GIF decoding stays
// on the C++ side), and learns the frame count/timing via astro_set_gif_meta.
inline void loadGifFrames(eSpriteKind kind, const std::string& fullPath) {
    gd_GIF* gif = gd_open_gif(fullPath.c_str());
    if (!gif) {
        Log::logger->log(Log::ERR, "astroland: failed to open gif {}", fullPath);
        return;
    }

    std::vector<uint8_t> frame(gif->width * gif->height * 3);
    std::vector<uint8_t> frameRgba(gif->width * gif->height * 4);
    auto&                frames    = g_gifFrames[kind];
    int                   totalDelayCentis = 0;
    int                   frameCount       = 0;

    while (gd_get_frame(gif) != -1) {
        gd_render_frame(gif, frame.data());
        totalDelayCentis += gif->gce.delay;
        frameCount++;

        const auto pixelCount = gif->width * gif->height;
        for (int i = 0; i < pixelCount; i++) {
            const auto b = frame[(i * 3) + 0];
            const auto g = frame[(i * 3) + 1];
            const auto r = frame[(i * 3) + 2];
            const auto a = (b + g + r) > 0;
            frameRgba[(i * 4) + 0] = r * a;
            frameRgba[(i * 4) + 1] = g * a;
            frameRgba[(i * 4) + 2] = b * a;
            frameRgba[(i * 4) + 3] = a * 255;
        }

        const auto cr = cairo_image_surface_create_for_data(frameRgba.data(), CAIRO_FORMAT_ARGB32, gif->width, gif->height, gif->width * 4);
        frames.push_back(g_pHyprRenderer->createTexture(cr));
        cairo_surface_destroy(cr);
    }

    gd_close_gif(gif);

    const int avgDelayMillis = frameCount > 0 ? (totalDelayCentis * 10) / frameCount : 100;
    astro_set_gif_meta(kind, frameCount, avgDelayMillis);
}

inline void loadAllAssets() {
    g_staticTextures[SPRITE_SHIP] = loadPngTexture(assetPath("ship.png"));
    g_staticTextures[SPRITE_MOON_0] = loadPngTexture(assetPath("moon_0.png"));
    g_staticTextures[SPRITE_MOON_1] = loadPngTexture(assetPath("moon_1.png"));
    g_staticTextures[SPRITE_MOON_2] = loadPngTexture(assetPath("moon_2.png"));
    g_staticTextures[SPRITE_BULLET] = loadPngTexture(assetPath("bullet.png"));
    g_staticTextures[SPRITE_CHAR] = loadPngTexture(assetPath("char.png"));
    g_staticTextures[SPRITE_BACKGROUND] = loadPngTexture(assetPath("bg.png"));

    loadGifFrames(SPRITE_EXPLODE, assetPath("explode.gif"));
    loadGifFrames(SPRITE_VAX, assetPath("vax.gif"));
}

inline void playSound(eSoundKind kind) {
    switch (kind) {
        case SOUND_SHOOT: ma_engine_play_sound(&g_ma, assetPath("shoot_" + std::to_string(std::rand() % 3) + ".wav").c_str(), nullptr); break;
        case SOUND_EXPLODE: ma_engine_play_sound(&g_ma, assetPath("explode_0.wav").c_str(), nullptr); break;
    }
}

// The original followed the compositor's *first* workspace's slide-in/out
// render offset, which is a crude stand-in for "whatever's animating on
// this monitor right now" -- using this monitor's actual active workspace
// instead (0.56.2 no longer exposes a flat getWorkspaces() off
// CCompositor) is strictly more correct for multi-monitor setups.
inline Vector2D g_worldToMonitor(PHLMONITORREF monitor, const Vector2D& worldPos, const Vector2D& camPos) {
    auto pos = worldPos - monitor->m_position;
    pos += monitor->m_size / 2;

    if (const auto& workspace = monitor->m_activeWorkspace)
        pos += workspace->m_renderOffset->value();

    return pos - camPos;
}

inline Vector2D g_monitorToWorld(PHLMONITORREF monitor, const Vector2D& monPos, const Vector2D& camPos) {
    auto pos = monPos + monitor->m_position;
    pos -= monitor->m_size / 2;

    if (const auto& workspace = monitor->m_activeWorkspace)
        pos -= workspace->m_renderOffset->value();

    return pos + camPos;
}

inline Vector2D g_getPointerWorldPos() {
    const auto ppos    = g_pInputManager->getMouseCoordsInternal();
    const auto monitor = State::CMonitorQuery(*State::monitorState()).vec(ppos).run();
    if (!monitor)
        return {astro_cam_x(), astro_cam_y()};
    return g_monitorToWorld(monitor, ppos, {astro_cam_x(), astro_cam_y()});
}
