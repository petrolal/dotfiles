package dev.petrolal.dotfiles.overlay

import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * One clickable celestial body -- Astroland's replacement for an
 * app-launcher icon. Position is FIXED (computed once from
 * [orbitalRadius]/[initialPhaseRadians] at construction, see
 * [position]) -- this was built, then deliberately changed: this
 * is meant to be a real daily-use launcher, and a continuously-orbiting
 * hitbox you have to re-aim a manually-orbited camera at is bad UX for
 * something you want to hit fast and from muscle memory.
 * 
 * [angularVelocityRadiansPerSecond] is kept, repurposed as a
 * purely cosmetic self-rotation rate (the sphere spins in place) --
 * decorative motion that never moves the click target, unlike the orbital
 * drift this originally specified.
 */
@JvmRecord
data class PlanetNode(
    val label: String,
    val exec: String,
    val orbitalRadius: Float,
    val angularVelocityRadiansPerSecond: Float,
    val initialPhaseRadians: Float,
    val baseColor: Vector3f,
    val emissiveGlow: Vector3f,
    val sphereRadius: Float,
    val shape: PolyhedronMesh.Shape
) {
    /** Fixed world position -- a circular layout around the star at
     * [initialPhaseRadians], with a small constant Y offset per
     * planet (derived from the phase, not from elapsed time) so the ring
     * doesn't read as perfectly flat.  */
    fun position(): Vector3f {
        val x = orbitalRadius * cos(initialPhaseRadians)
        val z = orbitalRadius * sin(initialPhaseRadians)
        val y = 0.08f * sin(2.0f * initialPhaseRadians)
        return Vector3f(x, y, z)
    }
}
