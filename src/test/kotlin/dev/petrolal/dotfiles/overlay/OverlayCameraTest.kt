package dev.petrolal.dotfiles.overlay

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.math.abs

internal class OverlayCameraTest {
    @Test
    fun defaultEyeMatchesTheDocumentedStartingFraming() {
        // Default framing is "eye at (0, 1.8, 2.8) looking at the origin" --
        // eyePosition() should reconstruct that from the internal
        // yaw/pitch/distance state, not just echo back a stored vector.
        val camera = OverlayCamera(1920, 1080)
        val eye = camera.eyePosition()
        Assertions.assertEquals(0f, eye.x, 1e-3f)
        Assertions.assertEquals(1.8f, eye.y, 1e-3f)
        Assertions.assertEquals(2.8f, eye.z, 1e-3f)
    }

    @Test
    fun unprojectCenterPixelPointsFromEyeTowardTarget() {
        val camera = OverlayCamera(1920, 1080)
        val ray = camera.unproject(960f, 540f, 1920, 1080)

        val expectedDirection = Vector3f(0f, 0f, 0f).sub(camera.eyePosition()).normalize()
        Assertions.assertEquals(expectedDirection.x, ray.direction.x, 1e-3f)
        Assertions.assertEquals(expectedDirection.y, ray.direction.y, 1e-3f)
        Assertions.assertEquals(expectedDirection.z, ray.direction.z, 1e-3f)
    }

    @Test
    fun worldToScreenAndUnprojectAreRoughlyInverse() {
        val camera = OverlayCamera(1920, 1080)

        val worldPoint = Vector3f(0.3f, 0.1f, 0.2f)
        val screen = camera.worldToScreen(worldPoint.x, worldPoint.y, worldPoint.z, 1920, 1080)

        val ray = camera.unproject(screen.x, screen.y, 1920, 1080)
        // ray.origin() is the near-clip-plane point for this pixel, not the
        // eye itself -- project worldPoint onto the ray's own line (both
        // points are collinear with the ray by construction) to get the
        // right parametric distance regardless of where along the ray
        // "origin" sits.
        val t = Vector3f(worldPoint).sub(ray.origin).dot(ray.direction)
        val pointOnRay = Vector3f(ray.direction).mul(t).add(ray.origin)
        Assertions.assertEquals(worldPoint.x, pointOnRay.x, 0.01f)
        Assertions.assertEquals(worldPoint.y, pointOnRay.y, 0.01f)
        Assertions.assertEquals(worldPoint.z, pointOnRay.z, 0.05f)
    }

    @Test
    fun orbitDeltaMovesTheEyeKeepingDistanceFixed() {
        val camera = OverlayCamera(1920, 1080)
        val distanceBefore = camera.eyePosition().length()

        camera.applyOrbitDelta(Math.toRadians(45.0).toFloat(), 0f)

        val distanceAfter = camera.eyePosition().length()
        Assertions.assertEquals(distanceBefore, distanceAfter, 1e-3f)
        // A 45-degree yaw with no pitch change must move the eye off its
        // original X=0 plane.
        Assertions.assertTrue(abs(camera.eyePosition().x) > 0.1f)
    }

    @Test
    fun zoomIsClampedToTheDocumentedRange() {
        val camera = OverlayCamera(1920, 1080)

        camera.zoomBy(-100f) // try to zoom in far past the minimum
        Assertions.assertEquals(OverlayCamera.MIN_ORBIT_DISTANCE, camera.eyePosition().length(), 1e-3f)

        camera.zoomBy(100f) // try to zoom out far past the maximum
        Assertions.assertEquals(OverlayCamera.MAX_ORBIT_DISTANCE, camera.eyePosition().length(), 1e-3f)
    }
}
