package dev.petrolal.dotfiles.overlay;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanetNodeTest {

    private static PlanetNode node(float radius, float phase) {
        return new PlanetNode("Test", "true", radius, 0.5f, phase,
                new Vector3f(1f, 1f, 1f), new Vector3f(1f, 1f, 1f), 0.1f,
                PolyhedronMesh.Shape.ICOSAHEDRON);
    }

    @Test
    void positionSitsOnTheOrbitRadiusAtTheInitialPhase() {
        PlanetNode planet = node(2f, 0f);
        Vector3f position = planet.position();
        assertEquals(2f, position.x, 1e-4f);
        assertEquals(0f, position.z, 1e-4f);
    }

    @Test
    void positionIsStableAcrossRepeatedCalls() {
        // The whole point of freezing positions: calling position() twice
        // must return the exact same point, never drifting with time.
        PlanetNode planet = node(1.5f, 1.2f);
        assertEquals(planet.position(), planet.position());
    }
}
