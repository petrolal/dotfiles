package dev.petrolal.dotfiles.astronative

import kotlin.random.Random

/**
 * One sprite to draw this frame, in world space -- the shim converts to
 * monitor/screen space itself (`g_worldToMonitor`) and multiplies [scale] by
 * that [kind]'s actual loaded-texture pixel size (this engine never touches
 * pixels, see [SpriteKind]'s class doc).
 */
data class DrawCommand(val kind: SpriteKind, val frame: Int, val pos: Vec2, val scale: Vec2, val rot: Float)

/**
 * The Kotlin/Native side of astroland_dots' `main.cpp` -- `onTick`'s moon
 * spawner + camera-shake math, plus walking the scene tree for render-stage
 * listener's sprite enumeration. [AstroLayout]-equivalent window placement
 * is just reading [WindowBody.pos]/[size] back out through [NativeApi].
 */
object AstroEngine {
    private var started = false

    fun ensureStarted() {
        if (!started) {
            AstroGlobals.init()
            started = true
        }
    }

    fun tick(dt: Float) {
        maybeSpawnMoonWave(dt)

        AstroGlobals.scene.tick(dt)

        val targetPos = AstroGlobals.ship.pos
        val camSpeed = 5f
        AstroGlobals.actualCamPos = Vec2(
            lerp(AstroGlobals.camPos.x, targetPos.x, dt * camSpeed),
            lerp(AstroGlobals.camPos.y, targetPos.y, dt * camSpeed),
        )

        if (AstroGlobals.camShakeIntensity > 0) {
            AstroGlobals.camPos = Vec2(
                AstroGlobals.actualCamPos.x + PerlinNoise.noise1(AstroGlobals.camShakeNoiseIndex.x) * 2f * AstroGlobals.camShakeIntensity,
                AstroGlobals.actualCamPos.y + PerlinNoise.noise1(AstroGlobals.camShakeNoiseIndex.y) * 2f * AstroGlobals.camShakeIntensity,
            )
            AstroGlobals.camShakeNoiseIndex += Vec2(AstroGlobals.CAM_SHAKE_NOISE_FREQ * dt, AstroGlobals.CAM_SHAKE_NOISE_FREQ * dt)
            AstroGlobals.camShakeIntensity -= dt * AstroGlobals.CAM_SHAKE_FALLOFF_SPEED
        } else {
            AstroGlobals.camPos = AstroGlobals.actualCamPos
            AstroGlobals.camShakeIntensity = 0f
        }
    }

    private fun maybeSpawnMoonWave(dt: Float) {
        if (!AstroGlobals.spawnMoonsEnabled) return

        if (AstroGlobals.moonCount < AstroGlobals.MAX_MOONS && AstroGlobals.moonSpawnT >= AstroGlobals.S_BETWEEN_WAVES) {
            var n = Random.nextInt(AstroGlobals.MAX_MOONS_PER_WAVE) + 1
            val spawned = n
            val vw = AstroGlobals.viewportWidth
            val vh = AstroGlobals.viewportHeight
            while (n > 0) {
                var x = (Random.nextInt(vw.toInt().coerceAtLeast(1)) - vw / 2)
                x *= (Random.nextInt(3) - 1).toFloat()

                var y: Float
                if (x == 0f) {
                    y = (Random.nextInt(vh.toInt().coerceAtLeast(1)) - vh / 2)
                    x = vh / 2f + 100f
                    x *= if (Random.nextBoolean()) -1f else 1f
                } else {
                    y = vh / 2f + 100f
                }
                y *= if (Random.nextBoolean()) -1f else 1f

                val spawnPos = AstroGlobals.ship.pos + Vec2(x, y)
                val moon = AstroGlobals.scene.instantiate(Moon(Transform(spawnPos)))
                val dir = AstroGlobals.ship.pos - spawnPos
                if (dir.length() > 0f) {
                    moon.velocity = dir.normalized() * 100f
                }
                n--
            }
            AstroGlobals.moonCount += spawned
            AstroGlobals.moonSpawnT = 0f
        } else if (AstroGlobals.ship.velocity.length() > 0) {
            AstroGlobals.moonSpawnT += dt
        }
    }

    fun snapshotDrawCommands(): List<DrawCommand> {
        val commands = mutableListOf<DrawCommand>()
        AstroGlobals.scene.forEachChildRecursive { node ->
            if (!node.isVisible) return@forEachChildRecursive
            val sprite = node as? NativeSprite ?: return@forEachChildRecursive
            val worldTransform = sprite.getWorldTransform()
            commands.add(DrawCommand(sprite.kind, sprite.frameIndex, worldTransform.getPos(), worldTransform.getScale(), sprite.rot))
        }
        return commands
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
