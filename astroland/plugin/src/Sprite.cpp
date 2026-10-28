#pragma once

#include "Body.cpp"
#include "globals.hpp"
#include <cfloat>
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

class Sprite : public Body {
  public:
	std::string m_texPath;
	SP<Render::ITexture> m_tex;
	float m_alpha = 1.0f;
	double m_lifetime = -1.0;
	double m_initialLifetime = -1.0;

	CBox m_prevBox;

	Sprite(const std::string& texPath, Transform transform = {}) : Body(transform) {
		m_texPath = texPath;
		m_tex = loadTex(texPath);
	}
	~Sprite() {
		auto [box, _] = boundingBoxes();
		box = expandToFitRotated(box, m_rot).expand(1);
		g_pHyprRenderer->damageBox(box);
	}

	std::pair<CBox, CBox> boundingBoxes() {
		const auto monitor = g_primaryMonitor();
		if (!monitor)
			return std::make_pair(CBox(), CBox());

		// TODO: support multi-monitor properly
		const auto& worldTransform = getWorldTransform();
		const auto& pos = g_worldToMonitor(monitor, worldTransform.getPos());
		const auto& size = m_tex->m_size * worldTransform.getScale();

		CBox box(pos - size / 2, size);
		box.rot = m_rot;

		return std::make_pair(box, box.copy().scale(monitor->m_scale));
	}

	virtual void tick(double dt) override {
		auto [box, _] = boundingBoxes();
		box = expandToFitRotated(box, m_rot).expand(1);

		if (m_lifetime > 0) {
			m_lifetime -= dt;
			if (m_lifetime <= 0) {
				dispose();
				return;
			}
			m_alpha = std::clamp((float)(m_lifetime / m_initialLifetime), 0.0f, 1.0f);
			g_pHyprRenderer->damageBox(box);
		}

		if (box != m_prevBox || box.rot != m_prevBox.rot) {
			g_pHyprRenderer->damageBox(m_prevBox);
			g_pHyprRenderer->damageBox(box);
			m_prevBox = box;
		}

		Body::tick(dt);
	}

	static CBox expandToFitRotated(const CBox& original, float rot) {
		using namespace std;

		const Vector2D center = original.pos() + original.size() / 2;

		Vector2D corners[4] = {
			original.pos(),
			{original.x + original.w, original.y},
			original.pos() + original.size(),
			{original.x, original.y + original.h},
		};

		Vector2D newMin = {DBL_MAX, DBL_MAX};
		Vector2D newMax = {-DBL_MAX, -DBL_MAX};

		for (int i = 0; i < 4; i++) {
			Vector2D worldCorner = rotPoint(corners[i] - center, rot) + center;

			newMin.x = min(newMin.x, worldCorner.x);
			newMin.y = min(newMin.y, worldCorner.y);

			newMax.x = max(newMax.x, worldCorner.x);
			newMax.y = max(newMax.y, worldCorner.y);
		}

		return {newMin, newMax - newMin};
	}

	static Vector2D rotPoint(const Vector2D& vec, float rot) {
		using namespace std;
		return {
			(vec.x * cos(rot)) - (vec.y * sin(rot)),
			(vec.x * sin(rot)) + (vec.y * cos(rot))};
	}

  protected:
	Sprite(Transform transform = {}) : Body(transform) {}
};
