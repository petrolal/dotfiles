#pragma once

#include "Sprite.hpp"
#include <string>

// Defined only in AnimatedSprite.cpp -- nothing outside that file touches
// a CachedGif's contents, so it's kept out of this header entirely (the
// gifdec.h vendor dependency it needs stays contained to the .cpp too).
struct CachedGif;

class AnimatedSprite : public Sprite {
  public:
	CachedGif* m_cached = nullptr;
	int m_currentFrame = 0;
	double m_t = 0;
	bool m_repeat;

	AnimatedSprite(std::string gifPath, bool repeat, Transform transform = {});
	~AnimatedSprite() = default;

	virtual void tick(double dt) override;
};
