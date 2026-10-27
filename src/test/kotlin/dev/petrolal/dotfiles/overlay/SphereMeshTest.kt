package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.SphereMesh.generate
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

internal class SphereMeshTest {
    @Test
    fun everyVertexSitsOnTheSphereSurface() {
        val mesh = generate(2f, 8, 12)
        val stride = 8
        var i = 0
        while (i < mesh.vertices.size) {
            val x = mesh.vertices[i]
            val y = mesh.vertices[i + 1]
            val z = mesh.vertices[i + 2]
            val distanceFromCenter = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            Assertions.assertEquals(2f, distanceFromCenter, 1e-4f)
            i += stride
        }
    }

    @Test
    fun normalsAreUnitLengthAndPointOutward() {
        val mesh = generate(2f, 8, 12)
        val stride = 8
        var i = 0
        while (i < mesh.vertices.size) {
            val px = mesh.vertices[i]
            val py = mesh.vertices[i + 1]
            val pz = mesh.vertices[i + 2]
            val nx = mesh.vertices[i + 3]
            val ny = mesh.vertices[i + 4]
            val nz = mesh.vertices[i + 5]
            val normalLength = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
            Assertions.assertEquals(1f, normalLength, 1e-4f)
            // Normal should point the same way as the (radius-scaled) position vector.
            Assertions.assertEquals(px / 2f, nx, 1e-4f)
            Assertions.assertEquals(py / 2f, ny, 1e-4f)
            Assertions.assertEquals(pz / 2f, nz, 1e-4f)
            i += stride
        }
    }

    @Test
    fun indicesStayWithinVertexBounds() {
        val latitudeBands = 8
        val longitudeBands = 12
        val mesh = generate(1f, latitudeBands, longitudeBands)
        val vertexCount = mesh.vertices.size / 8
        for (index in mesh.indices) {
            Assertions.assertTrue(index >= 0 && index < vertexCount, "index out of bounds: " + index)
        }
        Assertions.assertEquals(latitudeBands * longitudeBands * 6, mesh.indices.size)
    }
}
