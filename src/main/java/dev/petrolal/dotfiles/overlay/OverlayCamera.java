package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Manual perspective camera (see docs/migration_plan.md section 1 for why
 * this is hand-rolled JOML instead of libGDX's PerspectiveCamera: libGDX's
 * reflection-heavy asset/camera classes fight GraalVM native-image's
 * reachability analysis). Holds the view and projection matrices the app-node
 * mesh/quad renderer will consume; no GL calls here, so this stays testable
 * without a live GL context.
 */
public final class OverlayCamera {

    private static final float FOV_DEGREES = 60f;
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 100f;

    private final Vector3f position = new Vector3f(0f, 0f, 5f);
    private final Vector3f target = new Vector3f(0f, 0f, 0f);
    private final Vector3f up = new Vector3f(0f, 1f, 0f);

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();

    private float aspectRatio = 1f;

    public OverlayCamera(int framebufferWidth, int framebufferHeight) {
        resize(framebufferWidth, framebufferHeight);
        updateView();
    }

    /** Call when the window/framebuffer size changes (GLFW framebuffer-size callback). */
    public void resize(int framebufferWidth, int framebufferHeight) {
        aspectRatio = framebufferHeight == 0 ? 1f : (float) framebufferWidth / framebufferHeight;
        projection.identity().perspective((float) Math.toRadians(FOV_DEGREES), aspectRatio, NEAR_PLANE, FAR_PLANE);
    }

    private void updateView() {
        view.identity().lookAt(position, target, up);
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        updateView();
    }

    public Matrix4f projectionMatrix() {
        return projection;
    }

    public Matrix4f viewMatrix() {
        return view;
    }

    public float aspectRatio() {
        return aspectRatio;
    }

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
}
