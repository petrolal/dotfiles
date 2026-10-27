#pragma once

#include "../astro_native.h"
#include <desktop/view/Window.hpp>
#include <hyprutils/memory/SharedPtr.hpp>
#include <hyprutils/memory/UniquePtr.hpp>
#include <unordered_map>

// Ported from `Window.cpp`, shrunk down to a thin id-wrapper: all physics
// (position, health, flash alpha) now lives in the Kotlin/Native engine's
// WindowBody (see astro_native.h) -- this class only carries the id link
// and answers isVisible() for the render-stage listener in main.cpp.
class AstroWindow {
  public:
    PHLWINDOW m_window;
    int64_t   m_id;

    explicit AstroWindow(PHLWINDOW window) : m_window(window), m_id(reinterpret_cast<int64_t>(window.get())) {
        astro_window_ensure(m_id);
    }

    ~AstroWindow() {
        astro_window_remove(m_id);
    }

    bool isVisible() const {
        return m_window->m_isMapped && !m_window->isHidden();
    }
};

inline std::unordered_map<PHLWINDOW, UP<AstroWindow>> g_windows;

inline PHLWINDOW findWindowById(int64_t id) {
    for (const auto& [window, astroWindow] : g_windows) {
        if (astroWindow->m_id == id)
            return window;
    }
    return nullptr;
}

inline AstroWindow* getNodeForWindow(const PHLWINDOW& window) {
    auto it = g_windows.find(window);
    if (it != g_windows.end())
        return it->second.get();

    auto [inserted, _] = g_windows.emplace(window, makeUnique<AstroWindow>(window));
    return inserted->second.get();
}
