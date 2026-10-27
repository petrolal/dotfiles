package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.OverlayCamera.Ray
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ray-sphere intersection against [OverlayCamera.unproject]'s world
 * ray -- the real 3D hit test for mesh-backed props (the heart today; the
 * syringe rack/mirror/valves once they exist, see docs/biopunk_plan.md).
 * Deliberately separate from `OverlayCamera`: unprojecting a pixel
 * into a ray is a camera concern, intersecting that ray against a volume is
 * a geometry concern, and keeping them apart means this class has no GL/JOML
 * matrix math of its own to go wrong -- just the quadratic formula -- so it
 * stays trivially testable.
 */
object Raycaster {
    /**
     * Returns the distance along `ray` to the nearest intersection
     * with the sphere at `center`/`radius`, or
     * [Float.NaN] if the ray misses it (including when the sphere is
     * entirely behind the ray's origin).
     */
    @JvmStatic
    fun intersectSphere(ray: Ray, center: Vector3f, radius: Float): Float {
        val originToCenter = Vector3f(ray.origin).sub(center)

        // Ray direction is already unit length (OverlayCamera.unproject
        // normalizes it), so a == 1 and this reduces to the textbook
        // quadratic-formula sphere test without an extra dot product.
        val b = 2f * ray.direction.dot(originToCenter)
        val c = originToCenter.dot(originToCenter) - radius * radius
        val discriminant = b * b - 4f * c
        if (discriminant < 0f) {
            return Float.NaN
        }

        val sqrtDiscriminant = sqrt(discriminant)
        val t0 = (-b - sqrtDiscriminant) / 2f
        val t1 = (-b + sqrtDiscriminant) / 2f

        val nearest = minOf(t0, t1)
        val farthest = maxOf(t0, t1)
        if (farthest < 0f) {
            // Both intersections are behind the ray's origin.
            return Float.NaN
        }
        return if (nearest >= 0f) nearest else farthest
    }

    /**
     * Returns the distance along `ray` to the nearest intersection
     * with the axis-aligned box spanning `boxMin`..`boxMax`, or
     * [Float.NaN] if it misses.
     */
    @JvmStatic
    fun intersectBox(ray: Ray, boxMin: Vector3f, boxMax: Vector3f): Float {
        var tMin = Float.NEGATIVE_INFINITY
        var tMax = Float.POSITIVE_INFINITY

        for (axis in 0..2) {
            val origin = component(ray.origin, axis)
            val direction = component(ray.direction, axis)
            val min = component(boxMin, axis)
            val max = component(boxMax, axis)

            if (abs(direction) < 1e-8f) {
                if (origin < min || origin > max) {
                    return Float.NaN
                }
                continue
            }
            val t1 = (min - origin) / direction
            val t2 = (max - origin) / direction
            tMin = maxOf(tMin, minOf(t1, t2))
            tMax = minOf(tMax, maxOf(t1, t2))
            if (tMin > tMax) {
                return Float.NaN
            }
        }

        if (tMax < 0f) {
            return Float.NaN
        }
        return if (tMin >= 0f) tMin else tMax
    }

    private fun component(v: Vector3f, axis: Int): Float = when (axis) {
        0 -> v.x
        1 -> v.y
        else -> v.z
    }
}
