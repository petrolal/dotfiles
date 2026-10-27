package dev.petrolal.dotfiles.overlay

import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedurally generates a flat ribbon (not a single line) on the XZ plane
 * at a given nominal radius -- the glowing "orbital lane" guide under each
 * [PlanetNode]. Interleaved position(vec3)/normal(vec3, always
 * `(0,1,0)`)/UV(vec2) vertex layout: U runs around the circumference
 * (0..1), V is 0 at the inner edge and 1 at the outer edge, which is what
 * `orbit_ring`'s fragment shader uses for the radial soft-edge fade
 * and circumferential light-pulse pattern.
 */
object OrbitRingMesh {
    /**
     * @param nominalRadius centerline radius of the ribbon
     * @param halfWidth     half the ribbon's radial width (full width is 2x this)
     * @param segments      points around the circumference (>= 3)
     */
    @JvmStatic
    fun generate(nominalRadius: Float, halfWidth: Float, segments: Int): MeshData {
        val vertices = FloatArray(segments * 2 * 8) // inner+outer per segment, pos3+normal3+uv2
        var v = 0

        for (i in 0..<segments) {
            val theta = (2 * Math.PI * i / segments).toFloat()
            val cosTheta = cos(theta)
            val sinTheta = sin(theta)
            val u = i.toFloat() / segments
            val innerRadius = nominalRadius - halfWidth
            val outerRadius = nominalRadius + halfWidth

            // Inner vertex
            vertices[v++] = innerRadius * cosTheta
            vertices[v++] = 0f
            vertices[v++] = innerRadius * sinTheta
            vertices[v++] = 0f
            vertices[v++] = 1f
            vertices[v++] = 0f
            vertices[v++] = u
            vertices[v++] = 0f

            // Outer vertex
            vertices[v++] = outerRadius * cosTheta
            vertices[v++] = 0f
            vertices[v++] = outerRadius * sinTheta
            vertices[v++] = 0f
            vertices[v++] = 1f
            vertices[v++] = 0f
            vertices[v++] = u
            vertices[v++] = 1f
        }

        // Two triangles per segment, wrapping the last segment back to
        // segment 0 so the ribbon closes seamlessly at 2*pi.
        val indices = IntArray(segments * 6)
        var idx = 0
        for (i in 0..<segments) {
            val innerA = i * 2
            val outerA = i * 2 + 1
            val next = (i + 1) % segments
            val innerB = next * 2
            val outerB = next * 2 + 1

            indices[idx++] = innerA
            indices[idx++] = outerA
            indices[idx++] = innerB

            indices[idx++] = outerA
            indices[idx++] = outerB
            indices[idx++] = innerB
        }

        return MeshData(vertices, indices)
    }

    @JvmRecord
    data class MeshData(val vertices: FloatArray, val indices: IntArray)
}
