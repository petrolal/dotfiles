package dev.petrolal.dotfiles.overlay;

/**
 * Procedurally generates a flat ribbon (not a single line) on the XZ plane
 * at a given nominal radius -- the glowing "orbital lane" guide under each
 * {@link PlanetNode}. Interleaved position(vec3)/normal(vec3, always
 * {@code (0,1,0)})/UV(vec2) vertex layout: U runs around the circumference
 * (0..1), V is 0 at the inner edge and 1 at the outer edge, which is what
 * {@code orbit_ring}'s fragment shader uses for the radial soft-edge fade
 * and circumferential light-pulse pattern.
 */
final class OrbitRingMesh {

    private OrbitRingMesh() {
    }

    record MeshData(float[] vertices, int[] indices) {
    }

    /**
     * @param nominalRadius centerline radius of the ribbon
     * @param halfWidth     half the ribbon's radial width (full width is 2x this)
     * @param segments      points around the circumference (>= 3)
     */
    static MeshData generate(float nominalRadius, float halfWidth, int segments) {
        float[] vertices = new float[segments * 2 * 8]; // inner+outer per segment, pos3+normal3+uv2
        int v = 0;

        for (int i = 0; i < segments; i++) {
            double theta = 2 * Math.PI * i / segments;
            float cos = (float) Math.cos(theta);
            float sin = (float) Math.sin(theta);
            float u = (float) i / segments;
            float innerRadius = nominalRadius - halfWidth;
            float outerRadius = nominalRadius + halfWidth;

            // Inner vertex
            vertices[v++] = innerRadius * cos;
            vertices[v++] = 0f;
            vertices[v++] = innerRadius * sin;
            vertices[v++] = 0f;
            vertices[v++] = 1f;
            vertices[v++] = 0f;
            vertices[v++] = u;
            vertices[v++] = 0f;

            // Outer vertex
            vertices[v++] = outerRadius * cos;
            vertices[v++] = 0f;
            vertices[v++] = outerRadius * sin;
            vertices[v++] = 0f;
            vertices[v++] = 1f;
            vertices[v++] = 0f;
            vertices[v++] = u;
            vertices[v++] = 1f;
        }

        // Two triangles per segment, wrapping the last segment back to
        // segment 0 so the ribbon closes seamlessly at 2*pi.
        int[] indices = new int[segments * 6];
        int idx = 0;
        for (int i = 0; i < segments; i++) {
            int innerA = i * 2;
            int outerA = i * 2 + 1;
            int next = (i + 1) % segments;
            int innerB = next * 2;
            int outerB = next * 2 + 1;

            indices[idx++] = innerA;
            indices[idx++] = outerA;
            indices[idx++] = innerB;

            indices[idx++] = outerA;
            indices[idx++] = outerB;
            indices[idx++] = innerB;
        }

        return new MeshData(vertices, indices);
    }
}
