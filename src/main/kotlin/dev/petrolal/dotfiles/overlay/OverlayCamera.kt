package dev.petrolal.dotfiles.overlay

import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manual perspective camera (see docs/migration_plan.md section 1 for why
 * this is hand-rolled JOML instead of libGDX's PerspectiveCamera: libGDX's
 * reflection-heavy asset/camera classes fight GraalVM native-image's
 * reachability analysis), free-orbiting around a fixed [target]
 * (the Astroland system's central star at the origin) via spherical
 * yaw/pitch/distance state -- see [applyOrbitDelta]/[zoomBy],
 * driven from `OverlayWindow`'s mouse-drag/scroll callbacks. No GL
 * calls here, so this stays testable without a live GL context.
 */
class OverlayCamera(framebufferWidth: Int, framebufferHeight: Int) {
    private val position = Vector3f()
    private val target = Vector3f(0f, 0f, 0f)
    private val up = Vector3f(0f, 1f, 0f)

    // Free-orbit state around `target` -- see applyOrbitDelta/zoomBy. Default
    // eye (0, 1.8, 2.8) looking at the origin: distance = |eye - target|,
    // yaw/pitch derived from that same vector (see the constructor).
    private var yawRadians = 0f
    private var pitchRadians = 0f
    private var distance = 0f

    private val projection = Matrix4f()
    private val view = Matrix4f()

    private var aspectRatio = 1f

    init {
        resize(framebufferWidth, framebufferHeight)
        setOrbitFromEye(Vector3f(0f, 1.8f, 2.8f))
    }

    /** Derives the initial yaw/pitch/distance from a literal eye position,
     * so the default framing can still be specified as "eye at (x,y,z)"
     * (matches how every other camera framing in this codebase has been
     * expressed) while the camera itself is orbit-parameterized internally.  */
    private fun setOrbitFromEye(eye: Vector3f) {
        val offset = Vector3f(eye).sub(target)
        distance = offset.length()
        pitchRadians = asin(offset.y / distance)
        yawRadians = atan2(offset.x, offset.z)
        updateOrbitPosition()
    }

    /** Recomputes [position] from the current yaw/pitch/distance
     * (standard spherical-to-Cartesian around [target]) and the view
     * matrix from that -- called after every orbit/zoom change.  */
    private fun updateOrbitPosition() {
        val horizontalRadius = distance * cos(pitchRadians)
        position.set(
            target.x + horizontalRadius * sin(yawRadians),
            target.y + distance * sin(pitchRadians),
            target.z + horizontalRadius * cos(yawRadians)
        )
        updateView()
    }

    /** Mouse-drag orbit: positive `deltaYaw` orbits right, positive
     * `deltaPitch` orbits up. Both in radians -- the caller (GLFW
     * cursor-delta handling) decides the pixels-to-radians scale.  */
    fun applyOrbitDelta(deltaYawRadians: Float, deltaPitchRadians: Float) {
        yawRadians += deltaYawRadians
        pitchRadians = (pitchRadians + deltaPitchRadians).coerceIn(-MAX_PITCH_RADIANS, MAX_PITCH_RADIANS)
        updateOrbitPosition()
    }

    /** Scroll-wheel zoom, clamped to [[MIN_ORBIT_DISTANCE], [MAX_ORBIT_DISTANCE]].  */
    fun zoomBy(deltaDistance: Float) {
        distance = (distance + deltaDistance).coerceIn(MIN_ORBIT_DISTANCE, MAX_ORBIT_DISTANCE)
        updateOrbitPosition()
    }

    /** Call when the window/framebuffer size changes (GLFW framebuffer-size callback).  */
    fun resize(framebufferWidth: Int, framebufferHeight: Int) {
        aspectRatio = if (framebufferHeight == 0) 1f else framebufferWidth.toFloat() / framebufferHeight
        projection.identity()
            .perspective(Math.toRadians(FOV_DEGREES.toDouble()).toFloat(), aspectRatio, NEAR_PLANE, FAR_PLANE)
    }

    private fun updateView() {
        view.identity().lookAt(position, target, up)
    }

    fun projectionMatrix(): Matrix4f = projection

    fun viewMatrix(): Matrix4f = view

    fun eyePosition(): Vector3f = Vector3f(position)

    fun aspectRatio(): Float = aspectRatio

    /**
     * Unprojects a screen-space pixel coordinate (origin top-left, same
     * convention as [worldToScreen]) into a world-space ray from the
     * camera's eye through that pixel -- the inverse of
     * `worldToScreen`'s view-projection transform. Used for true 3D
     * hit-testing against mesh bounding volumes (see `Raycaster`),
     * as opposed to `worldToScreen`'s screen-space-box approach used
     * for the (coplanar, billboard-style) app-launcher nodes.
     */
    fun unproject(screenX: Float, screenY: Float, framebufferWidth: Int, framebufferHeight: Int): Ray {
        val ndcX = (2f * screenX) / framebufferWidth - 1f
        val ndcY = 1f - (2f * screenY) / framebufferHeight

        val viewProjection = Matrix4f()
        projection.mul(view, viewProjection)
        val inverseViewProjection = viewProjection.invert(Matrix4f())

        val nearPoint = Vector4f(ndcX, ndcY, -1f, 1f)
        val farPoint = Vector4f(ndcX, ndcY, 1f, 1f)
        inverseViewProjection.transform(nearPoint)
        inverseViewProjection.transform(farPoint)
        nearPoint.div(nearPoint.w)
        farPoint.div(farPoint.w)

        val origin = Vector3f(nearPoint.x, nearPoint.y, nearPoint.z)
        val direction = Vector3f(farPoint.x - nearPoint.x, farPoint.y - nearPoint.y, farPoint.z - nearPoint.z)
            .normalize()
        return Ray(origin, direction)
    }

    /** A world-space ray: origin plus a unit direction.  */
    @JvmRecord
    data class Ray(val origin: Vector3f, val direction: Vector3f)

    /**
     * Projects a world-space point to framebuffer pixel coordinates
     * (origin top-left, matching [TextRenderer]'s screen space) --
     * used to position a node's label under its 3D quad instead of in a
     * separate decorative row.
     */
    fun worldToScreen(x: Float, y: Float, z: Float, framebufferWidth: Int, framebufferHeight: Int): Vector2f {
        val viewProjection = Matrix4f()
        projection.mul(view, viewProjection)

        val clip = Vector4f(x, y, z, 1f)
        viewProjection.transform(clip)

        val ndcX = clip.x / clip.w
        val ndcY = clip.y / clip.w
        val screenX = (ndcX * 0.5f + 0.5f) * framebufferWidth
        val screenY = (1f - (ndcY * 0.5f + 0.5f)) * framebufferHeight
        return Vector2f(screenX, screenY)
    }

    /**
     * Same projection as [worldToScreen], but guarded against points
     * behind (or right on) the camera plane: `clip.w` going to zero
     * or negative makes the perspective divide blow up or flip the point
     * to the opposite side of the screen, which `worldToScreen`
     * doesn't protect against. Returns `null` when
     * `clip.w <= 0.05` instead of a garbage screen position -- used
     * for HUD labels, which must disappear rather than flash to a wrong
     * corner when their anchor point crosses behind the camera.
     */
    fun worldToScreenOrNull(x: Float, y: Float, z: Float, framebufferWidth: Int, framebufferHeight: Int): Vector2f? {
        val viewProjection = Matrix4f()
        projection.mul(view, viewProjection)

        val clip = Vector4f(x, y, z, 1f)
        viewProjection.transform(clip)

        if (clip.w <= 0.05f) {
            return null
        }

        val ndcX = clip.x / clip.w
        val ndcY = clip.y / clip.w
        val screenX = (ndcX * 0.5f + 0.5f) * framebufferWidth
        val screenY = (1f - (ndcY * 0.5f + 0.5f)) * framebufferHeight
        return Vector2f(screenX, screenY)
    }

    companion object {
        private const val FOV_DEGREES = 60f
        private const val NEAR_PLANE = 0.05f
        private const val FAR_PLANE = 50f

        const val MIN_ORBIT_DISTANCE: Float = 1.2f
        const val MAX_ORBIT_DISTANCE: Float = 5.0f

        /** Clamped shy of the poles so the camera can't flip upside down or
         * cross over the target -- standard free-orbit-camera guard.  */
        private val MAX_PITCH_RADIANS = Math.toRadians(89.0).toFloat()
    }
}
