package dev.petrolal.dotfiles.astronative

/** Ported from `Sprite.cpp`, minus pixels -- see [SpriteKind]'s class doc. */
open class NativeSprite(val kind: SpriteKind, transform: Transform = Transform.identity()) : Body(transform) {
    var frameIndex: Int = 0
}

/**
 * Ported from `AnimatedSprite.cpp`. [frameCount]/[frameDelaySeconds] come from
 * [AstroGlobals.gifMeta], filled in by the C++ shim at startup from the real
 * GIF's frame count/delay (decoded once there via `gifdec`, same as the
 * original) -- this port approximates per-clip timing as one average delay
 * rather than the original's per-frame `gce.delay`, since re-deriving that
 * here would mean re-implementing GIF parsing on the Kotlin/Native side too.
 */
class NativeAnimatedSprite(kind: SpriteKind, private val repeat: Boolean, transform: Transform = Transform.identity()) :
    NativeSprite(kind, transform) {

    private var t = 0f

    override fun tick(dt: Float) {
        val meta = AstroGlobals.gifMeta[kind] ?: GifMeta(1, 0.1f)
        t += dt
        if (t >= meta.frameDelaySeconds) {
            t = 0f
            frameIndex++
            if (frameIndex >= meta.frameCount) {
                if (repeat) {
                    frameIndex = 0
                } else {
                    dispose()
                    return
                }
            }
        }
        super.tick(dt)
    }
}

data class GifMeta(val frameCount: Int, val frameDelaySeconds: Float)
