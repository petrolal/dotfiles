#pragma once

#include "Hittable.cpp"
#include "Moon.cpp"
#include "PhysicsBody.cpp"
#include "Sprite.cpp"
#include "Transform.cpp"
#include "globals.hpp"
#include <debug/log/Logger.hpp>
#include <helpers/Color.hpp>
#include <hyprland/src/desktop/Workspace.hpp>
#include <hyprland/src/managers/input/InputManager.hpp>
#include <hyprland/src/plugins/PluginAPI.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprland/src/render/Texture.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <managers/KeybindManager.hpp>
#include <render/pass/TexPassElement.hpp>

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

	void onInitialized() override {
		instantiate<Sprite>(assetPath("bullet.png"), Transform{{0, 0}, {1, 1}});
	}

	void tick(double dt) override {
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
};
