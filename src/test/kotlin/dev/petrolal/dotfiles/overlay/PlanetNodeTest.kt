package dev.petrolal.dotfiles.overlay

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PlanetNodeTest {
    @Test
    fun positionSitsOnTheOrbitRadiusAtTheInitialPhase() {
        val planet: PlanetNode = node(2f, 0f)
        val position = planet.position()
        Assertions.assertEquals(2f, position.x, 1e-4f)
        Assertions.assertEquals(0f, position.z, 1e-4f)
    }

    @Test
    fun positionIsStableAcrossRepeatedCalls() {
        // The whole point of freezing positions: calling position() twice
        // must return the exact same point, never drifting with time.
        val planet: PlanetNode = node(1.5f, 1.2f)
        Assertions.assertEquals(planet.position(), planet.position())
    }

    companion object {
        private fun node(radius: Float, phase: Float): PlanetNode {
            return PlanetNode(
                "Test", "true", radius, 0.5f, phase,
                Vector3f(1f, 1f, 1f), Vector3f(1f, 1f, 1f), 0.1f,
                PolyhedronMesh.Shape.ICOSAHEDRON
            )
        }
    }
}
