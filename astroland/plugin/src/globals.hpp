#pragma once

// Declarations only -- see vendor/vendor_impl.cpp for why the
// *_IMPLEMENTATION macros are deliberately NOT defined here.
#include "miniaudio.c"
#include "stb_perlin.c"
#include <Compositor.hpp>
#include <helpers/time/Timer.hpp>
#include <hyprland/src/config/values/types/BoolValue.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprland/src/render/Texture.hpp>
#include <hyprutils/math/Box.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <hyprutils/signal/Listener.hpp>
#include <macros.hpp>
#include <managers/input/InputManager.hpp>
#include <plugins/HookSystem.hpp>
#include <wayland-server-core.h>

#include <cmath>
#include <cstdlib>
#include <hyprland/src/state/MonitorState.hpp>
#include <hyprland/src/state/MonitorQuery.hpp>
#include <hyprland/src/desktop/state/FocusState.hpp>

class Ship;
class Body;
class Window;

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

inline const double DEG2RAD = M_PI / 180.0;
inline const double ANGLE_90 = 90.0 * DEG2RAD;

inline const int MS_BETWEEN_TICKS = 16;

inline std::string assetPath(const std::string& fileName) {
    const char* home = std::getenv("HOME");
    std::string base = (home != nullptr) ? (std::string(home) + "/dotfiles/astroland/assets") : "/home/petrolal/dotfiles/astroland/assets";
    return base + "/" + fileName;
}

inline PHLMONITORREF g_primaryMonitor() {
    if (const auto focused = Desktop::focusState()->monitor())
        return PHLMONITORREF{focused};
    const auto ppos = g_pInputManager->getMouseCoordsInternal();
    if (const auto mouseMon = State::CMonitorQuery(*State::monitorState()).vec(ppos).run())
        return PHLMONITORREF{mouseMon};
    const auto& all = State::monitorState()->monitors();
    return all.empty() ? PHLMONITORREF{} : PHLMONITORREF{all[0]};
}

inline ma_engine g_ma;

inline SP<Render::ITexture> g_tex_bg;
inline std::unordered_map<std::string, SP<Render::ITexture>> g_texs;

static SP<Render::ITexture> loadTex(const std::string& fullPath) {
	const auto& kv = g_texs.find(fullPath);
	if (kv != g_texs.end()) {
		return kv->second;
	}

	const auto cr_surface = cairo_image_surface_create_from_png(fullPath.c_str());

	if (!cr_surface) {
		Log::logger->log(Log::ERR, "loadTex: failed to load {} (corrupt / inaccessible / not png)", fullPath);
		return nullptr;
	}

	auto tex = g_pHyprRenderer->createTexture(cr_surface);
	g_texs[fullPath] = tex;

	cairo_surface_destroy(cr_surface);

	return tex;
}

inline SP<Body> g_scene;
inline SP<Ship> g_ship;

const auto CAM_SHAKE_FALLOFF_SPEED = 30.0;
const auto CAM_SHAKE_NOISE_FREQ = 7.0;
inline Vector2D g_camPos;
inline Vector2D g_actualCamPos;
inline Vector2D g_camShakeOffset;
inline Vector2D g_camShakeNoiseIndex;
inline double g_camShakeIntensity = 0;

const auto MAX_MOONS_PER_WAVE = 3;
const auto MAX_MOONS = 10;
const auto S_BETWEEN_WAVES = 4;
inline double g_moonSpawnT;
inline double g_moonCount;

inline void camShake(double intensity) {
	g_camShakeIntensity += intensity;
}

inline bool g_isUpDown;
inline bool g_isDownDown;
inline bool g_isLeftDown;
inline bool g_isRightDown;
inline Vector2D g_inputVec;

inline CTimer g_tickTimer;
inline wl_event_source* g_tickSource;

inline CHyprSignalListener g_readyListener;
inline CHyprSignalListener g_windowCloseListener;
inline CHyprSignalListener g_windowFocusListener;
inline CHyprSignalListener g_renderListener;
inline CHyprSignalListener g_mouseButtonListener;
inline CHyprSignalListener g_keyListener;

inline CFunctionHook* g_drawSurfaceHook;

inline SP<Config::Values::CBoolValue> g_spawnMoons;
inline SP<Config::Values::CBoolValue> g_loonMoons;

static Vector2D g_worldToMonitor(PHLMONITORREF monitor, const Vector2D& worldPos) {
	if (!monitor)
		return worldPos - g_camPos;
	auto pos = worldPos - monitor->m_position;
	pos += monitor->m_size / 2; // pos relative to monitor center

	if (const auto& workspace = monitor->m_activeWorkspace) {
		pos += workspace->m_renderOffset->value();
	}

	return pos - g_camPos;
}

static Vector2D g_monitorToWorld(PHLMONITORREF monitor, const Vector2D& monPos) {
	if (!monitor)
		return monPos + g_camPos;
	auto pos = monPos + monitor->m_position;
	pos -= monitor->m_size / 2; // pos relative to monitor center

	if (const auto& workspace = monitor->m_activeWorkspace) {
		pos -= workspace->m_renderOffset->value();
	}

	return pos + g_camPos;
}

static Vector2D g_getPointerPos() {
	const auto ppos = g_pInputManager->getMouseCoordsInternal();
	const auto monitor = State::CMonitorQuery(*State::monitorState()).vec(ppos).run();
	return g_monitorToWorld(monitor, ppos);
}

static CBox g_worldToMonitor(PHLMONITORREF monitor, const CBox& worldBox) {
	return CBox(g_worldToMonitor(monitor, worldBox.pos()), worldBox.size());
}

static CBox g_monitorToWorld(PHLMONITORREF monitor, const CBox& monBox) {
	return CBox(g_monitorToWorld(monitor, monBox.pos()), monBox.size());
}

static void drawBox(const CHyprColor& color, const CBox& _box) {
	const auto monitor = g_primaryMonitor();
	if (!monitor)
		return;
	const auto box = g_worldToMonitor(monitor, _box);
	const auto scaledBox = box.copy().scale(monitor->m_scale);

	CBorderPassElement::SBorderData rdata = {.box = scaledBox, .grad1 = color, .grad2 = color};
	g_pHyprRenderer->m_renderPass.add(makeUnique<CBorderPassElement>(rdata));

	g_pHyprRenderer->damageBox(box.copy().expand(1));
}
