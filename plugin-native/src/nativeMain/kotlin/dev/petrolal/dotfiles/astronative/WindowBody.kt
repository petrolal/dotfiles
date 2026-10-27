package dev.petrolal.dotfiles.astronative

/**
 * Ported from `Window.cpp` -- the one class that makes this the *real*
 * astroland, not just the standalone-overlay approximation: each instance
 * stands in for one actual Hyprland window (identified by [id], an opaque
 * handle the C++ shim assigns from its own `PHLWINDOW`). [AstroLayout]
 * (the shim's tiled-algorithm hook) reads [pos]/[size] back out via
 * `astro_window_x/y/w/h` each tick and moves the *real* window there --
 * this body's physics simulation is what actually drives it around the
 * screen, same as the original.
 */
class WindowBody(val id: Long) : PhysicsBody(Vec2(300f, 300f)), Hittable {
    override var health: Int = 10
    var flashAlpha: Float = 0f

    companion object {
        private const val FLASH_FADE_SPEED = 5f
    }

    init {
        // Spawns just in front of the ship, same formula as the original
        // (`m_size.size()` there is the *length* of the (300,300) vector).
        val ship = AstroGlobals.ship
        val shipSprite = ship.children.getOrNull(0)
        val lookDir = shipSprite?.getLookDir() ?: Vec2(1f, 0f)
        pos = ship.pos + lookDir * (size.length() / 2f)
    }

    override fun tick(dt: Float) {
        if (flashAlpha > 0) {
            flashAlpha -= dt * FLASH_FADE_SPEED
        }

        moveAndCollide(dt)
        super.tick(dt)
    }

    override fun hit(collision: PhysicsBody.Collision, damage: Int) {
        health -= damage
        if (health <= 0) {
            AstroGlobals.soundEvents.addLast(SoundKind.EXPLODE)
            AstroGlobals.scene.instantiate(NativeAnimatedSprite(SpriteKind.EXPLODE, false, Transform(pos, size / 250f)))
            AstroGlobals.camShake(30f)
            AstroGlobals.windowCloseEvents.addLast(id)
            dispose()
            AstroGlobals.windows.remove(id)
        } else {
            flashAlpha = 1f
            val localPos = getWorldTransform().inv().multiply(collision.pos)
            instantiate(NativeSprite(SpriteKind.CHAR, Transform(localPos)))
        }
    }
}
