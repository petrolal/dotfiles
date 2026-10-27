package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f
import kotlin.math.PI
import kotlin.math.atan2

/** Ported from `Ship.cpp`. WASD accelerates, mouse aims/rotates the sprite, left click fires. */
class Ship : PhysicsBody(Vector2f(100f, 100f)) {
    private val maxSpeed = 500f
    private val accel = 2000f
    private val timeBetweenShots = 0.1f
    private val rotSpeed = 10f // rad/s

    private var shootTimer = 0f
    var isShooting = false

    init {
        decel = 1000f
    }

    override fun onInitialized() {
        instantiate(Sprite("astroland/ship.png", Transform(Vector2f(0f, 0f), Vector2f(0.5f, 0.5f))))
    }

    override fun tick(dt: Float) {
        velocity.x += AstroGlobals.inputVec.x * accel * dt
        velocity.y += AstroGlobals.inputVec.y * accel * dt
        var speed = velocity.length()
        if (speed > maxSpeed) {
            velocity = Vector2f(velocity).div(speed).mul(maxSpeed)
            speed = maxSpeed
        }

        val worldPos = getWorldPos()
        var dirToPointer = Vector2f(AstroGlobals.pointerWorldPos).sub(worldPos)
        if (dirToPointer.length() > 0f) dirToPointer = dirToPointer.div(dirToPointer.length())

        val sprite = children.getOrNull(0)
        if (sprite != null) {
            val desiredRot = atan2(dirToPointer.y, dirToPointer.x) + AstroGlobals.ANGLE_90
            sprite.rot = slerp(sprite.rot, desiredRot, dt * rotSpeed)
        }

        moveAndCollide(dt)
        super.tick(dt)

        if (isShooting && shootTimer >= timeBetweenShots) {
            SoundPlayer.play("astroland/shoot_${(0..2).random()}.wav")
            val bulletPos = Vector2f(worldPos).add(Vector2f(dirToPointer).mul(size.x / 2f))
            AstroGlobals.scene.instantiate(Bullet(dirToPointer, bulletPos))
            shootTimer = 0f
        } else {
            shootTimer += dt
        }
    }

    companion object {
        fun slerp(angle1: Float, angle2: Float, alpha: Float): Float {
            var diff = angle2 - angle1
            diff = ((diff + 3f * PI.toFloat()) % (2f * PI.toFloat())) - PI.toFloat()
            return angle1 + alpha * diff
        }
    }
}
