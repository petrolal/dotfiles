package dev.petrolal.dotfiles.overlay.astroland

/**
 * Ported from `AnimatedSprite.cpp`. The original decodes GIF frames lazily,
 * one per tick, through `gifdec`; this port decodes every frame up front
 * via [TextureLoader.loadGifFrames] (javax.imageio) since these GIFs are
 * small enough that the one-time decode cost is a non-issue, and it avoids
 * re-implementing GIF LZW decoding + frame compositing by hand.
 */
class AnimatedSprite(resourcePath: String, private val repeat: Boolean, transform: Transform = Transform.identity()) :
    Sprite(null, transform) {

    private val frames = TextureLoader.loadGifFrames(resourcePath)
    private var frameIndex = 0
    private var t = 0f

    init {
        require(frames.isNotEmpty()) { "GIF has no frames: $resourcePath" }
        texture = frames[0].texture
    }

    override fun tick(dt: Float) {
        t += dt
        if (t >= frames[frameIndex].delaySeconds) {
            t = 0f
            frameIndex++
            if (frameIndex >= frames.size) {
                if (repeat) {
                    frameIndex = 0
                } else {
                    dispose()
                    return
                }
            }
            texture = frames[frameIndex].texture
        }

        super.tick(dt)
    }
}
