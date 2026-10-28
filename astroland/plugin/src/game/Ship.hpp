#pragma once

#include "PhysicsBody.hpp"

class Ship : public PhysicsBody {
  public:
	const double MAX_SPEED = 500.0;
	const double ACCEL = 2000.0;
	const double TIME_BETWEEN_SHOTS = 0.1;
	const double ROT_SPEED = 10.0; // rads/s

	double m_shootTimer;
	bool m_isShooting = false;

	Ship() : PhysicsBody({100, 100}) {
		m_decel = 1000;
	}
	~Ship() {}

	void onInitialized() override;
	void tick(double dt) override;

	static double slerp(double angle1, double angle2, double alpha);
};
