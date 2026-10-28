#pragma once

#include "Sprite.cpp"
#include "gifdec.h"
#include <cairo.h>
#include <debug/log/Logger.hpp>
#include <helpers/Color.hpp>
#include <hyprland/src/desktop/Workspace.hpp>
#include <hyprland/src/managers/input/InputManager.hpp>
#include <hyprland/src/plugins/PluginAPI.hpp>
#include <hyprland/src/render/Renderer.hpp>
#include <hyprland/src/render/Texture.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <iostream>
#include <managers/KeybindManager.hpp>
#include <render/pass/TexPassElement.hpp>

class AnimatedSprite : public Sprite {
  public:
	gd_GIF* m_gif;
	uint8_t* m_frame;
	uint8_t* m_frame2;
	double m_t = 0;
	bool m_repeat;

	AnimatedSprite(std::string gifPath, bool repeat, Transform transform = {}) : m_repeat(repeat), Sprite(transform) {
		m_gif = gd_open_gif(gifPath.c_str());
		m_frame = (uint8_t*)malloc(m_gif->width * m_gif->height * 3);
		m_frame2 = (uint8_t*)malloc(m_gif->width * m_gif->height * 4);
		m_texPath = gifPath;
		loadNextFrame();
	}
	~AnimatedSprite() {
		gd_close_gif(m_gif);
		free(m_frame);
		free(m_frame2);
	}

	virtual void tick(double dt) override {
		m_t += dt;
		if (m_t >= m_gif->gce.delay * 0.01) {
			if (!loadNextFrame()) {
				if (m_repeat) {
					gd_rewind(m_gif);
					loadNextFrame();
				} else {
					dispose();
					return;
				}
			}
			m_t = 0;
		}

		Sprite::tick(dt);
	}

	bool loadNextFrame() {
		if (gd_get_frame(m_gif) == -1)
			return false;

		gd_render_frame(m_gif, m_frame);

		const auto gifSize = m_gif->width * m_gif->height;
		auto cIn = m_frame;
		auto cOut = m_frame2;
		for (int i = 0; i < gifSize; i++) {
			const auto b = m_frame[(i * 3) + 0];
			const auto g = m_frame[(i * 3) + 1];
			const auto r = m_frame[(i * 3) + 2];
			const auto a = b + g + r > 0;
			m_frame2[(i * 4) + 0] = r * a;
			m_frame2[(i * 4) + 1] = g * a;
			m_frame2[(i * 4) + 2] = b * a;
			m_frame2[(i * 4) + 3] = a * 255;
		}

		const auto cr = cairo_image_surface_create_for_data(m_frame2, CAIRO_FORMAT_ARGB32, m_gif->width, m_gif->height, m_gif->width * 4);
		m_tex = g_pHyprRenderer->createTexture(cr);
		cairo_surface_destroy(cr);

		return true;
	}
};
