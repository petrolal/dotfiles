package dev.petrolal.dotfiles.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolyhedronMeshTest {

    @Test
    void tetrahedronHasFourFacesAndSixEdges() {
        assertFacetAndWireframeCounts(PolyhedronMesh.Shape.TETRAHEDRON, 4, 4, 6);
    }

    @Test
    void octahedronHasEightFacesAndTwelveEdges() {
        assertFacetAndWireframeCounts(PolyhedronMesh.Shape.OCTAHEDRON, 6, 8, 12);
    }

    @Test
    void icosahedronHasTwentyFacesAndThirtyEdges() {
        assertFacetAndWireframeCounts(PolyhedronMesh.Shape.ICOSAHEDRON, 12, 20, 30);
    }

    private static void assertFacetAndWireframeCounts(PolyhedronMesh.Shape shape, int rawVertexCount,
                                                        int faceCount, int edgeCount) {
        var facets = PolyhedronMesh.generateFacets(shape);
        assertEquals(faceCount * 3 * 6, facets.vertices().length);
        assertEquals(faceCount * 3, facets.indices().length);

        var wireframe = PolyhedronMesh.generateWireframe(shape);
        assertEquals(rawVertexCount * 3, wireframe.vertices().length);
        assertEquals(edgeCount * 2, wireframe.indices().length);
    }

    @Test
    void everyFacetVertexIsUnitLength() {
        for (PolyhedronMesh.Shape shape : PolyhedronMesh.Shape.values()) {
            var mesh = PolyhedronMesh.generateFacets(shape);
            int stride = 6;
            for (int i = 0; i < mesh.vertices().length; i += stride) {
                float x = mesh.vertices()[i], y = mesh.vertices()[i + 1], z = mesh.vertices()[i + 2];
                float length = (float) Math.sqrt(x * x + y * y + z * z);
                assertEquals(1f, length, 1e-4f, shape + ": vertex not on unit sphere");
            }
        }
    }

    @Test
    void everyFaceWindsOutward() {
        for (PolyhedronMesh.Shape shape : PolyhedronMesh.Shape.values()) {
            var mesh = PolyhedronMesh.generateFacets(shape);
            int stride = 6;
            for (int i = 0; i < mesh.vertices().length; i += stride * 3) {
                float px = mesh.vertices()[i], py = mesh.vertices()[i + 1], pz = mesh.vertices()[i + 2];
                float nx = mesh.vertices()[i + 3], ny = mesh.vertices()[i + 4], nz = mesh.vertices()[i + 5];
                // Each solid is centered on the origin, so a face's own
                // vertex position points the same general direction as its
                // outward normal -- a reversed (culled-from-outside) face
                // would fail this.
                float dot = px * nx + py * ny + pz * nz;
                assertTrue(dot > 0f, shape + ": face normal points inward");
            }
        }
    }

    @Test
    void wireframeIndicesStayWithinVertexBounds() {
        for (PolyhedronMesh.Shape shape : PolyhedronMesh.Shape.values()) {
            var mesh = PolyhedronMesh.generateWireframe(shape);
            int vertexCount = mesh.vertices().length / 3;
            for (int index : mesh.indices()) {
                assertTrue(index >= 0 && index < vertexCount, shape + ": index out of bounds: " + index);
            }
        }
    }
}
