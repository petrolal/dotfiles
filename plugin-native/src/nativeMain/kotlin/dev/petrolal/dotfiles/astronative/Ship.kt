package dev.petrolal.dotfiles.astronative

import kotlin.math.PI
import kotlin.math.atan2

/** Ported from `Ship.cpp`. See the JVM port's `Ship.kt` for the line-by-line origin. */
class Ship : PhysicsBody(Vec2(100f, 100f)) {
    private val maxSpeed = 500f
    private val accel = 2000f
    private val timeBetweenShots = 0.1f
    private val rotSpeed = 10f

    private var shootTimer = 0f
    var isShooting = false

    init {
        decel = 1000f
    }

    override fun onInitialized() {
        instantiate(NativeSprite(SpriteKind.SHIP, Transform(Vec2.ZERO, Vec2(0.5f, 0.5f))))
    }

    override fun tick(dt: Float) {
        velocity += AstroGlobals.inputVec * accel * dt
        var speed = velocity.length()
        if (speed > maxSpeed) {
            velocity = velocity.normalized() * maxSpeed
            speed = maxSpeed
        }

        val worldPos = getWorldPos()
        val toPointer = AstroGlobals.pointerWorldPos - worldPos
        val dirToPointer = if (toPointer.length() > 0f) toPointer.normalized() else Vec2(1f, 0f)

        val sprite = children.getOrNull(0)
        if (sprite != null) {
            val desiredRot = atan2(dirToPointer.y, dirToPointer.x) + AstroGlobals.ANGLE_90
            sprite.rot = slerp(sprite.rot, desiredRot, dt * rotSpeed)
        }

        moveAndCollide(dt)
        super.tick(dt)

        if (isShooting && shootTimer >= timeBetweenShots) {
            AstroGlobals.soundEvents.addLast(SoundKind.SHOOT)
            val bulletPos = worldPos + dirToPointer * (size.x / 2f)
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
