#pragma once

#include "AnimatedSprite.cpp"
#include "Hittable.cpp"
#include "PhysicsBody.cpp"
#include "Sprite.cpp"
#include "Window.hpp"
#include "globals.hpp"
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
#include <hyprutils/memory/SharedPtr.hpp>
#include <iostream>
#include <managers/KeybindManager.hpp>
#include <render/pass/TexPassElement.hpp>

class Moon : public PhysicsBody, public Hittable {
  public:
	const double MAX_SPEED = 300;

	Moon(Transform transform = {}) : PhysicsBody({100, 100}, transform), Hittable(5) {
		m_decel = 0;
	}
	~Moon() {
		g_moonCount = std::max(0.0, g_moonCount - 1);
	}

	void onInitialized() override {
		if (g_loonMoons && g_loonMoons->value()) {
			instantiate<AnimatedSprite>(assetPath("vax.gif"), true, Transform{{0, 0}, {0.3, 0.3}});
		} else {
			instantiate<Sprite>(assetPath(std::format("moon_{}.png", rand() % 3)), Transform{{0, 0}, {0.5, 0.5}});
		}
	}

	void tick(double dt) override {
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

	void hit(const PhysicsBody::Collision& collision, int damage) override {
		m_health -= damage;
		if (m_health <= 0) {
			ma_engine_play_sound(&g_ma, assetPath(std::format("explode_{}.wav", std::rand() % 1)).c_str(), nullptr);
			g_scene->instantiate<AnimatedSprite>(assetPath("explode.gif"), false, Transform{m_pos, {0.5, 0.5}});
			camShake(20);
			dispose();
		}
	}
};
