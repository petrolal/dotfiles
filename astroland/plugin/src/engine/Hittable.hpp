#pragma once

#include "PhysicsBody.hpp"

// Header-only: every member is already trivial/inline.
class Hittable {
  public:
	int m_health;

	Hittable(int health) : m_health(health) {}
	~Hittable() {}

	virtual void hit(const PhysicsBody::Collision& collision, int damage) {}
};
