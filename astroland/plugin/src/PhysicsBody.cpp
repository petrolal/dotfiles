#pragma once

#include "Body.cpp"
#include "globals.hpp"
#include <helpers/Color.hpp>
#include <hyprutils/math/Box.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <iostream>
#include <render/pass/BorderPassElement.hpp>

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

	std::vector<Collision> moveAndCollide(double dt) {
		std::vector<Collision> collisions = {};

		// decel
		const auto decelThisTick = m_decel * dt;
		const auto speed = m_velocity.size();
		if (speed > 0 && decelThisTick < speed) {
			const auto dir = m_velocity / speed;
			m_velocity -= dir * decelThisTick;
		} else {
			m_velocity = {0, 0}; // the object stops, or was stopped already
		}

		auto pos = getWorldPos();
		auto newPos = pos + m_velocity * dt;

		g_scene->forEachChildRecursive([&](SP<Body> node) {
			if (!node->isVisible())
				return;

			const auto& other = dc<PhysicsBody*>(node.get());
			if (!other || other == this || !other->m_isSolid)
				return;

			const CBox box = getWorldBoundingBox();
			const CBox otherBox = other->getWorldBoundingBox();

			// In case we're inside the other node
			if (m_velocity.size() == 0 && box.overlaps(otherBox)) {
				auto normal = newPos - otherBox.middle();
				if (normal.size() == 0)
					normal = {1.0, 0.0};
				else
					normal /= normal.size();

				if (m_isSolid) {
					// Collision response
					newPos += normal * 500.0 * dt;
				}

				const auto isect = box.intersection(otherBox);
				collisions.push_back({.body = other, .pos = newPos, .normal = normal});
			} else {
				// Swept AABB Collision Detection https://www.youtube.com/watch?v=3dIiTo7mlnU
				const auto isect = rayIsectBox(pos, newPos, boxExpand(otherBox, m_size));
				if (isect != -1) {
					auto rayDir = (newPos - pos);
					rayDir /= rayDir.size();
					const auto hitPos = pos + rayDir * isect;

					// https://www.youtube.com/watch?v=nh37K1UMe_s
					Vector2D normal = {0, 0};
					const auto d = hitPos - otherBox.middle();
					const auto p = otherBox.size() / 2 - Vector2D{abs(d.x), abs(d.y)};
					if (p.x < p.y)
						normal.x = (d.x > 0) - (d.x < 0);
					else
						normal.y = (d.y > 0) - (d.y < 0);

					if (m_isSolid) {
						// Collision response: equal and opposite
						const auto reaction = normal * m_velocity.size() / 2;
						other->m_velocity -= reaction;
						m_velocity += reaction;
					}
					newPos = pos + m_velocity * dt;

					collisions.push_back({.body = other, .pos = hitPos, .normal = normal});
				}
			}

			// drawBox({1.0, 0.0, 0.0, 1.0}, box);
			// drawBox({0.0, 0.0, 1.0, 1.0}, otherBox);
		});

		m_pos = newPos;

		return collisions;
	}

	Vector2D getWorldSize() {
		return m_size * getWorldTransform().getScale();
	}

	CBox getWorldBoundingBox() {
		const auto worldTransform = getWorldTransform();
		const auto size = m_size * worldTransform.getScale();
		return {worldTransform.getPos() - size / 2, size};
	}

	template <typename T>
	static uint8_t sign(T x) {
		if (x >= 0)
			return 1;
		else
			return -1;
	}

	static Vector2D boxMin(const CBox& box) {
		return box.pos();
	}

	static Vector2D boxMax(const CBox& box) {
		return box.pos() + box.size();
	}

	static CBox boxExpand(const CBox& box, const Vector2D& size) {
		return {
			{box.x - size.x / 2, box.y - size.y / 2},
			{box.w + size.x, box.h + size.y},
		};
	}

	// https://tavianator.com/2015/ray_box_nan.html
	static double rayIsectBox(const Vector2D& p1, const Vector2D& p2, const CBox& box) {
		const auto rayPos = p1;
		auto rayDir = p2 - p1;
		const auto rayLen = rayDir.size();
		rayDir /= rayLen;

		using namespace std;

		auto t1 = (boxMin(box).x - rayPos.x) / rayDir.x;
		auto t2 = (boxMax(box).x - rayPos.x) / rayDir.x;
		auto tmin = min(t1, t2);
		auto tmax = max(t1, t2);

		t1 = (boxMin(box).y - rayPos.y) / rayDir.y;
		t2 = (boxMax(box).y - rayPos.y) / rayDir.y;
		tmin = max(tmin, min(t1, t2));
		tmax = min(tmax, max(t1, t2));

		if (tmin > rayLen)
			return -1;
		return tmax > max(tmin, 0.0) ? max(tmin, 0.0) : -1;
	}
};
