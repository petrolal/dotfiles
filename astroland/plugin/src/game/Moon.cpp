#include "Moon.hpp"
#include "AnimatedSprite.hpp"
#include "Sprite.hpp"
#include "Window.hpp"
#include "globals.hpp"
#include <algorithm>
#include <cstdlib>

Moon::~Moon() {
	g_moonCount = std::max(0.0, g_moonCount - 1);
}

void Moon::onInitialized() {
	if (g_loonMoons && g_loonMoons->value()) {
		instantiate<AnimatedSprite>(assetPath("vax.gif"), true, Transform{{0, 0}, {0.3, 0.3}});
	} else {
		instantiate<Sprite>(assetPath(std::format("moon_{}.png", rand() % 3)), Transform{{0, 0}, {0.5, 0.5}});
	}
}

void Moon::tick(double dt) {
	const auto collisions = moveAndCollide(dt);
	for (const auto& c : collisions) {
		if (dc<Window*>(c.body)) {
			hit(c, m_health);
			return;
		}
	}

	PhysicsBody::tick(dt);

	if (m_velocity.size() > MAX_SPEED) {
		m_velocity = m_velocity / m_velocity.size() * MAX_SPEED;
	}

	if (m_pos.distance(g_camPos) > 1000)
		dispose();
}

void Moon::hit(const PhysicsBody::Collision& collision, int damage) {
	m_health -= damage;
	if (m_health <= 0) {
		ma_engine_play_sound(&g_ma, assetPath(std::format("explode_{}.wav", std::rand() % 1)).c_str(), nullptr);
		g_scene->instantiate<AnimatedSprite>(assetPath("explode.gif"), false, Transform{m_pos, {0.5, 0.5}});
		camShake(20);
		dispose();
	}
}
