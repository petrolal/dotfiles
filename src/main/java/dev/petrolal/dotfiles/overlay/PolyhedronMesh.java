package dev.petrolal.dotfiles.overlay;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Procedurally generates the three Platonic solids Astroland's node
 * geometry is built from. Hand-authored, not an imported asset -- same
 * reasoning as {@link SphereMesh}'s javadoc (no {@code org.lwjgl.assimp}
 * dependency, no art pipeline).
 *
 * <p>Each shape yields two independent buffers: {@link #generateFacets} (flat
 * -shaded, per-face-duplicated vertices for the translucent obsidian body)
 * and {@link #generateWireframe} (the solid's real unique vertices, as
 * {@code GL_LINES} index pairs over its edges, for the glowing edge overlay).
 * All vertices are normalized to unit length so every shape scales the same
 * way under {@code PlanetNode.sphereRadius()}, matching the old unit-sphere
 * convention it replaces.
 */
final class PolyhedronMesh {

    enum Shape { TETRAHEDRON, OCTAHEDRON, ICOSAHEDRON }

    record FacetData(float[] vertices, int[] indices) {
    }

    record WireframeData(float[] vertices, int[] indices) {
    }

    private record RawSolid(Vector3f[] vertices, int[][] faces) {
    }

    private PolyhedronMesh() {
    }

    static FacetData generateFacets(Shape shape) {
        RawSolid solid = raw(shape);
        float[] vertices = new float[solid.faces().length * 3 * 6]; // pos3 + normal3, 3 verts/face
        int[] indices = new int[solid.faces().length * 3];
        int v = 0;
        int idx = 0;

        for (int[] face : solid.faces()) {
            Vector3f p0 = solid.vertices()[face[0]];
            Vector3f p1 = solid.vertices()[face[1]];
            Vector3f p2 = solid.vertices()[face[2]];

            Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p2).sub(p0));
            Vector3f centroid = new Vector3f(p0).add(p1).add(p2).mul(1f / 3f);
            if (normal.dot(centroid) < 0f) {
                // Winding is inward (would be back-face culled from outside) --
                // swap the last two vertices to flip it, which also flips the
                // normal we're about to store to match.
                Vector3f tmp = p1;
                p1 = p2;
                p2 = tmp;
                normal.negate();
            }
            normal.normalize();

            for (Vector3f p : new Vector3f[]{p0, p1, p2}) {
                vertices[v++] = p.x;
                vertices[v++] = p.y;
                vertices[v++] = p.z;
                vertices[v++] = normal.x;
                vertices[v++] = normal.y;
                vertices[v++] = normal.z;
                indices[idx] = idx;
                idx++;
            }
        }

        return new FacetData(vertices, indices);
    }

    static WireframeData generateWireframe(Shape shape) {
        RawSolid solid = raw(shape);
        float[] vertices = new float[solid.vertices().length * 3];
        int v = 0;
        for (Vector3f p : solid.vertices()) {
            vertices[v++] = p.x;
            vertices[v++] = p.y;
            vertices[v++] = p.z;
        }

        Set<Long> seenEdges = new HashSet<>();
        List<Integer> edgeIndices = new ArrayList<>();
        for (int[] face : solid.faces()) {
            for (int e = 0; e < 3; e++) {
                int a = face[e];
                int b = face[(e + 1) % 3];
                long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
                if (seenEdges.add(key)) {
                    edgeIndices.add(a);
                    edgeIndices.add(b);
                }
            }
        }

        int[] indices = edgeIndices.stream().mapToInt(Integer::intValue).toArray();
        return new WireframeData(vertices, indices);
    }

    private static RawSolid raw(Shape shape) {
        return switch (shape) {
            case TETRAHEDRON -> tetrahedron();
            case OCTAHEDRON -> octahedron();
            case ICOSAHEDRON -> icosahedron();
        };
    }

    private static RawSolid tetrahedron() {
        Vector3f[] vertices = normalized(
                new Vector3f(1, 1, 1),
                new Vector3f(1, -1, -1),
                new Vector3f(-1, 1, -1),
                new Vector3f(-1, -1, 1)
        );
        int[][] faces = {
                {0, 1, 2},
                {0, 3, 1},
                {0, 2, 3},
                {1, 3, 2},
        };
        return new RawSolid(vertices, faces);
    }

    private static RawSolid octahedron() {
        Vector3f[] vertices = normalized(
                new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
                new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
                new Vector3f(0, 0, 1), new Vector3f(0, 0, -1)
        );
        int px = 0, nx = 1, py = 2, ny = 3, pz = 4, nz = 5;
        int[][] faces = {
                {px, py, pz}, {py, nx, pz}, {nx, ny, pz}, {ny, px, pz},
                {py, px, nz}, {nx, py, nz}, {ny, nx, nz}, {px, ny, nz},
        };
        return new RawSolid(vertices, faces);
    }

    private static RawSolid icosahedron() {
        float phi = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);
        Vector3f[] vertices = normalized(
                new Vector3f(-1, phi, 0), new Vector3f(1, phi, 0),
                new Vector3f(-1, -phi, 0), new Vector3f(1, -phi, 0),
                new Vector3f(0, -1, phi), new Vector3f(0, 1, phi),
                new Vector3f(0, -1, -phi), new Vector3f(0, 1, -phi),
                new Vector3f(phi, 0, -1), new Vector3f(phi, 0, 1),
                new Vector3f(-phi, 0, -1), new Vector3f(-phi, 0, 1)
        );
        int[][] faces = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1},
        };
        return new RawSolid(vertices, faces);
    }

    private static Vector3f[] normalized(Vector3f... points) {
        for (Vector3f p : points) {
            p.normalize();
        }
        return points;
    }
}
