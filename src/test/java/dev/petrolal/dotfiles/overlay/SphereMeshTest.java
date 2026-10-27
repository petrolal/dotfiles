package dev.petrolal.dotfiles.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SphereMeshTest {

    @Test
    void everyVertexSitsOnTheSphereSurface() {
        var mesh = SphereMesh.generate(2f, 8, 12);
        int stride = 8;
        for (int i = 0; i < mesh.vertices().length; i += stride) {
            float x = mesh.vertices()[i];
            float y = mesh.vertices()[i + 1];
            float z = mesh.vertices()[i + 2];
            float distanceFromCenter = (float) Math.sqrt(x * x + y * y + z * z);
            assertEquals(2f, distanceFromCenter, 1e-4f);
        }
    }

    @Test
    void normalsAreUnitLengthAndPointOutward() {
        var mesh = SphereMesh.generate(2f, 8, 12);
        int stride = 8;
        for (int i = 0; i < mesh.vertices().length; i += stride) {
            float px = mesh.vertices()[i], py = mesh.vertices()[i + 1], pz = mesh.vertices()[i + 2];
            float nx = mesh.vertices()[i + 3], ny = mesh.vertices()[i + 4], nz = mesh.vertices()[i + 5];
            float normalLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            assertEquals(1f, normalLength, 1e-4f);
            // Normal should point the same way as the (radius-scaled) position vector.
            assertEquals(px / 2f, nx, 1e-4f);
            assertEquals(py / 2f, ny, 1e-4f);
            assertEquals(pz / 2f, nz, 1e-4f);
        }
    }

    @Test
    void indicesStayWithinVertexBounds() {
        int latitudeBands = 8, longitudeBands = 12;
        var mesh = SphereMesh.generate(1f, latitudeBands, longitudeBands);
        int vertexCount = mesh.vertices().length / 8;
        for (int index : mesh.indices()) {
            assertTrue(index >= 0 && index < vertexCount, "index out of bounds: " + index);
        }
        assertEquals(latitudeBands * longitudeBands * 6, mesh.indices().length);
    }
}
