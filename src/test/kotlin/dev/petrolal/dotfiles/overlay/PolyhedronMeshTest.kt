package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.PolyhedronMesh.generateFacets
import dev.petrolal.dotfiles.overlay.PolyhedronMesh.generateWireframe
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

internal class PolyhedronMeshTest {
    @Test
    fun tetrahedronHasFourFacesAndSixEdges() {
        assertFacetAndWireframeCounts(PolyhedronMesh.Shape.TETRAHEDRON, 4, 4, 6)
    }

    @Test
    fun octahedronHasEightFacesAndTwelveEdges() {
        assertFacetAndWireframeCounts(PolyhedronMesh.Shape.OCTAHEDRON, 6, 8, 12)
    }

    @Test
    fun icosahedronHasTwentyFacesAndThirtyEdges() {
        assertFacetAndWireframeCounts(PolyhedronMesh.Shape.ICOSAHEDRON, 12, 20, 30)
    }

    @Test
    fun everyFacetVertexIsUnitLength() {
        for (shape in PolyhedronMesh.Shape.values()) {
            val mesh = generateFacets(shape)
            val stride = 6
            var i = 0
            while (i < mesh.vertices.size) {
                val x = mesh.vertices[i]
                val y = mesh.vertices[i + 1]
                val z = mesh.vertices[i + 2]
                val length = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                Assertions.assertEquals(1f, length, 1e-4f, shape.toString() + ": vertex not on unit sphere")
                i += stride
            }
        }
    }

    @Test
    fun everyFaceWindsOutward() {
        for (shape in PolyhedronMesh.Shape.values()) {
            val mesh = generateFacets(shape)
            val stride = 6
            var i = 0
            while (i < mesh.vertices.size) {
                val px = mesh.vertices[i]
                val py = mesh.vertices[i + 1]
                val pz = mesh.vertices[i + 2]
                val nx = mesh.vertices[i + 3]
                val ny = mesh.vertices[i + 4]
                val nz = mesh.vertices[i + 5]
                // Each solid is centered on the origin, so a face's own
                // vertex position points the same general direction as its
                // outward normal -- a reversed (culled-from-outside) face
                // would fail this.
                val dot = px * nx + py * ny + pz * nz
                Assertions.assertTrue(dot > 0f, shape.toString() + ": face normal points inward")
                i += stride * 3
            }
        }
    }

    @Test
    fun wireframeIndicesStayWithinVertexBounds() {
        for (shape in PolyhedronMesh.Shape.values()) {
            val mesh = generateWireframe(shape)
            val vertexCount = mesh.vertices.size / 3
            for (index in mesh.indices) {
                Assertions.assertTrue(
                    index >= 0 && index < vertexCount,
                    shape.toString() + ": index out of bounds: " + index
                )
            }
        }
    }

    companion object {
        private fun assertFacetAndWireframeCounts(
            shape: PolyhedronMesh.Shape, rawVertexCount: Int,
            faceCount: Int, edgeCount: Int
        ) {
            val facets = generateFacets(shape)
            Assertions.assertEquals(faceCount * 3 * 6, facets.vertices.size)
            Assertions.assertEquals(faceCount * 3, facets.indices.size)

            val wireframe = generateWireframe(shape)
            Assertions.assertEquals(rawVertexCount * 3, wireframe.vertices.size)
            Assertions.assertEquals(edgeCount * 2, wireframe.indices.size)
        }
    }
}
