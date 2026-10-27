#include "AstroLayout.cpp"
#include "Window.hpp"
#include "globals.hpp"
#include <Compositor.hpp>
#include <SharedDefs.hpp>
#include <debug/log/Logger.hpp>
#include <desktop/DesktopTypes.hpp>
#include <desktop/state/FocusState.hpp>
#include <hyprland/src/config/ConfigManager.hpp>
#include <hyprland/src/config/values/types/BoolValue.hpp>
#include <hyprland/src/event/EventBus.hpp>
#include <hyprland/src/layout/LayoutManager.hpp>
#include <hyprland/src/plugins/PluginAPI.hpp>
#include <hyprland/src/render/ElementRenderer.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprland/src/render/Texture.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <linux/input-event-codes.h>
#include <managers/KeybindManager.hpp>
#include <managers/input/InputManager.hpp>
#include <render/OpenGL.hpp>
#include <render/pass/RectPassElement.hpp>
#include <render/pass/SurfacePassElement.hpp>
#include <render/pass/TexPassElement.hpp>
#include <wayland-server-core.h>
#include <wayland-server-protocol.h>

using namespace Render;

inline HANDLE PHANDLE = nullptr;

// WASD held-state -- forwarded into astro_set_input each change, same
// cancel-on-no-focused-window behavior as the original.
static bool g_isUpDown, g_isDownDown, g_isLeftDown, g_isRightDown;

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

static SP<Render::ITexture> textureForDrawCommand(int kind, int frame) {
    if (kind == SPRITE_EXPLODE || kind == SPRITE_VAX) {
        auto& frames = g_gifFrames[kind];
        if (frames.empty())
            return nullptr;
        return frames[frame % frames.size()];
    }
    if (kind < 0 || kind >= SPRITE_KIND_COUNT)
        return nullptr;
    return g_staticTextures[kind];
}

static int onTick(void* data) {
    auto dt = g_tickTimer.getSeconds();
    g_tickTimer.reset();

    const auto monitor = State::monitorState()->monitors()[0];
    astro_set_viewport(monitor->m_size.x, monitor->m_size.y);
    astro_set_spawn_moons(g_spawnMoons->value());

    const auto pointerWorld = g_getPointerWorldPos();
    astro_set_pointer_world(pointerWorld.x, pointerWorld.y);

    astro_tick((float)dt);

    const auto soundCount = astro_sound_event_count();
    for (int i = 0; i < soundCount; i++)
        playSound((eSoundKind)astro_sound_event_kind(i));
    astro_sound_events_clear();

    const auto closeCount = astro_window_close_event_count();
    for (int i = 0; i < closeCount; i++) {
        const auto id = astro_window_close_event_id(i);
        if (const auto& window = findWindowById(id))
            window->sendClose();
    }
    astro_window_close_events_clear();

    g_layoutManager->recalculateMonitor(monitor, Layout::CLayoutManager::RECALCULATE_MONITOR_REASON_UNKNOWN);

    return wl_event_source_timer_update(g_tickSource, MS_BETWEEN_TICKS);
}

