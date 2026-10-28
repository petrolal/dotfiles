#include "AstroLayout.hpp"
#include "Bullet.hpp"
#include "Moon.hpp"
#include "Ship.hpp"
#include "Sprite.hpp"
#include "Window.hpp"
#include "globals.hpp"
#include "hyprland/src/desktop/state/FocusState.hpp"
#include "hyprland/src/layout/LayoutManager.hpp"
#include "hyprland/src/render/ElementRenderer.hpp"
#include <Compositor.hpp>
#include <SharedDefs.hpp>
#include <config/lua/ConfigManager.hpp>
#include <config/lua/bindings/LuaBindingsInternal.hpp>
#include <debug/log/Logger.hpp>
#include <desktop/DesktopTypes.hpp>
#include <desktop/state/ViewHitTester.hpp>
#include <desktop/state/ViewState.hpp>
#include <fstream>
#include <helpers/time/Timer.hpp>
#include <hyprland/src/config/ConfigManager.hpp>
#include <hyprland/src/config/lua/types/LuaConfigColor.hpp>
#include <hyprland/src/event/EventBus.hpp>
#include <hyprland/src/plugins/PluginAPI.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprland/src/render/Texture.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <hyprutils/memory/Casts.hpp>
#include <iostream>
#include <linux/input-event-codes.h>
#include <managers/KeybindManager.hpp>
#include <managers/input/InputManager.hpp>
#include <render/OpenGL.hpp>
#include <render/pass/RectPassElement.hpp>
#include <render/pass/SurfacePassElement.hpp>
#include <wayland-server-core.h>
#include <wayland-server-protocol.h>

extern "C" {
#include <lauxlib.h>
#include <lua.h>
}

using namespace Render;

inline HANDLE PHANDLE = nullptr;

static int onTick(void* data) {
	if (g_tickSource)
		wl_event_source_timer_update(g_tickSource, MS_BETWEEN_TICKS);

	auto dt = g_tickTimer.getSeconds();
	g_tickTimer.reset();
	dt = std::clamp(dt, 0.0f, 0.05f);

	const auto monitor = g_primaryMonitor();
	if (!monitor)
		return 0;

	if (g_spawnMoons->value()) {
		if (g_moonCount < MAX_MOONS && g_moonSpawnT >= S_BETWEEN_WAVES) {
			const auto toSpawn = rand() % MAX_MOONS_PER_WAVE + 1;
			for (int i = 0; i < toSpawn; i++) {
				auto x = rand() % (int)monitor->m_size.x - monitor->m_size.x / 2;
				x *= rand() % 3 - 1;

				double y;
				if (x == 0) {
					y = rand() % (int)monitor->m_size.y - monitor->m_size.y / 2;
					x = monitor->m_size.y / 2 + 100;
					x *= rand() % 2 == 0 ? -1 : 1; // randomize the sign
				} else
					y = monitor->m_size.y / 2 + 100;
				y *= rand() % 2 == 0 ? -1 : 1; // randomize the sign

				const auto pos = g_ship->m_pos + Vector2D((double)x, (double)y);
				const auto moon = g_scene->instantiate<Moon>(Transform(pos));
				const auto dir = g_ship->m_pos - pos;
				moon->m_velocity = dir / dir.size() * 100;
			}
			g_moonCount += toSpawn;
			g_moonSpawnT = 0;
		}
		// only spawn moons if player is moving around
		else if (g_ship->m_velocity.size() > 0)
			g_moonSpawnT += dt;
	}

	g_scene->tick(dt);

	Vector2D targetPos;
	const auto& focusedWindow = Desktop::focusState()->window();
	if (focusedWindow) {
		const auto node = findNodeForWindow(focusedWindow);
		if (node)
			targetPos = node->m_pos;
		else
			targetPos = g_ship->m_pos * monitor->m_scale;
	} else {
		targetPos = g_ship->m_pos * monitor->m_scale;
	}
	const auto CAM_SPEED = 5.0;
	g_actualCamPos.x = std::lerp(g_camPos.x, targetPos.x, dt * CAM_SPEED);
	g_actualCamPos.y = std::lerp(g_camPos.y, targetPos.y, dt * CAM_SPEED);
	g_layoutManager->recalculateMonitor(monitor.lock(), Layout::CLayoutManager::RECALCULATE_MONITOR_REASON_UNKNOWN);

	const auto prevCamPos = g_camPos;

	if (g_camShakeIntensity > 0) {
		g_camPos.x = g_actualCamPos.x + stb_perlin_noise3(g_camShakeNoiseIndex.x, 0, 0, 0, 0, 0) * 2 * g_camShakeIntensity;
		g_camPos.y = g_actualCamPos.y + stb_perlin_noise3(0, g_camShakeNoiseIndex.y, 0, 0, 0, 0) * 2 * g_camShakeIntensity;

		g_camShakeNoiseIndex.x += dt * CAM_SHAKE_NOISE_FREQ;
		g_camShakeNoiseIndex.y += dt * CAM_SHAKE_NOISE_FREQ;

		g_camShakeIntensity -= dt * CAM_SHAKE_FALLOFF_SPEED;
	} else {
		g_camPos = g_actualCamPos;
		g_camShakeOffset = {0, 0};
		g_camShakeIntensity = 0;
	}

	if (!std::isfinite(g_camPos.x) || !std::isfinite(g_camPos.y)) {
		g_camPos = {0, 0};
		g_actualCamPos = {0, 0};
	}
	if (!std::isfinite(g_ship->m_pos.x) || !std::isfinite(g_ship->m_pos.y)) {
		g_ship->m_pos = {0, 0};
		g_ship->m_velocity = {0, 0};
	}

	if (g_camPos != prevCamPos) {
		g_pHyprRenderer->damageMonitor(monitor.lock());
	}

	return 0;
}

