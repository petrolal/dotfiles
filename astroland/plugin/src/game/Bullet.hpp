#pragma once

#include "PhysicsBody.hpp"

class Bullet : public PhysicsBody {
  public:
	const int DAMAGE = 1;
	const double MAX_SPEED = 900.0;
	const double TTL_S = 3.0;

	double m_timeAlive = 0;

	Bullet(Vector2D dir, Vector2D pos) : PhysicsBody({10, 10}, Transform{pos}) {
		m_isSolid = false;
		m_velocity = dir * MAX_SPEED;
		m_decel = 0;
	}
	~Bullet() {}

	void onInitialized() override;
	void tick(double dt) override;
};
