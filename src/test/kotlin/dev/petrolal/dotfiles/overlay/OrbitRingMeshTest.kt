package dev.petrolal.dotfiles.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrbitRingMeshTest {

    @Test
    void innerAndOuterVerticesStraddleTheNominalRadius() {
        var mesh = OrbitRingMesh.generate(2f, 0.1f, 32);
        int stride = 8;
        for (int i = 0; i < mesh.vertices().length; i += stride * 2) {
            float innerX = mesh.vertices()[i], innerZ = mesh.vertices()[i + 2];
            float outerX = mesh.vertices()[i + stride], outerZ = mesh.vertices()[i + stride + 2];
            float innerRadius = (float) Math.sqrt(innerX * innerX + innerZ * innerZ);
            float outerRadius = (float) Math.sqrt(outerX * outerX + outerZ * outerZ);
            assertEquals(1.9f, innerRadius, 1e-4f);
            assertEquals(2.1f, outerRadius, 1e-4f);
        }
    }

    @Test
    void indexCountMatchesTwoTrianglesPerSegment() {
        var mesh = OrbitRingMesh.generate(2f, 0.1f, 32);
        assertEquals(32 * 6, mesh.indices().length);
    }
}
