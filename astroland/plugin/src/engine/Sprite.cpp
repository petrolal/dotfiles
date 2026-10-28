#include "Sprite.hpp"
#include "globals.hpp"
#include <algorithm>
#include <cfloat>

Sprite::Sprite(const std::string& texPath, Transform transform) : Body(transform) {
	m_texPath = texPath;
	m_tex = loadTex(texPath);
}
Sprite::~Sprite() {
	auto [box, _] = boundingBoxes();
	box = expandToFitRotated(box, m_rot).expand(1);
	g_pHyprRenderer->damageBox(box);
}

std::pair<CBox, CBox> Sprite::boundingBoxes() {
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

void Sprite::tick(double dt) {
	if (!isVisible()) {
		Body::tick(dt);
		return;
	}

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

CBox Sprite::expandToFitRotated(const CBox& original, float rot) {
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

Vector2D Sprite::rotPoint(const Vector2D& vec, float rot) {
	using namespace std;
	return {
		(vec.x * cos(rot)) - (vec.y * sin(rot)),
		(vec.x * sin(rot)) + (vec.y * cos(rot))};
}
