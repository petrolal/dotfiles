package dev.petrolal.dotfiles.overlay;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayCameraTest {

    @Test
    void defaultEyeMatchesTheDocumentedStartingFraming() {
        // Default framing is "eye at (0, 1.8, 2.8) looking at the origin" --
        // eyePosition() should reconstruct that from the internal
        // yaw/pitch/distance state, not just echo back a stored vector.
        OverlayCamera camera = new OverlayCamera(1920, 1080);
        Vector3f eye = camera.eyePosition();
        assertEquals(0f, eye.x, 1e-3f);
        assertEquals(1.8f, eye.y, 1e-3f);
        assertEquals(2.8f, eye.z, 1e-3f);
    }

    @Test
    void unprojectCenterPixelPointsFromEyeTowardTarget() {
        OverlayCamera camera = new OverlayCamera(1920, 1080);
        OverlayCamera.Ray ray = camera.unproject(960f, 540f, 1920, 1080);

        Vector3f expectedDirection = new Vector3f(0f, 0f, 0f).sub(camera.eyePosition()).normalize();
        assertEquals(expectedDirection.x, ray.direction().x, 1e-3f);
        assertEquals(expectedDirection.y, ray.direction().y, 1e-3f);
        assertEquals(expectedDirection.z, ray.direction().z, 1e-3f);
    }

    @Test
    void worldToScreenAndUnprojectAreRoughlyInverse() {
        OverlayCamera camera = new OverlayCamera(1920, 1080);

        Vector3f worldPoint = new Vector3f(0.3f, 0.1f, 0.2f);
        var screen = camera.worldToScreen(worldPoint.x, worldPoint.y, worldPoint.z, 1920, 1080);

        OverlayCamera.Ray ray = camera.unproject(screen.x, screen.y, 1920, 1080);
        // ray.origin() is the near-clip-plane point for this pixel, not the
        // eye itself -- project worldPoint onto the ray's own line (both
        // points are collinear with the ray by construction) to get the
        // right parametric distance regardless of where along the ray
        // "origin" sits.
        float t = new Vector3f(worldPoint).sub(ray.origin()).dot(ray.direction());
        Vector3f pointOnRay = new Vector3f(ray.direction()).mul(t).add(ray.origin());
        assertEquals(worldPoint.x, pointOnRay.x, 0.01f);
        assertEquals(worldPoint.y, pointOnRay.y, 0.01f);
        assertEquals(worldPoint.z, pointOnRay.z, 0.05f);
    }

    @Test
    void orbitDeltaMovesTheEyeKeepingDistanceFixed() {
        OverlayCamera camera = new OverlayCamera(1920, 1080);
        float distanceBefore = camera.eyePosition().length();

        camera.applyOrbitDelta((float) Math.toRadians(45), 0f);

        float distanceAfter = camera.eyePosition().length();
        assertEquals(distanceBefore, distanceAfter, 1e-3f);
        // A 45-degree yaw with no pitch change must move the eye off its
        // original X=0 plane.
        assertTrue(Math.abs(camera.eyePosition().x) > 0.1f);
    }

    @Test
    void zoomIsClampedToTheDocumentedRange() {
        OverlayCamera camera = new OverlayCamera(1920, 1080);

        camera.zoomBy(-100f); // try to zoom in far past the minimum
        assertEquals(OverlayCamera.MIN_ORBIT_DISTANCE, camera.eyePosition().length(), 1e-3f);

        camera.zoomBy(100f); // try to zoom out far past the maximum
        assertEquals(OverlayCamera.MAX_ORBIT_DISTANCE, camera.eyePosition().length(), 1e-3f);
    }
}