static CFunctionHook* addHook(const std::string& className, const std::string& methodName, const void* fnPtr) {
    const auto all   = HyprlandAPI::findFunctionsByName(PHANDLE, methodName);
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

// Ported from the original's `drawSurfaceHook`: tints a window white at an
// alpha read back from the Kotlin/Native engine's WindowBody.flashAlpha
// (via astro_window_flash_alpha) whenever it's just been shot.
void drawSurfaceHook(void* thisptr, WP<CSurfacePassElement> element, const CRegion& damage) {
    typedef void original(void*, WP<CSurfacePassElement> element, const CRegion& damage);
    ((original*)g_drawSurfaceHook->m_original)(thisptr, element, damage);

    if (!element->m_data.pWindow)
        return;

    const auto astroWindow = getNodeForWindow(element->m_data.pWindow);
    const auto alpha       = astro_window_flash_alpha(astroWindow->m_id);
    if (alpha <= 0)
        return;

    const auto self = (IElementRenderer*)thisptr;
    const auto data = element->m_data;
    auto&      renderData = g_pHyprRenderer->m_renderData;

    auto rounding      = data.rounding;
    auto roundingPower = data.roundingPower;
    rounding -= 1;
    if (data.dontRound) {
        rounding      = 0;
        roundingPower = 2.0f;
    }

    auto windowBox = element->getTexBox();
    windowBox.scale(data.pMonitor->m_scale);
    windowBox.round();
    if (windowBox.width <= 1 || windowBox.height <= 1)
        return;

    auto cancelRender = false;
    auto clipRegion   = element->visibleRegion(cancelRender);
    if (cancelRender)
        return;

    const auto rdata = CRectPassElement::SRectData{
        .box           = windowBox,
        .color         = {1.0, 1.0, 1.0, alpha},
        .round         = rounding,
        .roundingPower = roundingPower,
        .clipBox       = clipRegion.getExtents(),
    };
    self->drawElement(makeShared<CRectPassElement>(rdata), renderData.damage.copy().intersect(windowBox));
}

APICALL EXPORT std::string PLUGIN_API_VERSION() {
    return HYPRLAND_API_VERSION;
}

APICALL EXPORT PLUGIN_DESCRIPTION_INFO PLUGIN_INIT(HANDLE handle) {
    PHANDLE = handle;

    ma_engine_init(NULL, &g_ma);
    loadAllAssets();
    astro_init();

    g_readyListener = Event::bus()->m_events.ready.listen([]() {
        g_tickTimer.reset();
        g_tickSource = wl_event_loop_add_timer(g_pCompositor->m_wlEventLoop, onTick, &g_tickTimer);
        if (!g_tickSource || wl_event_source_timer_update(g_tickSource, MS_BETWEEN_TICKS))
            HyprlandAPI::addNotification(PHANDLE, "[astroland] failed to create tick timer", CHyprColor{1.0, 1.0, 0.2, 1.0}, 10000);
        g_readyListener.reset();
    });

    g_windowCloseListener = Event::bus()->m_events.window.close.listen([](PHLWINDOW window) {
        g_windows.erase(window);
    });

    g_windowFocusListener = Event::bus()->m_events.window.active.listen([](PHLWINDOW window, Desktop::eFocusReason reason) {
        g_isUpDown = g_isDownDown = g_isLeftDown = g_isRightDown = false;
        astro_set_input(0, 0, 0, 0);
    });

    g_renderListener = Event::bus()->m_events.render.stage.listen([](eRenderStage stage) {
        const auto monitor = State::monitorState()->monitors()[0];
        const Vector2D camPos = {astro_cam_x(), astro_cam_y()};

        if (stage == RENDER_POST_WALLPAPER) {
            const auto bg = g_staticTextures[SPRITE_BACKGROUND];
            if (!bg)
                return;

            const auto scaledSize = bg->m_size / monitor->m_scale;
            const auto repeatsX   = monitor->m_size.x / scaledSize.x + 2;
            const auto repeatsY   = monitor->m_size.y / scaledSize.y + 2;

            const auto camTopLeft = camPos - monitor->m_size / 2;
            const auto tlTilePos  = (camTopLeft / scaledSize).floor() * scaledSize;

            for (size_t y = 0; y < repeatsY; y++) {
                for (size_t x = 0; x < repeatsX; x++) {
                    Vector2D pos = {tlTilePos.x + x * scaledSize.x, tlTilePos.y + scaledSize.y * y};
                    pos          = g_worldToMonitor(monitor, pos, camPos);
                    const auto rdata = CTexPassElement::SRenderData{.tex = bg, .box = {pos, scaledSize}};
                    g_pHyprRenderer->m_renderPass.add(makeUnique<CTexPassElement>(rdata));
                }
            }
        } else if (stage == RENDER_POST_WINDOWS) {
            const auto commandCount = astro_draw_command_count();
            for (int i = 0; i < commandCount; i++) {
                const auto kind  = astro_draw_kind(i);
                const auto tex   = textureForDrawCommand(kind, astro_draw_frame(i));
                if (!tex)
                    continue;

                const Vector2D worldPos = {astro_draw_x(i), astro_draw_y(i)};
                const Vector2D scale    = {astro_draw_scale_x(i), astro_draw_scale_y(i)};
                const Vector2D size     = tex->m_size * scale;

                auto pos = g_worldToMonitor(monitor, worldPos - size / 2, camPos);
                CBox box = {pos, size};
                box.rot  = astro_draw_rot(i);

                const auto rdata = CTexPassElement::SRenderData{.tex = tex, .box = box.copy().scale(monitor->m_scale)};
                g_pHyprRenderer->m_renderPass.add(makeUnique<CTexPassElement>(rdata));
            }
        }
    });

    // The original used `vectorToWindowUnified` to tell "clicked on empty
    // desktop" apart from "clicked on a window" before deciding whether to
    // hijack the click as a shot -- that query no longer exists on
    // CCompositor in this Hyprland version (headers expose no direct
    // replacement). Simplified equivalent: just gate shooting on whether a
    // window is currently focused, and never cancel the event -- Hyprland's
    // own normal click-to-focus handling still runs, same as before.
#define LMB 272
    g_mouseButtonListener = Event::bus()->m_events.input.mouse.button.listen([](const IPointer::SButtonEvent& e, Event::SCallbackInfo& cbInfo) {
        if (e.button != LMB)
            return;

        if (e.state == WL_POINTER_BUTTON_STATE_PRESSED)
            astro_set_shooting(Desktop::focusState()->window() ? 0 : 1);
        else
            astro_set_shooting(0);
    });

    g_keyListener = Event::bus()->m_events.input.keyboard.key.listen([](const IKeyboard::SKeyEvent& e, Event::SCallbackInfo& cbInfo) {
        if (Desktop::focusState()->window())
            return;

        const auto isPressed = e.state == WL_KEYBOARD_KEY_STATE_PRESSED;
        if (e.keycode == KEY_W) {
            g_isUpDown = isPressed;
            cbInfo.cancelled = true;
        }
        if (e.keycode == KEY_S) {
            g_isDownDown = isPressed;
            cbInfo.cancelled = true;
        }
        if (e.keycode == KEY_A) {
            g_isLeftDown = isPressed;
            cbInfo.cancelled = true;
        }
        if (e.keycode == KEY_D) {
            g_isRightDown = isPressed;
            cbInfo.cancelled = true;
        }
        astro_set_input(g_isUpDown, g_isDownDown, g_isLeftDown, g_isRightDown);
    });

    g_drawSurfaceHook = addHook("IElementRenderer", "drawSurface", (void*)drawSurfaceHook);

    HyprlandAPI::addTiledAlgo(PHANDLE, "astrolayout", &typeid(AstroLayout), []() { return makeUnique<AstroLayout>(); });

    g_spawnMoons = makeShared<Config::Values::CBoolValue>("plugin:astroland:spawn_moons", "", false);
    HyprlandAPI::addConfigValueV2(PHANDLE, g_spawnMoons);

    HyprlandAPI::reloadConfig();

    return {"astroland", "Kotlin-brained astroid-esque gamification of Hyprland", "petrolal (ported from zacoons' astroland_dots)", "1.0"};
}

APICALL EXPORT void PLUGIN_EXIT() {
    ma_engine_uninit(&g_ma);
    wl_event_source_remove(g_tickSource);
    g_drawSurfaceHook->unhook();
}