static CFunctionHook* addHook(const std::string& className, const std::string& methodName, const void* fnPtr) {
	const auto all = HyprlandAPI::findFunctionsByName(PHANDLE, methodName);
	const auto found = std::find_if(all.begin(), all.end(), [&](const auto& fn) {
		return fn.demangled.contains(className + "::" + methodName + "(");
	});
	if (found == all.end()) {
		HyprlandAPI::addNotification(PHANDLE, "[astroland] failed to find function for hook " + className + "::" + methodName, CHyprColor{1.0, 1.0, 0.2, 1.0}, 10000);
		return nullptr;
	}
	const auto hook = HyprlandAPI::createFunctionHook(PHANDLE, found->address, fnPtr);
	if (!hook || !hook->hook()) {
		HyprlandAPI::addNotification(PHANDLE, "[astroland] failed to create hook " + className + "::" + methodName, CHyprColor{1.0, 1.0, 0.2, 1.0}, 10000);
		return nullptr;
	}
	return hook;
}

void drawSurfaceHook(void* thisptr, WP<CSurfacePassElement> element, const CRegion& damage) {
	typedef void original(void*, WP<CSurfacePassElement> element, const CRegion& damage);
	((original*)g_drawSurfaceHook->m_original)(thisptr, element, damage);

	// do nothing if its not a window
	if (!element->m_data.pWindow)
		return;

	const auto winNode = findNodeForWindow(element->m_data.pWindow);
	if (!winNode)
		return;

	const auto alpha = winNode->m_flashAlpha;
	if (alpha <= 0.001f)
		return;

	const auto self = (IElementRenderer*)thisptr;
	const auto data = element->m_data;
	auto& renderData = g_pHyprRenderer->m_renderData;

	auto rounding = data.rounding;
	auto roundingPower = data.roundingPower;
	rounding -= 1; // to fix a border issue
	if (data.dontRound) {
		rounding = 0;
		roundingPower = 2.0f;
	}

	auto windowBox = element->getTexBox();
	windowBox.scale(data.pMonitor->m_scale);
	windowBox.round();
	if (windowBox.width <= 1 || windowBox.height <= 1) {
		return;
	}

	auto cancelRender = false;
	auto clipRegion = element->visibleRegion(cancelRender);
	if (cancelRender)
		return;

	const auto rdata = CRectPassElement::SRectData{
		.box = windowBox,
		.color = {1.0, 1.0, 1.0, alpha},
		.round = rounding,
		.roundingPower = roundingPower,
		.clipBox = clipRegion.getExtents(),
	};
	self->drawElement(
		makeShared<CRectPassElement>(rdata),
		renderData.damage.copy().intersect(windowBox));
}

int luaPlaceShip(lua_State* L) {
	g_ship->m_pos.x = lua_tonumber(L, 1);
	g_ship->m_pos.y = lua_tonumber(L, 2);
	g_ship->m_rot = lua_tonumber(L, 3);
	g_ship->m_velocity.x = lua_tonumber(L, 4);
	g_ship->m_velocity.y = lua_tonumber(L, 5);
	return 0;
}

