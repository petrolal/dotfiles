package dev.petrolal.dotfiles.astronative

import kotlin.math.atan2

/** Ported from `Bullet.cpp`. */
class Bullet(dir: Vec2, pos: Vec2) : PhysicsBody(Vec2(10f, 10f), Transform(pos)) {
    private val damage = 1
    private val maxSpeed = 900f
    private val ttlSeconds = 3f
    private var timeAlive = 0f

    init {
        isSolid = false
        velocity = dir * maxSpeed
        decel = 0f
    }

    override fun onInitialized() {
        instantiate(NativeSprite(SpriteKind.BULLET))
    }

    override fun tick(dt: Float) {
        timeAlive += dt
        if (timeAlive >= ttlSeconds) {
            dispose()
            return
        }

        val collisions = moveAndCollide(dt)
        for (collision in collisions) {
            val hittable = collision.body as? Hittable
            if (hittable != null) {
                hittable.hit(collision, damage)
                dispose()
                return
            }
        }

        children.getOrNull(0)?.let { sprite ->
            sprite.rot = atan2(velocity.y, velocity.x) + AstroGlobals.ANGLE_90
        }

        super.tick(dt)
    }
}
