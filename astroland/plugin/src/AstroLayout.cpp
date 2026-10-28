#include "Window.cpp"
#include "globals.hpp"
#include <hyprland/src/desktop/DesktopTypes.hpp>
#include <hyprland/src/event/EventBus.hpp>
#include <hyprland/src/helpers/signal/Signal.hpp>
#include <hyprland/src/layout/LayoutManager.hpp>
#include <hyprland/src/layout/algorithm/Algorithm.hpp>
#include <hyprland/src/layout/algorithm/TiledAlgorithm.hpp>
#include <hyprland/src/layout/space/Space.hpp>
#include <hyprland/src/layout/target/Target.hpp>

class AstroLayout : public Layout::ITiledAlgorithm {
  public:
	std::vector<WP<Layout::ITarget>> m_targets;

	AstroLayout() {}
	~AstroLayout() {}

	void newTarget(SP<Layout::ITarget> target) override {
		auto it = std::ranges::find_if(m_targets, [&target](const auto& t) { return t.lock() == target; });
		if (it == m_targets.end())
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
		auto node = getNodeForWindow(target->window());
		node->m_size += delta;
		recalculate();
	}

	void recalculate(Layout::eRecalculateReason reason = Layout::RECALCULATE_REASON_UNKNOWN) override {
		auto targets = liveTargets();
		if (targets.empty())
			return;

		const auto defaultMonitor = g_primaryMonitor();

		for (const auto& target : targets) {
			const auto win = target->window();
			if (!win)
				continue;
			PHLMONITOR winMonitor = nullptr;
			for (const auto& m : State::monitorState()->monitors()) {
				if (m->m_id == win->monitorID()) {
					winMonitor = m;
					break;
				}
			}
			const auto mon = winMonitor ? PHLMONITORREF{winMonitor} : defaultMonitor;
			if (!mon)
				continue;
			auto node = getNodeForWindow(win);
			const auto pos = g_worldToMonitor(mon, node->m_pos - node->m_size / 2);
			const CBox newBox(pos + mon->m_position, node->m_size);
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
		auto space = parent ? parent->space() : nullptr;
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
