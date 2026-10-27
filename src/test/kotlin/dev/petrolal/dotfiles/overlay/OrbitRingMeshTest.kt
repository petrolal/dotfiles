package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.OrbitRingMesh.generate
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

internal class OrbitRingMeshTest {
    @Test
    fun innerAndOuterVerticesStraddleTheNominalRadius() {
        val mesh = generate(2f, 0.1f, 32)
        val stride = 8
        var i = 0
        while (i < mesh.vertices.size) {
            val innerX = mesh.vertices[i]
            val innerZ = mesh.vertices[i + 2]
            val outerX = mesh.vertices[i + stride]
            val outerZ = mesh.vertices[i + stride + 2]
            val innerRadius = sqrt((innerX * innerX + innerZ * innerZ).toDouble()).toFloat()
            val outerRadius = sqrt((outerX * outerX + outerZ * outerZ).toDouble()).toFloat()
            Assertions.assertEquals(1.9f, innerRadius, 1e-4f)
            Assertions.assertEquals(2.1f, outerRadius, 1e-4f)
            i += stride * 2
        }
    }

    @Test
    fun indexCountMatchesTwoTrianglesPerSegment() {
        val mesh = generate(2f, 0.1f, 32)
        Assertions.assertEquals(32 * 6, mesh.indices.size)
    }
}
