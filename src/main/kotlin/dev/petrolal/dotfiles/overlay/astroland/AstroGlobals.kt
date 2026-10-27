package dev.petrolal.dotfiles.overlay.astroland

import org.joml.Vector2f

/**
 * Everything astroland_dots' `globals.hpp` kept as free globals (scene root,
 * ship ref, camera, input state, spawn tuning). The original is a Hyprland
 * plugin hooking the compositor's own render/input pipeline directly; this
 * port runs as a standalone GLFW overlay window instead (see
 * [dev.petrolal.dotfiles.overlay.OverlayWindow]), so there is no window
 * manager here to hand real desktop windows into the scene as physics
 * bodies -- that half of the original (`Window.cpp`, `AstroLayout.cpp`,
 * the `drawSurface` render hook) has no standalone-process equivalent and
 * is intentionally left out. Everything else -- ship flight, moon spawning,
 * bullets, collision, camera shake -- is a straight port.
 */
object AstroGlobals {
    const val DEG2RAD = Math.PI.toFloat() / 180f
    const val ANGLE_90 = 90f * DEG2RAD

    const val MAX_MOONS_PER_WAVE = 3
    const val MAX_MOONS = 10
    const val S_BETWEEN_WAVES = 4f

    const val CAM_SHAKE_FALLOFF_SPEED = 30f
    const val CAM_SHAKE_NOISE_FREQ = 7f

    lateinit var scene: Body
    lateinit var ship: Ship

    var camPos = Vector2f(0f, 0f)
    var actualCamPos = Vector2f(0f, 0f)
    var camShakeNoiseIndex = Vector2f(0f, 0f)
    var camShakeIntensity = 0f

    var moonSpawnT = 0f
    var moonCount = 0
    var spawnMoonsEnabled = true

    var isUpDown = false
    var isDownDown = false
    var isLeftDown = false
    var isRightDown = false
    var inputVec = Vector2f(0f, 0f)

    /** World-space position the ship aims toward -- fed by the overlay's cursor each frame. */
    var pointerWorldPos = Vector2f(0f, 0f)

    fun camShake(intensity: Float) {
        camShakeIntensity += intensity
    }

    fun reset() {
        scene = Body().also { it.name = "scene" }
        ship = scene.instantiate(Ship()).also { it.name = "ship" }
        camPos = Vector2f(0f, 0f)
        actualCamPos = Vector2f(0f, 0f)
        camShakeNoiseIndex = Vector2f(0f, 0f)
        camShakeIntensity = 0f
        moonSpawnT = 0f
        moonCount = 0
        isUpDown = false; isDownDown = false; isLeftDown = false; isRightDown = false
        inputVec = Vector2f(0f, 0f)
    }
}
