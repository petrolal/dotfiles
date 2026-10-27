package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Manual perspective camera (see docs/migration_plan.md section 1 for why
 * this is hand-rolled JOML instead of libGDX's PerspectiveCamera: libGDX's
 * reflection-heavy asset/camera classes fight GraalVM native-image's
 * reachability analysis), free-orbiting around a fixed {@link #target}
 * (the Astroland system's central star at the origin) via spherical
 * yaw/pitch/distance state -- see {@link #applyOrbitDelta}/{@link #zoomBy},
 * driven from {@code OverlayWindow}'s mouse-drag/scroll callbacks. No GL
 * calls here, so this stays testable without a live GL context.
 */
public final class OverlayCamera {

    private static final float FOV_DEGREES = 60f;
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 50f;

    static final float MIN_ORBIT_DISTANCE = 1.2f;
    static final float MAX_ORBIT_DISTANCE = 5.0f;
    /** Clamped shy of the poles so the camera can't flip upside down or
     * cross over the target -- standard free-orbit-camera guard. */
    private static final float MAX_PITCH_RADIANS = (float) Math.toRadians(89f);

    private final Vector3f position = new Vector3f();
    private final Vector3f target = new Vector3f(0f, 0f, 0f);
    private final Vector3f up = new Vector3f(0f, 1f, 0f);

    // Free-orbit state around `target` -- see applyOrbitDelta/zoomBy. Default
    // eye (0, 1.8, 2.8) looking at the origin: distance = |eye - target|,
    // yaw/pitch derived from that same vector (see the constructor).
    private float yawRadians;
    private float pitchRadians;
    private float distance;

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();

    private float aspectRatio = 1f;

    public OverlayCamera(int framebufferWidth, int framebufferHeight) {
        resize(framebufferWidth, framebufferHeight);
        setOrbitFromEye(new Vector3f(0f, 1.8f, 2.8f));
    }

    /** Derives the initial yaw/pitch/distance from a literal eye position,
     * so the default framing can still be specified as "eye at (x,y,z)"
     * (matches how every other camera framing in this codebase has been
     * expressed) while the camera itself is orbit-parameterized internally. */
    private void setOrbitFromEye(Vector3f eye) {
        Vector3f offset = new Vector3f(eye).sub(target);
        distance = offset.length();
        pitchRadians = (float) Math.asin(offset.y / distance);
        yawRadians = (float) Math.atan2(offset.x, offset.z);
        updateOrbitPosition();
    }

    /** Recomputes {@link #position} from the current yaw/pitch/distance
     * (standard spherical-to-Cartesian around {@link #target}) and the view
     * matrix from that -- called after every orbit/zoom change. */
    private void updateOrbitPosition() {
        float horizontalRadius = distance * (float) Math.cos(pitchRadians);
        position.set(
                target.x + horizontalRadius * (float) Math.sin(yawRadians),
                target.y + distance * (float) Math.sin(pitchRadians),
                target.z + horizontalRadius * (float) Math.cos(yawRadians));
        updateView();
    }

    /** Mouse-drag orbit: positive {@code deltaYaw} orbits right, positive
     * {@code deltaPitch} orbits up. Both in radians -- the caller (GLFW
     * cursor-delta handling) decides the pixels-to-radians scale. */
    public void applyOrbitDelta(float deltaYawRadians, float deltaPitchRadians) {
        yawRadians += deltaYawRadians;
        pitchRadians = Math.clamp(pitchRadians + deltaPitchRadians, -MAX_PITCH_RADIANS, MAX_PITCH_RADIANS);
        updateOrbitPosition();
    }

    /** Scroll-wheel zoom, clamped to [{@link #MIN_ORBIT_DISTANCE}, {@link #MAX_ORBIT_DISTANCE}]. */
    public void zoomBy(float deltaDistance) {
        distance = Math.clamp(distance + deltaDistance, MIN_ORBIT_DISTANCE, MAX_ORBIT_DISTANCE);
        updateOrbitPosition();
    }

    /** Call when the window/framebuffer size changes (GLFW framebuffer-size callback). */
    public void resize(int framebufferWidth, int framebufferHeight) {
        aspectRatio = framebufferHeight == 0 ? 1f : (float) framebufferWidth / framebufferHeight;
        projection.identity().perspective((float) Math.toRadians(FOV_DEGREES), aspectRatio, NEAR_PLANE, FAR_PLANE);
    }

    private void updateView() {
        view.identity().lookAt(position, target, up);
    }

    public Matrix4f projectionMatrix() {
        return projection;
    }

    public Matrix4f viewMatrix() {
        return view;
    }

    public Vector3f eyePosition() {
        return new Vector3f(position);
    }

    public float aspectRatio() {
        return aspectRatio;
    }

    /**
     * Unprojects a screen-space pixel coordinate (origin top-left, same
     * convention as {@link #worldToScreen}) into a world-space ray from the
     * camera's eye through that pixel -- the inverse of
     * {@code worldToScreen}'s view-projection transform. Used for true 3D
     * hit-testing against mesh bounding volumes (see {@code Raycaster}),
     * as opposed to {@code worldToScreen}'s screen-space-box approach used
     * for the (coplanar, billboard-style) app-launcher nodes.
     */
    public Ray unproject(float screenX, float screenY, int framebufferWidth, int framebufferHeight) {
        float ndcX = (2f * screenX) / framebufferWidth - 1f;
        float ndcY = 1f - (2f * screenY) / framebufferHeight;

        Matrix4f viewProjection = new Matrix4f();
        projection.mul(view, viewProjection);
        Matrix4f inverseViewProjection = viewProjection.invert(new Matrix4f());

        Vector4f nearPoint = new Vector4f(ndcX, ndcY, -1f, 1f);
        Vector4f farPoint = new Vector4f(ndcX, ndcY, 1f, 1f);
        inverseViewProjection.transform(nearPoint);
        inverseViewProjection.transform(farPoint);
        nearPoint.div(nearPoint.w);
        farPoint.div(farPoint.w);

        Vector3f origin = new Vector3f(nearPoint.x, nearPoint.y, nearPoint.z);
        Vector3f direction = new Vector3f(farPoint.x - nearPoint.x, farPoint.y - nearPoint.y, farPoint.z - nearPoint.z)
                .normalize();
        return new Ray(origin, direction);
    }

    /** A world-space ray: origin plus a unit direction. */
    public record Ray(Vector3f origin, Vector3f direction) {}

    /**
     * Projects a world-space point to framebuffer pixel coordinates
     * (origin top-left, matching {@link TextRenderer}'s screen space) --
     * used to position a node's label under its 3D quad instead of in a
     * separate decorative row.
     */
    public Vector2f worldToScreen(float x, float y, float z, int framebufferWidth, int framebufferHeight) {
        Matrix4f viewProjection = new Matrix4f();
        projection.mul(view, viewProjection);

        Vector4f clip = new Vector4f(x, y, z, 1f);
        viewProjection.transform(clip);

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float screenX = (ndcX * 0.5f + 0.5f) * framebufferWidth;
        float screenY = (1f - (ndcY * 0.5f + 0.5f)) * framebufferHeight;
        return new Vector2f(screenX, screenY);
    }

    /**
     * Same projection as {@link #worldToScreen}, but guarded against points
     * behind (or right on) the camera plane: {@code clip.w} going to zero
     * or negative makes the perspective divide blow up or flip the point
     * to the opposite side of the screen, which {@code worldToScreen}
     * doesn't protect against. Returns {@code null} when
     * {@code clip.w <= 0.05} instead of a garbage screen position -- used
     * for HUD labels, which must disappear rather than flash to a wrong
     * corner when their anchor point crosses behind the camera.
     */
    public Vector2f worldToScreenOrNull(float x, float y, float z, int framebufferWidth, int framebufferHeight) {
        Matrix4f viewProjection = new Matrix4f();
        projection.mul(view, viewProjection);

        Vector4f clip = new Vector4f(x, y, z, 1f);
        viewProjection.transform(clip);

        if (clip.w <= 0.05f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float screenX = (ndcX * 0.5f + 0.5f) * framebufferWidth;
        float screenY = (1f - (ndcY * 0.5f + 0.5f)) * framebufferHeight;
        return new Vector2f(screenX, screenY);
    }
}
