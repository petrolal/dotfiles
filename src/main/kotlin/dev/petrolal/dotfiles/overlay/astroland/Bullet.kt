package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f
import kotlin.math.atan2

/** Ported from `Bullet.cpp`: a short-lived projectile that damages the first [Hittable] it hits. */
class Bullet(dir: Vector2f, pos: Vector2f) : PhysicsBody(Vector2f(10f, 10f), Transform(pos)) {
    private val damage = 1
    private val maxSpeed = 900f
    private val ttlSeconds = 3f

    private var timeAlive = 0f

    init {
        isSolid = false
        velocity = Vector2f(dir).mul(maxSpeed)
        decel = 0f
    }

    override fun onInitialized() {
        instantiate(Sprite("astroland/bullet.png", Transform()))
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
