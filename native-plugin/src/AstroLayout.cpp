#include "Window.hpp"
#include "globals.hpp"
#include <hyprland/src/desktop/DesktopTypes.hpp>
#include <hyprland/src/layout/LayoutManager.hpp>
#include <hyprland/src/layout/algorithm/Algorithm.hpp>
#include <hyprland/src/layout/algorithm/TiledAlgorithm.hpp>
#include <hyprland/src/layout/space/Space.hpp>
#include <hyprland/src/layout/target/Target.hpp>
#include <hyprland/src/state/MonitorState.hpp>

// Ported from `AstroLayout.cpp`, unchanged except recalculate(): window
// position/size now come from the Kotlin/Native engine's physics
// simulation (astro_window_x/y/w/h) instead of a locally-owned Body.
class AstroLayout : public Layout::ITiledAlgorithm {
  public:
    std::vector<WP<Layout::ITarget>> m_targets;

    void newTarget(SP<Layout::ITarget> target) override {
        m_targets.emplace_back(target);
        recalculate();
    }

    void movedTarget(SP<Layout::ITarget> target, std::optional<Vector2D> focalPoint = std::nullopt) override {
        newTarget(target);
    }

    void removeTarget(SP<Layout::ITarget> target) override {
        std::erase_if(m_targets, [&target](const auto& t) { return !t || t.lock() == target; });
        recalculate();
    }

    void resizeTarget(const Vector2D& delta, SP<Layout::ITarget> target, Layout::eRectCorner corner = Layout::CORNER_NONE) override {
        recalculate();
    }

    void recalculate(Layout::eRecalculateReason reason = Layout::RECALCULATE_REASON_UNKNOWN) override {
        auto targets = liveTargets();
        if (targets.empty())
            return;

        const auto monitor = State::monitorState()->monitors()[0];
        const Vector2D camPos = {astro_cam_x(), astro_cam_y()};

        for (const auto& target : targets) {
            const auto window = target->window();
            if (!window)
                continue;

            const auto astroWindow = getNodeForWindow(window);
            const Vector2D size = {astro_window_w(astroWindow->m_id), astro_window_h(astroWindow->m_id)};
            const Vector2D worldPos = {astro_window_x(astroWindow->m_id), astro_window_y(astroWindow->m_id)};
            const auto monitorPos = g_worldToMonitor(monitor, worldPos - size / 2, camPos);

            const CBox newBox = CBox{monitorPos, size};
            if (target->position() != newBox)
                target->setPositionGlobal(newBox);
        }
    }

    void swapTargets(SP<Layout::ITarget> a, SP<Layout::ITarget> b) override {
        auto ia = std::ranges::find_if(m_targets, [&a](const auto& t) { return t.lock() == a; });
        auto ib = std::ranges::find_if(m_targets, [&b](const auto& t) { return t.lock() == b; });

        if (ia != m_targets.end() && ib != m_targets.end())
            std::iter_swap(ia, ib);
        else {
            if (ia != m_targets.end())
                *ia = b;
            if (ib != m_targets.end())
                *ib = a;
        }

        recalculate();
    }

    void moveTargetInDirection(SP<Layout::ITarget> t, Math::eDirection dir, bool silent) override {
        auto it = std::ranges::find_if(m_targets, [&t](const auto& target) { return target.lock() == t; });
        if (it == m_targets.end())
            return;

        if ((dir == Math::DIRECTION_LEFT || dir == Math::DIRECTION_UP) && it != m_targets.begin())
            std::iter_swap(it, std::prev(it));
        else if ((dir == Math::DIRECTION_RIGHT || dir == Math::DIRECTION_DOWN) && std::next(it) != m_targets.end())
            std::iter_swap(it, std::next(it));
        else
            return;

        recalculate();
    }

    Config::ErrorResult layoutMsg(const std::string_view& sv) override {
        return {};
    }

    std::optional<Vector2D> predictSizeForNewTarget() override {
        auto parent = m_parent.lock();
        auto space  = parent ? parent->space() : nullptr;
        if (!space)
            return std::nullopt;
        return space->workArea().size();
    }

    SP<Layout::ITarget> getNextCandidate(SP<Layout::ITarget> old) override {
        auto targets = liveTargets();
        if (targets.empty())
            return nullptr;

        auto it = std::ranges::find(targets, old);
        if (it == targets.end())
            return targets.back();

        if (targets.size() == 1)
            return nullptr;

        auto next = std::next(it);
        if (next == targets.end())
            next = targets.begin();

        return *next;
    }

    std::vector<SP<Layout::ITarget>> liveTargets() {
        std::erase_if(m_targets, [](const auto& target) { return !target || !target.lock(); });

        std::vector<SP<Layout::ITarget>> result;
        for (const auto& target : m_targets) {
            if (const auto locked = target.lock(); locked)
                result.emplace_back(locked);
        }
        return result;
    }
};
