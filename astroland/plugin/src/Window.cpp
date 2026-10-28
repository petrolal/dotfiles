#pragma once

#include "Window.hpp"
#include "AnimatedSprite.cpp"
#include "Hittable.cpp"
#include "PhysicsBody.cpp"
#include "Ship.cpp"
#include "Sprite.cpp"
#include <desktop/view/Window.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <hyprutils/memory/Atomic.hpp>
#include <render/Renderer.hpp>

Window::Window(PHLWINDOW window)
	: m_window(window),
	  PhysicsBody(window->size(Desktop::View::IGeometric::GEOMETRIC_CURRENT), Transform(window->position(Desktop::View::IGeometric::GEOMETRIC_CURRENT))),
	  Hittable(10),
	  m_flashAlpha(0.0f) {
	m_size = {300, 300};
	m_pos = g_ship->m_pos + g_ship->m_children[0]->getLookDir() * m_size.size() / 2;
}
Window::~Window() {}

bool Window::isVisible() {
	return m_window->m_isMapped && !m_window->isHidden();
}

void Window::tick(double dt) {
	if (m_flashAlpha > 0) {
		m_flashAlpha -= dt * FLASH_FADE_SPEED;
		g_pHyprRenderer->damageWindow(m_window);
	}

	moveAndCollide(dt);

	PhysicsBody::tick(dt);
}

void Window::hit(const PhysicsBody::Collision& collision, int damage) {
	m_health -= damage;

	if (m_health <= 0) {
		ma_engine_play_sound(&g_ma, assetPath(std::format("explode_{}.wav", std::rand() % 1)).c_str(), nullptr);
		g_scene->instantiate<AnimatedSprite>(assetPath("explode.gif"), false, Transform{m_pos, m_size / 250});
		m_window->sendClose();
		camShake(30);
	} else {
		m_flashAlpha = 1.0;
		const auto localCollisionPos = getWorldTransform().inv().multiply(collision.pos);
		const auto charSprite = instantiate<Sprite>(assetPath("char.png"), Transform{localCollisionPos});
		charSprite->m_lifetime = 2.5;
		charSprite->m_initialLifetime = 2.5;
	}
}

static SP<Window> getNodeForWindow(PHLWINDOW window) {
	for (auto node : g_scene->m_children) {
		if (const auto w = dynamicPointerCast<Window>(node)) {
			if (w->m_window == window) {
				return w;
			}
		}
	}

	const auto node = g_scene->instantiate<Window>(window);
	return node;
}
