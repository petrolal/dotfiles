#pragma once

#include "Hittable.hpp"
#include "PhysicsBody.hpp"

class Moon : public PhysicsBody, public Hittable {
  public:
	const double MAX_SPEED = 300;

	Moon(Transform transform = {}) : PhysicsBody({100, 100}, transform), Hittable(5) {
		m_decel = 0;
	}
	~Moon();

	void onInitialized() override;
	void tick(double dt) override;
	void hit(const PhysicsBody::Collision& collision, int damage) override;
};
