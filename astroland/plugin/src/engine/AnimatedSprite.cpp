#include "AnimatedSprite.hpp"
#include "gifdec.h"
#include "globals.hpp"
#include <cairo.h>
#include <cstdlib>
#include <unordered_map>
#include <vector>

struct CachedGif {
	std::vector<SP<Render::ITexture>> frames;
	double frameDelay = 0.05;
};

static std::unordered_map<std::string, CachedGif> g_cachedGifs;

static CachedGif* getOrLoadGif(const std::string& gifPath) {
	const auto it = g_cachedGifs.find(gifPath);
	if (it != g_cachedGifs.end()) {
		return &it->second;
	}

	gd_GIF* gif = gd_open_gif(gifPath.c_str());
	if (!gif) {
		Log::logger->log(Log::ERR, "Failed to open GIF: {}", gifPath);
		return nullptr;
	}

	CachedGif cached;
	cached.frameDelay = std::max(0.01, (double)gif->gce.delay * 0.01);
	const auto gifSize = gif->width * gif->height;
	auto frame = (uint8_t*)malloc(gifSize * 3);
	auto frame2 = (uint8_t*)malloc(gifSize * 4);

	while (gd_get_frame(gif) != -1) {
		gd_render_frame(gif, frame);

		for (int i = 0; i < gifSize; i++) {
			const auto b = frame[(i * 3) + 0];
			const auto g = frame[(i * 3) + 1];
			const auto r = frame[(i * 3) + 2];
			const auto a = b + g + r > 0;
			frame2[(i * 4) + 0] = r * a;
			frame2[(i * 4) + 1] = g * a;
			frame2[(i * 4) + 2] = b * a;
			frame2[(i * 4) + 3] = a * 255;
		}

		const auto cr = cairo_image_surface_create_for_data(frame2, CAIRO_FORMAT_ARGB32, gif->width, gif->height, gif->width * 4);
		auto tex = g_pHyprRenderer->createTexture(cr);
		cairo_surface_destroy(cr);

		if (tex) {
			cached.frames.push_back(tex);
		}
	}

	free(frame);
	free(frame2);
	gd_close_gif(gif);

	auto [inserted, _] = g_cachedGifs.emplace(gifPath, std::move(cached));
	return &inserted->second;
}

AnimatedSprite::AnimatedSprite(std::string gifPath, bool repeat, Transform transform) : m_repeat(repeat), Sprite(transform) {
	m_texPath = gifPath;
	m_cached = getOrLoadGif(gifPath);
	if (m_cached && !m_cached->frames.empty()) {
		m_tex = m_cached->frames[0];
	}
}

void AnimatedSprite::tick(double dt) {
	if (!isVisible()) {
		Body::tick(dt);
		return;
	}

	if (m_cached && !m_cached->frames.empty()) {
		m_t += dt;
		if (m_t >= m_cached->frameDelay) {
			m_t = 0;
			m_currentFrame++;
			if (m_currentFrame >= (int)m_cached->frames.size()) {
				if (m_repeat) {
					m_currentFrame = 0;
				} else {
					dispose();
					return;
				}
			}
			m_tex = m_cached->frames[m_currentFrame];
		}
	}

	Sprite::tick(dt);
}
