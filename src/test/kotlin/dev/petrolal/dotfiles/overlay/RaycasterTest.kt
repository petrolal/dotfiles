package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.OverlayCamera.Ray
import dev.petrolal.dotfiles.overlay.Raycaster.intersectBox
import dev.petrolal.dotfiles.overlay.Raycaster.intersectSphere
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.Float

internal class RaycasterTest {
    @Test
    fun hitsSphereDeadCenter() {
        val ray = Ray(Vector3f(0f, 0f, 5f), Vector3f(0f, 0f, -1f))
        val t = intersectSphere(ray, Vector3f(0f, 0f, 0f), 1f)
        Assertions.assertEquals(4f, t, 1e-4f) // 5 - radius 1 = distance to the near surface
    }

    @Test
    fun missesSphereOffToTheSide() {
        val ray = Ray(Vector3f(10f, 0f, 5f), Vector3f(0f, 0f, -1f))
        val t = intersectSphere(ray, Vector3f(0f, 0f, 0f), 1f)
        Assertions.assertTrue(Float.isNaN(t))
    }

    @Test
    fun missesSphereBehindTheRayOrigin() {
        val ray = Ray(Vector3f(0f, 0f, 5f), Vector3f(0f, 0f, 1f)) // pointing away
        val t = intersectSphere(ray, Vector3f(0f, 0f, 0f), 1f)
        Assertions.assertTrue(Float.isNaN(t))
    }

    @Test
    fun originInsideSphereReturnsExitDistance() {
        val ray = Ray(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, -1f))
        val t = intersectSphere(ray, Vector3f(0f, 0f, 0f), 1f)
        Assertions.assertEquals(1f, t, 1e-4f)
    }

    @Test
    fun hitsBoxDeadCenter() {
        val ray = Ray(Vector3f(0f, 0f, 5f), Vector3f(0f, 0f, -1f))
        val t = intersectBox(ray, Vector3f(-1f, -1f, -1f), Vector3f(1f, 1f, 1f))
        Assertions.assertEquals(4f, t, 1e-4f)
    }

    @Test
    fun missesBoxOffToTheSide() {
        val ray = Ray(Vector3f(10f, 0f, 5f), Vector3f(0f, 0f, -1f))
        val t = intersectBox(ray, Vector3f(-1f, -1f, -1f), Vector3f(1f, 1f, 1f))
        Assertions.assertTrue(Float.isNaN(t))
    }

    @Test
    fun missesBoxBehindTheRayOrigin() {
        val ray = Ray(Vector3f(0f, 0f, 5f), Vector3f(0f, 0f, 1f))
        val t = intersectBox(ray, Vector3f(-1f, -1f, -1f), Vector3f(1f, 1f, 1f))
        Assertions.assertTrue(Float.isNaN(t))
    }

    @Test
    fun axisAlignedRayParallelToAFaceStillHitsWhenWithinBounds() {
        // Ray travels purely along X, offset in Y/Z but still inside the
        // box's Y/Z extent -- exercises the "parallel to an axis" branch in
        // intersectBox without that axis' division ever running.
        val ray = Ray(Vector3f(-5f, 0.5f, 0.5f), Vector3f(1f, 0f, 0f))
        val t = intersectBox(ray, Vector3f(-1f, -1f, -1f), Vector3f(1f, 1f, 1f))
        Assertions.assertEquals(4f, t, 1e-4f)
    }
}
