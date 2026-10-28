#pragma once

#include "Transform.hpp"
#include <functional>
#include <hyprutils/math/Vector2D.hpp>
#include <hyprutils/memory/SharedPtr.hpp>
#include <macros.hpp>
#include <string>
#include <vector>

class Body {
  public:
	std::string m_name;
	SP<Body> m_self;
	SP<Body> m_parent;
	std::vector<SP<Body>> m_children;
	bool is_disposed = false;
	bool m_isVisible = true;

	Vector2D m_pos;
	Vector2D m_scale;
	double m_rot; // counter-clockwise, radians

	Body(Transform transform = {});
	// Not virtual in the original -- SP<T> (Hyprutils shared_ptr) captures
	// the concrete type's deleter at construction, so correct destruction
	// through a base SP<Body> doesn't depend on a virtual destructor here.
	~Body();

	virtual void onInitialized() {}

	virtual void tick(double dt);

	// Template: must stay defined in the header (instantiated per concrete
	// derived type at each call site), unlike every other Body method.
	template <typename U, typename... Args>
	SP<U> instantiate(Args&&... args) {
		const auto child = makeShared<U>(args...);
		child->m_self = child;
		child->m_parent = m_self;
		child->onInitialized();
		m_children.push_back(child);
		return child;
	}

	virtual void dispose();

	virtual bool isVisible();

	Transform getTransform() const;
	Transform getWorldTransform() const;
	Vector2D getWorldPos() const;

	void printTree(int level = 0);
	void forEachChildRecursive(std::function<void(SP<Body>)> delegate);
	Vector2D getLookDir();
};