int luaSpawnBullet(lua_State* L) {
	const auto bullet = g_scene->instantiate<Bullet>(Vector2D{}, Vector2D{});
	bullet->m_pos.x = lua_tonumber(L, 1);
	bullet->m_pos.y = lua_tonumber(L, 2);
	bullet->m_rot = lua_tonumber(L, 3);
	bullet->m_isVisible = lua_toboolean(L, 4);
	bullet->m_size.x = lua_tonumber(L, 5);
	bullet->m_size.y = lua_tonumber(L, 6);
	bullet->m_velocity.x = lua_tonumber(L, 7);
	bullet->m_velocity.y = lua_tonumber(L, 8);
	return 0;
}

int luaSpawnMoon(lua_State* L) {
	const auto moon = g_scene->instantiate<Moon>();
	moon->m_pos.x = lua_tonumber(L, 1);
	moon->m_pos.y = lua_tonumber(L, 2);
	moon->m_rot = lua_tonumber(L, 3);
	moon->m_isVisible = lua_toboolean(L, 4);
	moon->m_size.x = lua_tonumber(L, 5);
	moon->m_size.y = lua_tonumber(L, 6);
	moon->m_velocity.x = lua_tonumber(L, 7);
	moon->m_velocity.y = lua_tonumber(L, 8);
	return 0;
}

int luaSave(lua_State* L) {
	std::string savePath = (std::getenv("HOME") ? (std::string(std::getenv("HOME")) + "/.config/hypr/save.lua") : "/home/petrolal/.config/hypr/save.lua");
	std::ofstream file(savePath);
	if (file.is_open()) {
		// seriualize g_ship
		file << std::format("hl.plugin.astroland.place_ship({},{},{},{},{})\n", g_ship->m_pos.x, g_ship->m_pos.y, g_ship->m_rot, g_ship->m_velocity.x, g_ship->m_velocity.y);

		// serilize everythign else
		for (const auto& node : g_scene->m_children) {
			if (const auto& ship = dc<Ship const*>(node.get())) {
				continue;
			}

			if (const auto& bullet = dc<Bullet const*>(node.get())) {
				file << std::format("hl.plugin.astroland.spawn_bullet({},{},{},{},{},{},{},{})\n", bullet->m_pos.x, bullet->m_pos.y, bullet->m_rot, bullet->m_isVisible, bullet->m_size.x, bullet->m_size.y, bullet->m_velocity.x, bullet->m_velocity.y);
			} else if (const auto& moon = dc<Moon const*>(node.get())) {
				file << std::format("hl.plugin.astroland.spawn_moon({},{},{},{},{},{},{},{})\n", moon->m_pos.x, moon->m_pos.y, moon->m_rot, moon->m_isVisible, moon->m_size.x, moon->m_size.y, moon->m_velocity.x, moon->m_velocity.y);
			}
		}

		file.close();
	}
	return 0;
}

int luaUnfocus(lua_State* L) {
	Desktop::focusState()->fullWindowFocus(nullptr, Desktop::FOCUS_REASON_OTHER, nullptr, false);
	Desktop::focusState()->resetWindowFocus();
	return 0;
}

APICALL EXPORT std::string PLUGIN_API_VERSION() { return HYPRLAND_API_VERSION; }

