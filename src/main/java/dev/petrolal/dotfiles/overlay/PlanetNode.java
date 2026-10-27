package dev.petrolal.dotfiles.overlay;

import org.joml.Vector3f;

/**
 * One clickable celestial body -- Astroland's replacement for an
 * app-launcher icon. Position is FIXED (computed once from
 * {@link #orbitalRadius}/{@link #initialPhaseRadians} at construction, see
 * {@link #position()}) -- this was built, then deliberately changed: this
 * is meant to be a real daily-use launcher, and a continuously-orbiting
 * hitbox you have to re-aim a manually-orbited camera at is bad UX for
 * something you want to hit fast and from muscle memory. See
 * docs/biopunk_plan.md's lineage notes for the full history.
 *
 * <p>{@link #angularVelocityRadiansPerSecond} is kept, repurposed as a
 * purely cosmetic self-rotation rate (the sphere spins in place) --
 * decorative motion that never moves the click target, unlike the orbital
 * drift this originally specified.
 */
record PlanetNode(
        String label,
        String exec,
        float orbitalRadius,
        float angularVelocityRadiansPerSecond,
        float initialPhaseRadians,
        Vector3f baseColor,
        Vector3f emissiveGlow,
        float sphereRadius,
        PolyhedronMesh.Shape shape) {

    /** Fixed world position -- a circular layout around the star at
     * {@link #initialPhaseRadians}, with a small constant Y offset per
     * planet (derived from the phase, not from elapsed time) so the ring
     * doesn't read as perfectly flat. */
    Vector3f position() {
        float x = orbitalRadius * (float) Math.cos(initialPhaseRadians);
        float z = orbitalRadius * (float) Math.sin(initialPhaseRadians);
        float y = 0.08f * (float) Math.sin(2.0 * initialPhaseRadians);
        return new Vector3f(x, y, z);
    }
}
