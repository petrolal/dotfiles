#include "AstroLayout.hpp"
#include "Window.hpp"
#include "globals.hpp"
#include <algorithm>
#include <hyprland/src/desktop/DesktopTypes.hpp>
#include <hyprland/src/layout/algorithm/Algorithm.hpp>
#include <hyprland/src/layout/space/Space.hpp>
#include <hyprland/src/layout/target/Target.hpp>

void AstroLayout::newTarget(SP<Layout::ITarget> target) {
	if (!target || !target->window() || !target->window()->m_isMapped || target->window()->isHidden())
		return;
	auto it = std::ranges::find_if(m_targets, [&target](const auto& t) { return t.lock() == target; });
	if (it == m_targets.end())
		m_targets.emplace_back(target);
	recalculate();
}

void AstroLayout::movedTarget(SP<Layout::ITarget> target, std::optional<Vector2D> focalPoint) {
	if (target && target->window() && target->window()->m_isMapped && !target->window()->isHidden()) {
		auto node = getNodeForWindow(target->window());
		if (node) {
			const auto win = target->window();
			PHLMONITOR winMonitor = nullptr;
			for (const auto& m : State::monitorState()->monitors()) {
				if (m->m_id == win->monitorID()) {
					winMonitor = m;
					break;
				}
			}
			const auto defaultMonitor = g_primaryMonitor();
			const auto mon = winMonitor ? PHLMONITORREF{winMonitor} : defaultMonitor;
			if (mon) {
				const auto centerGlobal = target->position().pos() + target->position().size() / 2.0;
				const auto monPos = centerGlobal - mon->m_position;
				node->m_pos = g_monitorToWorld(mon, monPos);
				node->m_velocity = {0, 0};
			}
		}
		newTarget(target);
	}
}

void AstroLayout::removeTarget(SP<Layout::ITarget> target) {
	std::erase_if(m_targets, [&target](const auto& t) { return !t || t.lock() == target; });
	recalculate();
}

void AstroLayout::resizeTarget(const Vector2D& delta, SP<Layout::ITarget> target, Layout::eRectCorner corner) {
	if (!target || !target->window() || !target->window()->m_isMapped || target->window()->isHidden())
		return;
	auto node = getNodeForWindow(target->window());
	if (!node)
		return;
	node->m_size += delta;
	if (node->m_size.x < 100)
		node->m_size.x = 100;
	if (node->m_size.y < 100)
		node->m_size.y = 100;
	recalculate();
}

void AstroLayout::recalculate(Layout::eRecalculateReason reason) {
	auto targets = liveTargets();
	if (targets.empty())
		return;

	const auto defaultMonitor = g_primaryMonitor();

	for (const auto& target : targets) {
		const auto win = target->window();
		if (!win || !win->m_isMapped || win->isHidden())
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
		if (!node)
			continue;
		const auto pos = g_worldToMonitor(mon, node->m_pos - node->m_size / 2);
		const CBox newBox(pos + mon->m_position, node->m_size);
		if (target->position() != newBox)
			target->setPositionGlobal(newBox);
	}
}

void AstroLayout::swapTargets(SP<Layout::ITarget> a, SP<Layout::ITarget> b) {
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

void AstroLayout::moveTargetInDirection(SP<Layout::ITarget> t, Math::eDirection dir, bool silent) {
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

Config::ErrorResult AstroLayout::layoutMsg(const std::string_view& sv) {
	return {};
}

std::optional<Vector2D> AstroLayout::predictSizeForNewTarget() {
	auto parent = m_parent.lock();
	auto space = parent ? parent->space() : nullptr;
	if (!space)
		return std::nullopt;
	return space->workArea().size();
}

SP<Layout::ITarget> AstroLayout::getNextCandidate(SP<Layout::ITarget> old) {
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

std::vector<SP<Layout::ITarget>> AstroLayout::liveTargets() {
	auto parent = m_parent.lock();
	auto mySpace = parent ? parent->space() : nullptr;

	std::erase_if(m_targets, [&](const auto& targetRef) {
		const auto target = targetRef.lock();
		if (!target)
			return true;
		const auto win = target->window();
		if (!win)
			return true;
		if (!win->m_isMapped || win->isHidden())
			return true;
		if (mySpace && target->space() != mySpace)
			return true;
		return false;
	});

	std::vector<SP<Layout::ITarget>> result;
	for (const auto& target : m_targets) {
		if (const auto locked = target.lock(); locked)
			result.emplace_back(locked);
	}
	return result;
}