APICALL EXPORT PLUGIN_DESCRIPTION_INFO PLUGIN_INIT(HANDLE handle) {
	PHANDLE = handle;

	ma_engine_init(NULL, &g_ma);

	g_tex_bg = loadTex(assetPath("bg.png"));

	g_scene = makeShared<Body>();
	g_scene->m_self = g_scene;
	g_scene->m_name = "scene";
	g_ship = g_scene->instantiate<Ship>();
	g_ship->m_name = "ship";

	auto initTimer = []() {
		g_tickTimer.reset();
		g_tickSource = wl_event_loop_add_timer(g_pCompositor->m_wlEventLoop, onTick, &g_tickTimer);
		if (!g_tickSource || wl_event_source_timer_update(g_tickSource, MS_BETWEEN_TICKS))
			HyprlandAPI::addNotification(PHANDLE, "[astroland] failed to create tick timer", CHyprColor{1.0, 1.0, 0.2, 1.0}, 10000);
	};

	if (g_pCompositor->m_initialized) {
		initTimer();
	} else {
		g_readyListener = Event::bus()->m_events.ready.listen([initTimer]() {
			initTimer();
			g_readyListener.reset();
		});
	}

	g_windowCloseListener = Event::bus()->m_events.window.close.listen([](PHLWINDOW window) {
		if (const auto& windowNode = findNodeForWindow(window)) {
			windowNode->dispose();
		}
		const auto monitor = g_primaryMonitor();
		if (monitor)
			g_layoutManager->recalculateMonitor(monitor.lock(), Layout::CLayoutManager::RECALCULATE_MONITOR_REASON_UNKNOWN);
	});

	g_windowFocusListener = Event::bus()->m_events.window.active.listen([](PHLWINDOW window, Desktop::eFocusReason reason) {
		g_isUpDown = false;
		g_isDownDown = false;
		g_isLeftDown = false;
		g_isRightDown = false;
		g_inputVec = {0, 0};
	});

	g_renderListener = Event::bus()->m_events.render.stage.listen([](eRenderStage stage) {
		const auto monitor = g_primaryMonitor();
		if (!monitor)
			return;

		if (g_pHyprRenderer->m_renderData.pMonitor.lock() != monitor.lock())
			return;

		if (stage == RENDER_POST_WALLPAPER) {
			const double scale = 1.0;
			const auto scaledSize = g_tex_bg->m_size * scale;
			const auto size = scaledSize / monitor->m_scale;

			const auto repeatsX = monitor->m_size.x / size.x + 1;
			const auto repeatsY = monitor->m_size.y / size.y + 1;

			const auto camTopLeft = g_camPos - monitor->m_size / 2;
			const auto tlTilePos = (camTopLeft / size).floor() * size;

			for (size_t y = 0; y < repeatsY; y++) {
				for (size_t x = 0; x < repeatsX; x++) {
					Vector2D pos(tlTilePos.x + x * size.x, tlTilePos.y + size.y * y);
					pos = g_worldToMonitor(monitor, pos);
					const auto rdata = CTexPassElement::SRenderData{.tex = g_tex_bg, .box = CBox(pos, scaledSize)};
					g_pHyprRenderer->m_renderPass.add(makeUnique<CTexPassElement>(rdata));
				}
			}

			// drawBox({1.0, 0.0, 0.0, 1.0}, {tlTilePos, size});
		} else if (stage == RENDER_PRE_WINDOWS) {
			g_scene->forEachChildRecursive([&](SP<Body> child) {
				if (!child->isVisible())
					return;

				if (const auto& sprite = dc<Sprite*>(child.get())) {
					// Window charring overlays are rendered on top of windows in RENDER_POST_WINDOWS;
					// all other sprites (ship, moons, bullets, etc.) render behind windows here.
					if (dc<Window*>(sprite->m_parent.get()))
						return;

					auto [_, scaledBox] = sprite->boundingBoxes();
					CTexPassElement::SRenderData rdata = {.tex = sprite->m_tex, .box = scaledBox, .a = sprite->m_alpha};
					g_pHyprRenderer->m_renderPass.add(makeUnique<CTexPassElement>(rdata));
				}
			});
		} else if (stage == RENDER_POST_WINDOWS) {
			g_scene->forEachChildRecursive([&](SP<Body> child) {
				if (!child->isVisible())
					return;

				if (const auto& sprite = dc<Sprite*>(child.get())) {
					// Window charring texture renders on top of the window with fade out
					if (const auto& w = dc<Window*>(sprite->m_parent.get())) {
						auto [_, scaledBox] = sprite->boundingBoxes();
						CTexPassElement::SRenderData rdata = {.tex = sprite->m_tex, .box = scaledBox, .a = sprite->m_alpha};
						rdata.clipBox = w->m_window->getFullWindowBoundingBox().scale(monitor->m_scale);
						g_pHyprRenderer->m_renderPass.add(makeUnique<CTexPassElement>(rdata));
					}
				}
			});
		}
	});
	g_mouseButtonListener = Event::bus()->m_events.input.mouse.button.listen([](const IPointer::SButtonEvent& e, Event::SCallbackInfo& cbInfo) {
#define LMB 272
		if (e.button == LMB) {
			const auto mouseCoords = g_pInputManager->getMouseCoordsInternal();
			const auto mouseMonitor = State::CMonitorQuery(*State::monitorState()).vec(mouseCoords).run();
			if (!mouseMonitor || mouseMonitor != g_primaryMonitor().lock())
				return;

			const auto w = Desktop::CViewHitTester(*Desktop::viewState())
				.windowAt(mouseCoords, Desktop::View::ALLOW_FLOATING | Desktop::View::RESERVED_EXTENTS | Desktop::View::INPUT_EXTENTS);

			// if window not clicked
			if (!w) {
				g_ship->m_isShooting = false;
				if (e.state == WL_POINTER_BUTTON_STATE_PRESSED) {
					// if no focused window, start shooting
					if (!Desktop::focusState()->window())
						g_ship->m_isShooting = true;
					else {
						Desktop::focusState()->fullWindowFocus(nullptr, Desktop::FOCUS_REASON_OTHER, nullptr, false);
						Desktop::focusState()->resetWindowFocus();
					}
				}

				cbInfo.cancelled = true;
			}
		}
	});
	g_keyListener = Event::bus()->m_events.input.keyboard.key.listen([](const IKeyboard::SKeyEvent& e, Event::SCallbackInfo& cbInfo) {
		// do nothing if a window or a layer surface (app launcher, lockscreen,
		// notification center, ...) holds keyboard focus -- window() alone
		// misses layer-shell surfaces like tofi-drun, which focus via
		// rawSurfaceFocus() and leave window() null, so WASD/arrows kept
		// getting eaten here instead of reaching the launcher.
		if (Desktop::focusState()->window() || Desktop::focusState()->surface())
			return;

		const auto isPressed = e.state == WL_KEYBOARD_KEY_STATE_PRESSED;
		if (e.keycode == KEY_W || e.keycode == KEY_W + 8 || e.keycode == KEY_UP || e.keycode == KEY_UP + 8) {
			g_isUpDown = isPressed;
			cbInfo.cancelled = true;
		}
		if (e.keycode == KEY_S || e.keycode == KEY_S + 8 || e.keycode == KEY_DOWN || e.keycode == KEY_DOWN + 8) {
			g_isDownDown = isPressed;
			cbInfo.cancelled = true;
		}
		if (e.keycode == KEY_A || e.keycode == KEY_A + 8 || e.keycode == KEY_LEFT || e.keycode == KEY_LEFT + 8) {
			g_isLeftDown = isPressed;
			cbInfo.cancelled = true;
		}
		if (e.keycode == KEY_D || e.keycode == KEY_D + 8 || e.keycode == KEY_RIGHT || e.keycode == KEY_RIGHT + 8) {
			g_isRightDown = isPressed;
			cbInfo.cancelled = true;
		}
		g_inputVec = {-g_isLeftDown + g_isRightDown, -g_isUpDown + g_isDownDown};
	});

	g_drawSurfaceHook = addHook("IElementRenderer", "drawSurface", (void*)drawSurfaceHook);

	HyprlandAPI::addTiledAlgo(PHANDLE, "astrolayout", &typeid(AstroLayout), []() {
		return makeUnique<AstroLayout>();
	});

	g_spawnMoons = makeShared<Config::Values::CBoolValue>("plugin:astroland:spawn_moons", "", false);
	g_loonMoons = makeShared<Config::Values::CBoolValue>("plugin:astroland:loon_moons", "", false);
	HyprlandAPI::addConfigValueV2(PHANDLE, g_spawnMoons);
	HyprlandAPI::addConfigValueV2(PHANDLE, g_loonMoons);

	HyprlandAPI::addLuaFunction(PHANDLE, "astroland", "place_ship", luaPlaceShip);
	HyprlandAPI::addLuaFunction(PHANDLE, "astroland", "spawn_bullet", luaSpawnBullet);
	HyprlandAPI::addLuaFunction(PHANDLE, "astroland", "spawn_moon", luaSpawnMoon);

	HyprlandAPI::addLuaFunction(PHANDLE, "astroland", "save", luaSave);
	HyprlandAPI::addLuaFunction(PHANDLE, "astroland", "unfocus", luaUnfocus);

	HyprlandAPI::reloadConfig();

	return {"astroland", "Astroid-esque gamifification of Hyprland", "zacoons", VERSION};
}

APICALL EXPORT void PLUGIN_EXIT() {
	ma_engine_uninit(&g_ma);

	wl_event_source_remove(g_tickSource);

	g_drawSurfaceHook->unhook();
}
