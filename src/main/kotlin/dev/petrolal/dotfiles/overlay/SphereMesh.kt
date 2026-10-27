package dev.petrolal.dotfiles.overlay

import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedurally generates a UV sphere's interleaved vertex data (position,
 * normal, UV -- the layout `heart.vert`'s `aPos`/`aNormal`/
 * `aTexCoords` attributes expect) and its triangle index list.
 * 
 * Hand-authored procedural geometry, not an imported asset: this
 * deliberately avoids pulling in `org.lwjgl.assimp` (see
 * docs/biopunk_plan.md Phase 2 -- no existing native-image reachability
 * precedent for it, no art assets to load yet). A sphere is one of the few
 * "meshes" simple enough to not need an asset pipeline at all; this is
 * expected to stay the exception, not the start of a hand-rolled mesh
 * library.
 */
object SphereMesh {
    /**
     * @param radius      sphere radius in local (pre-model-matrix) space
     * @param latitudeBands  bands from pole to pole (>= 2)
     * @param longitudeBands bands around the equator (>= 3)
     */
    @JvmStatic
    fun generate(radius: Float, latitudeBands: Int, longitudeBands: Int): MeshData {
        val vertexCount = (latitudeBands + 1) * (longitudeBands + 1)
        val vertices = FloatArray(vertexCount * 8) // pos3 + normal3 + uv2
        var v = 0

        for (lat in 0..latitudeBands) {
            val theta = (kotlin.math.PI * lat / latitudeBands).toFloat() // 0 (north pole) -> PI (south pole)
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (lon in 0..longitudeBands) {
                val phi = (2 * kotlin.math.PI * lon / longitudeBands).toFloat()
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                val nx = cosPhi * sinTheta
                val ny = cosTheta
                val nz = sinPhi * sinTheta

                vertices[v++] = nx * radius
                vertices[v++] = ny * radius
                vertices[v++] = nz * radius
                vertices[v++] = nx
                vertices[v++] = ny
                vertices[v++] = nz
                vertices[v++] = lon.toFloat() / longitudeBands
                vertices[v++] = lat.toFloat() / latitudeBands
            }
        }

        val quadCount = latitudeBands * longitudeBands
        val indices = IntArray(quadCount * 6)
        var i = 0
        for (lat in 0..<latitudeBands) {
            for (lon in 0..<longitudeBands) {
                val first = lat * (longitudeBands + 1) + lon
                val second = first + longitudeBands + 1

                indices[i++] = first
                indices[i++] = second
                indices[i++] = first + 1

                indices[i++] = second
                indices[i++] = second + 1
                indices[i++] = first + 1
            }
        }

        return MeshData(vertices, indices)
    }

    @JvmRecord
    data class MeshData(val vertices: FloatArray, val indices: IntArray)
}
