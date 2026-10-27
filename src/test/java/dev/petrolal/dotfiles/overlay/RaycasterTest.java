package dev.petrolal.dotfiles.overlay;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaycasterTest {

    @Test
    void hitsSphereDeadCenter() {
        var ray = new OverlayCamera.Ray(new Vector3f(0f, 0f, 5f), new Vector3f(0f, 0f, -1f));
        float t = Raycaster.intersectSphere(ray, new Vector3f(0f, 0f, 0f), 1f);
        assertEquals(4f, t, 1e-4f); // 5 - radius 1 = distance to the near surface
    }

    @Test
    void missesSphereOffToTheSide() {
        var ray = new OverlayCamera.Ray(new Vector3f(10f, 0f, 5f), new Vector3f(0f, 0f, -1f));
        float t = Raycaster.intersectSphere(ray, new Vector3f(0f, 0f, 0f), 1f);
        assertTrue(Float.isNaN(t));
    }

    @Test
    void missesSphereBehindTheRayOrigin() {
        var ray = new OverlayCamera.Ray(new Vector3f(0f, 0f, 5f), new Vector3f(0f, 0f, 1f)); // pointing away
        float t = Raycaster.intersectSphere(ray, new Vector3f(0f, 0f, 0f), 1f);
        assertTrue(Float.isNaN(t));
    }

    @Test
    void originInsideSphereReturnsExitDistance() {
        var ray = new OverlayCamera.Ray(new Vector3f(0f, 0f, 0f), new Vector3f(0f, 0f, -1f));
        float t = Raycaster.intersectSphere(ray, new Vector3f(0f, 0f, 0f), 1f);
        assertEquals(1f, t, 1e-4f);
    }

    @Test
    void hitsBoxDeadCenter() {
        var ray = new OverlayCamera.Ray(new Vector3f(0f, 0f, 5f), new Vector3f(0f, 0f, -1f));
        float t = Raycaster.intersectBox(ray, new Vector3f(-1f, -1f, -1f), new Vector3f(1f, 1f, 1f));
        assertEquals(4f, t, 1e-4f);
    }

    @Test
    void missesBoxOffToTheSide() {
        var ray = new OverlayCamera.Ray(new Vector3f(10f, 0f, 5f), new Vector3f(0f, 0f, -1f));
        float t = Raycaster.intersectBox(ray, new Vector3f(-1f, -1f, -1f), new Vector3f(1f, 1f, 1f));
        assertTrue(Float.isNaN(t));
    }

    @Test
    void missesBoxBehindTheRayOrigin() {
        var ray = new OverlayCamera.Ray(new Vector3f(0f, 0f, 5f), new Vector3f(0f, 0f, 1f));
        float t = Raycaster.intersectBox(ray, new Vector3f(-1f, -1f, -1f), new Vector3f(1f, 1f, 1f));
        assertTrue(Float.isNaN(t));
    }

    @Test
    void axisAlignedRayParallelToAFaceStillHitsWhenWithinBounds() {
        // Ray travels purely along X, offset in Y/Z but still inside the
        // box's Y/Z extent -- exercises the "parallel to an axis" branch in
        // intersectBox without that axis' division ever running.
        var ray = new OverlayCamera.Ray(new Vector3f(-5f, 0.5f, 0.5f), new Vector3f(1f, 0f, 0f));
        float t = Raycaster.intersectBox(ray, new Vector3f(-1f, -1f, -1f), new Vector3f(1f, 1f, 1f));
        assertEquals(4f, t, 1e-4f);
    }
}
