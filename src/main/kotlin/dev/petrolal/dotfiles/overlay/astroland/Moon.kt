package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f

/** Ported from `Moon.cpp`. Drifts toward the ship, dies in 5 hits, explodes and shakes the camera. */
class Moon(transform: Transform = Transform.identity()) : PhysicsBody(Vector2f(100f, 100f), transform), Hittable {
    override var health: Int = 5
    private val maxSpeed = 300f

    init {
        decel = 0f
    }

    override fun dispose() {
        super.dispose()
        AstroGlobals.moonCount -= 1
    }

    override fun onInitialized() {
        instantiate(Sprite("astroland/moon_${(0..2).random()}.png", Transform(Vector2f(0f, 0f), Vector2f(0.5f, 0.5f))))
    }

    override fun tick(dt: Float) {
        // The original also died on collision with a desktop `Window` here
        // (ramming a window popped the moon) -- there's no Window body in
        // this standalone port (see AstroGlobals' class doc), so moons now
        // only take damage from [Bullet] hits, dispatched via [Hittable].
        moveAndCollide(dt)

        super.tick(dt)

        if (velocity.length() > maxSpeed) {
            velocity = Vector2f(velocity).div(velocity.length()).mul(maxSpeed)
        }

        if (pos.distance(AstroGlobals.camPos) > 1000f) {
            dispose()
        }
    }

    override fun hit(collision: PhysicsBody.Collision, damage: Int) {
        health -= damage
        if (health <= 0) {
            SoundPlayer.play("astroland/explode_0.wav")
            AstroGlobals.scene.instantiate(AnimatedSprite("astroland/explode.gif", false, Transform(pos, Vector2f(0.5f, 0.5f))))
            AstroGlobals.camShake(20f)
            dispose()
        }
    }
}
