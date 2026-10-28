#pragma once

#include <hyprland/src/layout/algorithm/TiledAlgorithm.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <optional>
#include <vector>

class AstroLayout : public Layout::ITiledAlgorithm {
  public:
	std::vector<WP<Layout::ITarget>> m_targets;

	AstroLayout() {}
	~AstroLayout() {}

	void newTarget(SP<Layout::ITarget> target) override;
	void movedTarget(SP<Layout::ITarget> target, std::optional<Vector2D> focalPoint = std::nullopt) override;
	void removeTarget(SP<Layout::ITarget> target) override;
	void resizeTarget(const Vector2D& delta, SP<Layout::ITarget> target, Layout::eRectCorner corner = Layout::CORNER_NONE) override;
	void recalculate(Layout::eRecalculateReason reason = Layout::RECALCULATE_REASON_UNKNOWN) override;
	void swapTargets(SP<Layout::ITarget> a, SP<Layout::ITarget> b) override;
	void moveTargetInDirection(SP<Layout::ITarget> t, Math::eDirection dir, bool silent) override;
	Config::ErrorResult layoutMsg(const std::string_view& sv) override;
	std::optional<Vector2D> predictSizeForNewTarget() override;
	SP<Layout::ITarget> getNextCandidate(SP<Layout::ITarget> old) override;

	std::vector<SP<Layout::ITarget>> liveTargets();
};
