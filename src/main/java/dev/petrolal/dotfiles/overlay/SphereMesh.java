package dev.petrolal.dotfiles.overlay;

/**
 * Procedurally generates a UV sphere's interleaved vertex data (position,
 * normal, UV -- the layout {@code heart.vert}'s {@code aPos}/{@code aNormal}/
 * {@code aTexCoords} attributes expect) and its triangle index list.
 *
 * <p>Hand-authored procedural geometry, not an imported asset: this
 * deliberately avoids pulling in {@code org.lwjgl.assimp} (see
 * docs/biopunk_plan.md Phase 2 -- no existing native-image reachability
 * precedent for it, no art assets to load yet). A sphere is one of the few
 * "meshes" simple enough to not need an asset pipeline at all; this is
 * expected to stay the exception, not the start of a hand-rolled mesh
 * library.
 */
final class SphereMesh {

    private SphereMesh() {
    }

    record MeshData(float[] vertices, int[] indices) {
    }

    /**
     * @param radius      sphere radius in local (pre-model-matrix) space
     * @param latitudeBands  bands from pole to pole (>= 2)
     * @param longitudeBands bands around the equator (>= 3)
     */
    static MeshData generate(float radius, int latitudeBands, int longitudeBands) {
        int vertexCount = (latitudeBands + 1) * (longitudeBands + 1);
        float[] vertices = new float[vertexCount * 8]; // pos3 + normal3 + uv2
        int v = 0;

        for (int lat = 0; lat <= latitudeBands; lat++) {
            double theta = Math.PI * lat / latitudeBands; // 0 (north pole) -> PI (south pole)
            double sinTheta = Math.sin(theta);
            double cosTheta = Math.cos(theta);

            for (int lon = 0; lon <= longitudeBands; lon++) {
                double phi = 2 * Math.PI * lon / longitudeBands;
                double sinPhi = Math.sin(phi);
                double cosPhi = Math.cos(phi);

                float nx = (float) (cosPhi * sinTheta);
                float ny = (float) cosTheta;
                float nz = (float) (sinPhi * sinTheta);

                vertices[v++] = nx * radius;
                vertices[v++] = ny * radius;
                vertices[v++] = nz * radius;
                vertices[v++] = nx;
                vertices[v++] = ny;
                vertices[v++] = nz;
                vertices[v++] = (float) lon / longitudeBands;
                vertices[v++] = (float) lat / latitudeBands;
            }
        }

        int quadCount = latitudeBands * longitudeBands;
        int[] indices = new int[quadCount * 6];
        int i = 0;
        for (int lat = 0; lat < latitudeBands; lat++) {
            for (int lon = 0; lon < longitudeBands; lon++) {
                int first = lat * (longitudeBands + 1) + lon;
                int second = first + longitudeBands + 1;

                indices[i++] = first;
                indices[i++] = second;
                indices[i++] = first + 1;

                indices[i++] = second;
                indices[i++] = second + 1;
                indices[i++] = first + 1;
            }
        }

        return new MeshData(vertices, indices);
    }
}
