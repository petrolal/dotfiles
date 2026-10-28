#pragma once

#include "Bullet.cpp"
#include "PhysicsBody.cpp"
#include "Sprite.cpp"
#include "globals.hpp"
#include <cmath>
#include <cstdlib>
#include <debug/log/Logger.hpp>
#include <desktop/state/FocusState.hpp>
#include <helpers/Color.hpp>
#include <helpers/time/Timer.hpp>
#include <hyprland/src/desktop/Workspace.hpp>
#include <hyprland/src/managers/input/InputManager.hpp>
#include <hyprland/src/plugins/PluginAPI.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprland/src/render/Texture.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <iostream>
#include <managers/KeybindManager.hpp>
#include <render/pass/TexPassElement.hpp>

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

	void onInitialized() override {
		instantiate<Sprite>(assetPath("ship.png"), Transform{{0, 0}, {0.5, 0.5}});
	}

	void tick(double dt) override {
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
			const auto desiredRot = std::atan2(dirToPointer.y, dirToPointer.x) + ANGLE_90;
			m_children[0]->m_rot = slerp(m_children[0]->m_rot, desiredRot, dt * ROT_SPEED);
		}

		moveAndCollide(dt);

		PhysicsBody::tick(dt);

		if (m_isShooting && m_shootTimer >= TIME_BETWEEN_SHOTS) {
			ma_engine_play_sound(&g_ma, assetPath(std::format("shoot_{}.wav", std::rand() % 3)).c_str(), nullptr);
			// Spawn a bullet
			g_scene->instantiate<Bullet>(dirToPointer, worldPos + dirToPointer * m_size.x / 2);
			m_shootTimer = 0;
		} else
			m_shootTimer += dt;
	}

	static double slerp(double angle1, double angle2, double alpha) {
		auto diff = angle2 - angle1;

		// Normalize difference to [-PI, PI]
		diff = std::fmod(diff + 3.0f * M_PI, 2.0f * M_PI) - M_PI;

		return angle1 + alpha * diff;
	}
};
