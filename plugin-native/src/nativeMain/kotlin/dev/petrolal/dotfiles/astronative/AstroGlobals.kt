package dev.petrolal.dotfiles.astronative

/**
 * The Kotlin/Native side of astroland_dots' `globals.hpp`. Unlike the JVM
 * standalone port, [windows] is real: [WindowBody] proxies for actual
 * Hyprland windows the C++ shim tells us about (see `native-plugin/`'s
 * `Window.cpp`), so collisions here genuinely represent desktop windows
 * getting shot/rammed -- this is the half the standalone JVM overlay
 * couldn't do.
 */
object AstroGlobals {
    val ANGLE_90 = (kotlin.math.PI / 2.0).toFloat()

    const val MAX_MOONS_PER_WAVE = 3
    const val MAX_MOONS = 10
    const val S_BETWEEN_WAVES = 4f

    const val CAM_SHAKE_FALLOFF_SPEED = 30f
    const val CAM_SHAKE_NOISE_FREQ = 7f

    lateinit var scene: Body
    lateinit var ship: Ship

    var camPos = Vec2.ZERO
    var actualCamPos = Vec2.ZERO
    var camShakeNoiseIndex = Vec2.ZERO
    var camShakeIntensity = 0f

    var moonSpawnT = 0f
    var moonCount = 0
    var spawnMoonsEnabled = true

    var inputVec = Vec2.ZERO
    var pointerWorldPos = Vec2.ZERO

    var viewportWidth = 1920f
    var viewportHeight = 1080f

    /** Real Hyprland windows, keyed by the id the shim assigns (it owns the real `PHLWINDOW`). */
    val windows = HashMap<Long, WindowBody>()

    /** Per-[SpriteKind] animated-GIF timing, filled in once by the shim at startup -- see [NativeAnimatedSprite]. */
    val gifMeta = HashMap<SpriteKind, GifMeta>()

    val soundEvents = ArrayDeque<SoundKind>()
    val windowCloseEvents = ArrayDeque<Long>()

    fun camShake(intensity: Float) {
        camShakeIntensity += intensity
    }

    fun init() {
        scene = Body().also { it.name = "scene" }
        ship = scene.instantiate(Ship())
        ship.name = "ship"
    }
}

enum class SoundKind { SHOOT, EXPLODE }
