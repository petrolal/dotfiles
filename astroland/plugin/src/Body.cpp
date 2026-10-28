#pragma once

#include "Transform.cpp"
#include "globals.hpp"
#include <hyprland/src/desktop/Workspace.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <hyprutils/memory/SharedPtr.hpp>
#include <iostream>

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

	Body(Transform transform = {}) : m_pos(transform.getPos()), m_scale(transform.getScale()), m_rot(transform.getRot()) {}
	~Body() {
		for (auto& node : m_children)
			node->dispose();
		m_children.clear();
	}

	virtual void onInitialized() {}

	virtual void tick(double dt) {
		std::vector<SP<Body>> childrenCopy = m_children; // copy children in case they're modified during a tick
		for (auto& node : childrenCopy) {
			node->tick(dt);
		}
		std::erase_if(m_children, [](const auto& node) {
			return node->is_disposed;
		});
	}

	template <typename U, typename... Args>
	SP<U> instantiate(Args&&... args) {
		const auto child = makeShared<U>(args...);
		child->m_self = child;
		child->m_parent = m_self;
		child->onInitialized();
		m_children.push_back(child);
		return child;
	}

	virtual void dispose() {
		m_self.reset();
		is_disposed = true;
	}

	virtual bool isVisible() { return m_isVisible; }

	Transform getTransform() const {
		return {m_pos, m_scale, m_rot};
	}

	Transform getWorldTransform() const {
		if (m_parent.get())
			return m_parent->getWorldTransform().multiply(getTransform());
		return getTransform();
	}

	Vector2D getWorldPos() const {
		return getWorldTransform().getPos();
	}

	void printTree(int level = 0) {
		for (int i = 0; i < level; i++)
			std::cerr << "  ";
		std::cerr << "- " << (void*)m_self.get() << " (" << m_name << ")\n";
		for (const auto& node : m_children) {
			node->printTree(level + 1);
		}
	}

	void forEachChildRecursive(std::function<void(SP<Body>)> delegate) {
		for (auto& node : m_children) {
			node->forEachChildRecursive(delegate);
			delegate(node);
		}
	}

	Vector2D getLookDir() {
		// idk why I have to do all these negatives, very stupid
		return -Vector2D{sin(-m_rot), cos(-m_rot)};
	}
};
