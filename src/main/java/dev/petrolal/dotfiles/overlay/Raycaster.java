package dev.petrolal.dotfiles.overlay;

import org.joml.Vector3f;

/**
 * Ray-sphere intersection against {@link OverlayCamera#unproject}'s world
 * ray -- the real 3D hit test for mesh-backed props (the heart today; the
 * syringe rack/mirror/valves once they exist, see docs/biopunk_plan.md).
 * Deliberately separate from {@code OverlayCamera}: unprojecting a pixel
 * into a ray is a camera concern, intersecting that ray against a volume is
 * a geometry concern, and keeping them apart means this class has no GL/JOML
 * matrix math of its own to go wrong -- just the quadratic formula -- so it
 * stays trivially testable.
 */
final class Raycaster {

    private Raycaster() {
    }

    /**
     * Returns the distance along {@code ray} to the nearest intersection
     * with the sphere at {@code center}/{@code radius}, or
     * {@link Float#NaN} if the ray misses it (including when the sphere is
     * entirely behind the ray's origin).
     */
    static float intersectSphere(OverlayCamera.Ray ray, Vector3f center, float radius) {
        Vector3f originToCenter = new Vector3f(ray.origin()).sub(center);

        // Ray direction is already unit length (OverlayCamera.unproject
        // normalizes it), so a == 1 and this reduces to the textbook
        // quadratic-formula sphere test without an extra dot product.
        float b = 2f * ray.direction().dot(originToCenter);
        float c = originToCenter.dot(originToCenter) - radius * radius;
        float discriminant = b * b - 4f * c;
        if (discriminant < 0f) {
            return Float.NaN;
        }

        float sqrtDiscriminant = (float) Math.sqrt(discriminant);
        float t0 = (-b - sqrtDiscriminant) / 2f;
        float t1 = (-b + sqrtDiscriminant) / 2f;

        float nearest = Math.min(t0, t1);
        float farthest = Math.max(t0, t1);
        if (farthest < 0f) {
            // Both intersections are behind the ray's origin.
            return Float.NaN;
        }
        return nearest >= 0f ? nearest : farthest;
    }

    /**
     * Returns the distance along {@code ray} to the nearest intersection
     * with the axis-aligned box spanning {@code boxMin}..{@code boxMax}, or
     * {@link Float#NaN} if it misses -- the standard "slab" test: clip the
     * ray's parametric range against each axis's pair of planes in turn,
     * degenerate-axis division (direction component 0) handled by treating
     * a parallel-and-outside ray as a miss rather than dividing by zero.
     * Used for the syringe rack's slots (small boxes), where a sphere
     * approximation would be visibly too generous/stingy depending on
     * orientation -- unlike the heart, which reads fine as a sphere.
     */
    static float intersectBox(OverlayCamera.Ray ray, Vector3f boxMin, Vector3f boxMax) {
        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;

        for (int axis = 0; axis < 3; axis++) {
            float origin = component(ray.origin(), axis);
            float direction = component(ray.direction(), axis);
            float min = component(boxMin, axis);
            float max = component(boxMax, axis);

            if (Math.abs(direction) < 1e-8f) {
                if (origin < min || origin > max) {
                    return Float.NaN;
                }
                continue;
            }
            float t1 = (min - origin) / direction;
            float t2 = (max - origin) / direction;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) {
                return Float.NaN;
            }
        }

        if (tMax < 0f) {
            return Float.NaN;
        }
        return tMin >= 0f ? tMin : tMax;
    }

    private static float component(Vector3f v, int axis) {
        return switch (axis) {
            case 0 -> v.x;
            case 1 -> v.y;
            default -> v.z;
        };
    }
}
