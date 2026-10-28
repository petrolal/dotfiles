#include "Bullet.hpp"
#include "Hittable.hpp"
#include "Moon.hpp"
#include "Sprite.hpp"
#include "globals.hpp"
#include <cmath>

void Bullet::onInitialized() {
	instantiate<Sprite>(assetPath("bullet.png"), Transform{{0, 0}, {1, 1}});
}

void Bullet::tick(double dt) {
	m_timeAlive += dt;
	if (m_timeAlive >= TTL_S) {
		dispose();
		return;
	}

	const auto collisions = moveAndCollide(dt);

	for (const auto& collision : collisions) {
		if (g_loonMoons->value()) {
			if (const auto& moon = dc<Moon*>(collision.body)) {
				m_velocity += collision.normal * m_velocity.size();
				return;
			}
		}

		if (const auto& hittable = dc<Hittable*>(collision.body)) {
			hittable->hit(collision, DAMAGE);
			dispose();
			return;
		}
	}

	m_children[0]->m_rot = std::atan2(m_velocity.y, m_velocity.x) + ANGLE_90;

	PhysicsBody::tick(dt);
}
