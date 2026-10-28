#include "Body.hpp"
#include <iostream>

Body::Body(Transform transform) : m_pos(transform.getPos()), m_scale(transform.getScale()), m_rot(transform.getRot()) {}

Body::~Body() {
	for (auto& node : m_children)
		node->dispose();
	m_children.clear();
}

void Body::tick(double dt) {
	std::vector<SP<Body>> childrenCopy = m_children; // copy children in case they're modified during a tick
	for (auto& node : childrenCopy) {
		node->tick(dt);
	}
	std::erase_if(m_children, [](const auto& node) {
		return node->is_disposed;
	});
}

void Body::dispose() {
	m_self.reset();
	is_disposed = true;
}

bool Body::isVisible() { return m_isVisible; }

Transform Body::getTransform() const {
	return {m_pos, m_scale, m_rot};
}

Transform Body::getWorldTransform() const {
	if (m_parent.get())
		return m_parent->getWorldTransform().multiply(getTransform());
	return getTransform();
}

Vector2D Body::getWorldPos() const {
	return getWorldTransform().getPos();
}

void Body::printTree(int level) {
	for (int i = 0; i < level; i++)
		std::cerr << "  ";
	std::cerr << "- " << (void*)m_self.get() << " (" << m_name << ")\n";
	for (const auto& node : m_children) {
		node->printTree(level + 1);
	}
}

void Body::forEachChildRecursive(std::function<void(SP<Body>)> delegate) {
	for (auto& node : m_children) {
		node->forEachChildRecursive(delegate);
		delegate(node);
	}
}

Vector2D Body::getLookDir() {
	// idk why I have to do all these negatives, very stupid
	return -Vector2D{sin(-m_rot), cos(-m_rot)};
}
