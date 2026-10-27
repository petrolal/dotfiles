package dev.petrolal.dotfiles.astronative

import kotlin.random.Random

/** Ported from `Moon.cpp`. See the JVM port's `Moon.kt` for the collide-with-Window note. */
class Moon(transform: Transform = Transform.identity()) : PhysicsBody(Vec2(100f, 100f), transform), Hittable {
    override var health: Int = 5
    private val maxSpeed = 300f
    private val moonKind = when (Random.nextInt(3)) {
        0 -> SpriteKind.MOON_0
        1 -> SpriteKind.MOON_1
        else -> SpriteKind.MOON_2
    }

    init {
        decel = 0f
    }

    override fun dispose() {
        super.dispose()
        AstroGlobals.moonCount -= 1
    }

    override fun onInitialized() {
        instantiate(NativeSprite(moonKind, Transform(Vec2.ZERO, Vec2(0.5f, 0.5f))))
    }

    override fun tick(dt: Float) {
        moveAndCollide(dt)
        super.tick(dt)

        if (velocity.length() > maxSpeed) {
            velocity = velocity.normalized() * maxSpeed
        }

        if (pos.distance(AstroGlobals.camPos) > 1000f) {
            dispose()
        }
    }

    override fun hit(collision: PhysicsBody.Collision, damage: Int) {
        health -= damage
        if (health <= 0) {
            AstroGlobals.soundEvents.addLast(SoundKind.EXPLODE)
            AstroGlobals.scene.instantiate(NativeAnimatedSprite(SpriteKind.EXPLODE, false, Transform(pos, Vec2(0.5f, 0.5f))))
            AstroGlobals.camShake(20f)
            dispose()
        }
    }
}
