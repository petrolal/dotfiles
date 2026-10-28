#pragma once

#include "Body.hpp"
#include <hyprutils/math/Box.hpp>
#include <hyprutils/math/Vector2D.hpp>

class PhysicsBody : public Body {
  public:
	bool m_isSolid = true; // can it be passed through?
	Vector2D m_size;
	double m_decel = 500;
	Vector2D m_velocity = {0, 0};

	struct Collision {
		PhysicsBody* body;
		Vector2D pos;
		Vector2D normal;
	};

	PhysicsBody(Vector2D size, Transform transform = {}) : m_size(size), Body(transform) {}
	~PhysicsBody() {}

	std::vector<Collision> moveAndCollide(double dt);

	Vector2D getWorldSize();
	CBox getWorldBoundingBox();

	template <typename T>
	static uint8_t sign(T x) {
		if (x >= 0)
			return 1;
		else
			return -1;
	}

	static Vector2D boxMin(const CBox& box);
	static Vector2D boxMax(const CBox& box);
	static CBox boxExpand(const CBox& box, const Vector2D& size);
	// https://tavianator.com/2015/ray_box_nan.html
	static double rayIsectBox(const Vector2D& p1, const Vector2D& p2, const CBox& box);
};
