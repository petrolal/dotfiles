#pragma once

#include "Hittable.hpp"
#include "PhysicsBody.hpp"
#include <desktop/view/Window.hpp>
#include <hyprutils/memory/SharedPtr.hpp>

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

// Looked up from AstroLayout.cpp and main.cpp -- external linkage (no
// `static`) is required now that each .cpp is its own translation unit;
// under the old unity build these were `static` but still reachable
// because every file was textually one TU.
SP<Window> findNodeForWindow(PHLWINDOW window);
SP<Window> getNodeForWindow(PHLWINDOW window, bool createIfNotFound = true);
