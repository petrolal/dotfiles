package dev.petrolal.dotfiles.overlay

import org.joml.Vector3f

/**
 * Procedurally generates the three Platonic solids Astroland's node
 * geometry is built from. Hand-authored, not an imported asset -- same
 * reasoning as [SphereMesh]'s javadoc (no `org.lwjgl.assimp`
 * dependency, no art pipeline).
 * 
 * Each shape yields two independent buffers: [generateFacets] (flat
 * -shaded, per-face-duplicated vertices for the translucent obsidian body)
 * and [generateWireframe] (the solid's real unique vertices, as
 * `GL_LINES` index pairs over its edges, for the glowing edge overlay).
 * All vertices are normalized to unit length so every shape scales the same
 * way under `PlanetNode.sphereRadius()`, matching the old unit-sphere
 * convention it replaces.
 */
object PolyhedronMesh {
    @JvmStatic
    fun generateFacets(shape: Shape): FacetData {
        val solid = raw(shape)
        val vertices = FloatArray(solid.faces.size * 3 * 6) // pos3 + normal3, 3 verts/face
        val indices = IntArray(solid.faces.size * 3)
        var v = 0
        var idx = 0

        for (face in solid.faces) {
            val p0 = solid.vertices[face[0]]
            var p1 = solid.vertices[face[1]]
            var p2 = solid.vertices[face[2]]

            val normal = Vector3f(p1).sub(p0).cross(Vector3f(p2).sub(p0))
            val centroid = Vector3f(p0).add(p1).add(p2).mul(1f / 3f)
            if (normal.dot(centroid) < 0f) {
                // Winding is inward (would be back-face culled from outside) --
                // swap the last two vertices to flip it, which also flips the
                // normal we're about to store to match.
                val tmp = p1
                p1 = p2
                p2 = tmp
                normal.negate()
            }
            normal.normalize()

            for (p in arrayOf(p0, p1, p2)) {
                vertices[v++] = p.x
                vertices[v++] = p.y
                vertices[v++] = p.z
                vertices[v++] = normal.x
                vertices[v++] = normal.y
                vertices[v++] = normal.z
                indices[idx] = idx
                idx++
            }
        }

        return FacetData(vertices, indices)
    }

    @JvmStatic
    fun generateWireframe(shape: Shape): WireframeData {
        val solid = raw(shape)
        val vertices = FloatArray(solid.vertices.size * 3)
        var v = 0
        for (p in solid.vertices) {
            vertices[v++] = p.x
            vertices[v++] = p.y
            vertices[v++] = p.z
        }

        val seenEdges = HashSet<Long>()
        val edgeIndices = ArrayList<Int>()
        for (face in solid.faces) {
            for (e in 0..2) {
                val a = face[e]
                val b = face[(e + 1) % 3]
                val key = (minOf(a, b).toLong() shl 32) or (maxOf(a, b).toLong() and 0xFFFFFFFFL)
                if (seenEdges.add(key)) {
                    edgeIndices.add(a)
                    edgeIndices.add(b)
                }
            }
        }

        return WireframeData(vertices, edgeIndices.toIntArray())
    }

    private fun raw(shape: Shape): RawSolid = when (shape) {
        Shape.TETRAHEDRON -> tetrahedron()
        Shape.OCTAHEDRON -> octahedron()
        Shape.ICOSAHEDRON -> icosahedron()
    }

    private fun tetrahedron(): RawSolid {
        val vertices = normalized(
            Vector3f(1f, 1f, 1f),
            Vector3f(1f, -1f, -1f),
            Vector3f(-1f, 1f, -1f),
            Vector3f(-1f, -1f, 1f)
        )
        val faces = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(0, 3, 1),
            intArrayOf(0, 2, 3),
            intArrayOf(1, 3, 2),
        )
        return RawSolid(vertices, faces)
    }

    private fun octahedron(): RawSolid {
        val vertices = normalized(
            Vector3f(1f, 0f, 0f), Vector3f(-1f, 0f, 0f),
            Vector3f(0f, 1f, 0f), Vector3f(0f, -1f, 0f),
            Vector3f(0f, 0f, 1f), Vector3f(0f, 0f, -1f)
        )
        val px = 0
        val nx = 1
        val py = 2
        val ny = 3
        val pz = 4
        val nz = 5
        val faces = arrayOf(
            intArrayOf(px, py, pz), intArrayOf(py, nx, pz), intArrayOf(nx, ny, pz), intArrayOf(ny, px, pz),
            intArrayOf(py, px, nz), intArrayOf(nx, py, nz), intArrayOf(ny, nx, nz), intArrayOf(px, ny, nz),
        )
        return RawSolid(vertices, faces)
    }

    private fun icosahedron(): RawSolid {
        val phi = ((1.0 + kotlin.math.sqrt(5.0)) / 2.0).toFloat()
        val vertices = normalized(
            Vector3f(-1f, phi, 0f), Vector3f(1f, phi, 0f),
            Vector3f(-1f, -phi, 0f), Vector3f(1f, -phi, 0f),
            Vector3f(0f, -1f, phi), Vector3f(0f, 1f, phi),
            Vector3f(0f, -1f, -phi), Vector3f(0f, 1f, -phi),
            Vector3f(phi, 0f, -1f), Vector3f(phi, 0f, 1f),
            Vector3f(-phi, 0f, -1f), Vector3f(-phi, 0f, 1f)
        )
        val faces = arrayOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
        )
        return RawSolid(vertices, faces)
    }

    private fun normalized(vararg points: Vector3f): Array<Vector3f> {
        for (p in points) {
            p.normalize()
        }
        @Suppress("UNCHECKED_CAST")
        return points as Array<Vector3f>
    }

    enum class Shape {
        TETRAHEDRON, OCTAHEDRON, ICOSAHEDRON
    }

    @JvmRecord
    data class FacetData(val vertices: FloatArray, val indices: IntArray)

    @JvmRecord
    data class WireframeData(val vertices: FloatArray, val indices: IntArray)

    private data class RawSolid(val vertices: Array<Vector3f>, val faces: Array<IntArray>)
}
