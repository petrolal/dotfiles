#pragma once

#include "PhysicsBody.cpp"

class Hittable {
  public:
	int m_health;

	Hittable(int health) : m_health(health) {}
	~Hittable() {}

	virtual void hit(const PhysicsBody::Collision& collision, int damage) {}
};
