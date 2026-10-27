package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f
import kotlin.math.floor
import kotlin.random.Random

/**
 * Standalone-overlay port of astroland_dots' per-tick game loop
 * (`main.cpp`'s `onTick` + render-stage listener), minus everything that
 * depended on Hyprland hooking real desktop windows into the scene (see
 * [AstroGlobals]'s class doc). Owns the scene tree, camera, moon spawner,
 * and screen-space drawing of the tiled background + every visible sprite.
 */
class AstroScene {
    private val spriteRenderer = SpriteRenderer()
    private val background = TextureLoader.loadPng("astroland/bg.png")

    private var viewportWidth = 0
    private var viewportHeight = 0

    init {
        AstroGlobals.reset()
    }

    fun resize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        spriteRenderer.resize(width, height)
    }

    fun setInputVec(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        AstroGlobals.isUpDown = up
        AstroGlobals.isDownDown = down
        AstroGlobals.isLeftDown = left
        AstroGlobals.isRightDown = right
        AstroGlobals.inputVec = Vector2f(
            (if (right) 1f else 0f) - (if (left) 1f else 0f),
            (if (down) 1f else 0f) - (if (up) 1f else 0f),
        )
    }

    fun setShooting(shooting: Boolean) {
        AstroGlobals.ship.isShooting = shooting
    }

    /** [cursorX]/[cursorY] in screen pixels -- converted to world space via the current camera. */
    fun setCursorScreenPos(cursorX: Float, cursorY: Float) {
        AstroGlobals.pointerWorldPos = screenToWorld(cursorX, cursorY)
    }

    fun tick(dt: Float) {
        maybeSpawnMoonWave(dt)

        AstroGlobals.scene.tick(dt)

        val targetPos = Vector2f(AstroGlobals.ship.pos)
        val camSpeed = 5f
        AstroGlobals.actualCamPos = Vector2f(
            lerp(AstroGlobals.camPos.x, targetPos.x, dt * camSpeed),
            lerp(AstroGlobals.camPos.y, targetPos.y, dt * camSpeed),
        )

        if (AstroGlobals.camShakeIntensity > 0) {
            AstroGlobals.camPos = Vector2f(
                AstroGlobals.actualCamPos.x + PerlinNoise.noise1(AstroGlobals.camShakeNoiseIndex.x) * 2f * AstroGlobals.camShakeIntensity,
                AstroGlobals.actualCamPos.y + PerlinNoise.noise1(AstroGlobals.camShakeNoiseIndex.y) * 2f * AstroGlobals.camShakeIntensity,
            )
            AstroGlobals.camShakeNoiseIndex = Vector2f(AstroGlobals.camShakeNoiseIndex).add(AstroGlobals.CAM_SHAKE_NOISE_FREQ * dt, AstroGlobals.CAM_SHAKE_NOISE_FREQ * dt)
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
            while (n > 0) {
                var x = (Random.nextInt(viewportWidth) - viewportWidth / 2).toFloat()
                x *= (Random.nextInt(3) - 1).toFloat()

                var y: Float
                if (x == 0f) {
                    y = (Random.nextInt(viewportHeight) - viewportHeight / 2).toFloat()
                    x = viewportHeight / 2f + 100f
                    x *= if (Random.nextBoolean()) -1f else 1f
                } else {
                    y = viewportHeight / 2f + 100f
                }
                y *= if (Random.nextBoolean()) -1f else 1f

                val spawnPos = Vector2f(AstroGlobals.ship.pos).add(x, y)
                val moon = AstroGlobals.scene.instantiate(Moon(Transform(spawnPos)))
                val dir = Vector2f(AstroGlobals.ship.pos).sub(spawnPos)
                if (dir.length() > 0f) {
                    moon.velocity = dir.div(dir.length()).mul(100f)
                }
                n--
            }
            AstroGlobals.moonCount += spawned
            AstroGlobals.moonSpawnT = 0f
        } else if (AstroGlobals.ship.velocity.length() > 0) {
            AstroGlobals.moonSpawnT += dt
        }
    }

    fun draw() {
        drawTiledBackground()

        AstroGlobals.scene.forEachChildRecursive { node ->
            if (!node.isVisible) return@forEachChildRecursive
            val sprite = node as? Sprite ?: return@forEachChildRecursive

            val worldTransform = sprite.getWorldTransform()
            val screenPos = worldToScreen(worldTransform.getPos())
            val size = sprite.worldSize()
            spriteRenderer.draw(sprite.texture, screenPos.x, screenPos.y, size.x, size.y, sprite.rot)
        }
    }

    private fun drawTiledBackground() {
        val size = Vector2f(background.width.toFloat(), background.height.toFloat())
        val camTopLeft = Vector2f(AstroGlobals.camPos).sub(viewportWidth / 2f, viewportHeight / 2f)
        val tileX0 = floor(camTopLeft.x / size.x) * size.x
        val tileY0 = floor(camTopLeft.y / size.y) * size.y
        val repeatsX = (viewportWidth / size.x).toInt() + 2
        val repeatsY = (viewportHeight / size.y).toInt() + 2

        for (ty in 0 until repeatsY) {
            for (tx in 0 until repeatsX) {
                val worldPos = Vector2f(tileX0 + tx * size.x, tileY0 + ty * size.y)
                val screenPos = worldToScreen(worldPos)
                spriteRenderer.draw(background, screenPos.x + size.x / 2f, screenPos.y + size.y / 2f, size.x, size.y, 0f)
            }
        }
    }

    private fun worldToScreen(worldPos: Vector2f): Vector2f =
        Vector2f(worldPos).sub(AstroGlobals.camPos).add(viewportWidth / 2f, viewportHeight / 2f)

    private fun screenToWorld(screenX: Float, screenY: Float): Vector2f =
        Vector2f(screenX - viewportWidth / 2f, screenY - viewportHeight / 2f).add(AstroGlobals.camPos)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun destroy() {
        spriteRenderer.destroy()
    }
}
