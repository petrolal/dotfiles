#pragma once

#include "Hittable.cpp"
#include "PhysicsBody.cpp"
#include <desktop/view/Window.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <hyprutils/memory/Atomic.hpp>
#include <render/Renderer.hpp>

class Window : public PhysicsBody, public Hittable {
  public:
	const float FLASH_FADE_SPEED = 5.0; // fades in 1/FLASH_FADE_SPEED seconds

	PHLWINDOW m_window;
	float m_flashAlpha = 0.0f;

	Window(PHLWINDOW window);
	~Window();

	bool isVisible() override;

	void tick(double dt) override;

	void hit(const PhysicsBody::Collision& collision, int damage) override;
};
