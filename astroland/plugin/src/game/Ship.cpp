#include "Ship.hpp"
#include "Bullet.hpp"
#include "Sprite.hpp"
#include "globals.hpp"
#include <cmath>
#include <cstdlib>
#include <desktop/state/FocusState.hpp>

void Ship::onInitialized() {
	instantiate<Sprite>(assetPath("ship.png"), Transform{{0, 0}, {0.5, 0.5}});
}

void Ship::tick(double dt) {
	// accel
	m_velocity.x += g_inputVec.x * ACCEL * dt;
	m_velocity.y += g_inputVec.y * ACCEL * dt;
	auto speed = m_velocity.size();
	if (speed > MAX_SPEED) {
		m_velocity = m_velocity / speed * MAX_SPEED;
		speed = MAX_SPEED;
	}

	const auto pointerPos = g_getPointerPos();
	const auto worldPos = getWorldPos();
	auto dirToPointer = pointerPos - worldPos;
	dirToPointer /= dirToPointer.size();

	if (!Desktop::focusState()->window()) {
		if (g_isRotLeftDown || g_isRotRightDown) {
			const auto rotDir = (-g_isRotLeftDown + g_isRotRightDown);
			m_children[0]->m_rot += rotDir * ROT_SPEED * dt;
		} else {
			const auto desiredRot = std::atan2(dirToPointer.y, dirToPointer.x) + ANGLE_90;
			m_children[0]->m_rot = slerp(m_children[0]->m_rot, desiredRot, dt * ROT_SPEED);
		}
	}

	moveAndCollide(dt);

	PhysicsBody::tick(dt);

	const auto facingDir = Vector2D{std::cos(m_children[0]->m_rot - ANGLE_90), std::sin(m_children[0]->m_rot - ANGLE_90)};
	const auto shootDir = (g_isRotLeftDown || g_isRotRightDown) ? facingDir : dirToPointer;

	if ((m_isShooting || g_isShootKeyDown) && m_shootTimer >= TIME_BETWEEN_SHOTS) {
		ma_engine_play_sound(&g_ma, assetPath(std::format("shoot_{}.wav", std::rand() % 3)).c_str(), nullptr);
		// Spawn a bullet
		g_scene->instantiate<Bullet>(shootDir, worldPos + shootDir * m_size.x / 2);
		m_shootTimer = 0;
	} else
		m_shootTimer += dt;
}

double Ship::slerp(double angle1, double angle2, double alpha) {
	auto diff = angle2 - angle1;

	// Normalize difference to [-PI, PI]
	diff = std::fmod(diff + 3.0f * M_PI, 2.0f * M_PI) - M_PI;

	return angle1 + alpha * diff;
}
