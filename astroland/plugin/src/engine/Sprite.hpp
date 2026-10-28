#pragma once

#include "Body.hpp"
#include <hyprland/src/render/Texture.hpp>
#include <hyprutils/math/Box.hpp>
#include <hyprutils/math/Vector2D.hpp>
#include <string>
#include <utility>

class Sprite : public Body {
  public:
	std::string m_texPath;
	SP<Render::ITexture> m_tex;
	float m_alpha = 1.0f;
	double m_lifetime = -1.0;
	double m_initialLifetime = -1.0;

	CBox m_prevBox;

	Sprite(const std::string& texPath, Transform transform = {});
	~Sprite();

	std::pair<CBox, CBox> boundingBoxes();

	virtual void tick(double dt) override;

	static CBox expandToFitRotated(const CBox& original, float rot);
	static Vector2D rotPoint(const Vector2D& vec, float rot);

  protected:
	Sprite(Transform transform = {}) : Body(transform) {}
};
