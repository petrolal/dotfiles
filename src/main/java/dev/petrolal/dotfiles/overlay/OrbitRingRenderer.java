package dev.petrolal.dotfiles.overlay;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Draws one glowing bio-luminescent ribbon per orbital lane. Builds one
 * mesh/VAO per distinct {@code orbitalRadius} at construction time, each
 * generated directly at its real-world radius (see {@link OrbitRingMesh}) --
 * deliberately NOT a single shared unit-radius mesh scaled per draw call,
 * because scaling would also scale the ribbon's half-width, breaking the
 * "constant width regardless of radius" look a shared, translate-only mesh
 * gives. Only a handful of rings ever exist (one per {@link PlanetNode}),
 * so a few KB of extra VBO data per ring is a non-issue.
 */
final class OrbitRingRenderer {

    private static final String VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aUV;

            out vec2 vUV;

            uniform mat4 u_view;
            uniform mat4 u_projection;
            uniform float u_time;

            void main() {
                vUV = aUV;
                vec3 pos = aPos;
                pos.y += sin(u_time * 1.5 + aPos.x * 2.0 + aPos.z * 2.0) * 0.01;
                gl_Position = u_projection * u_view * vec4(pos, 1.0);
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;

            uniform float u_time;
            uniform float u_ripple; // 0..1, decays after a click -- see CelestialSystem

            void main() {
                float edgeDist = abs(vUV.y - 0.5) * 2.0;
                float radialFade = smoothstep(1.0, 0.15, edgeDist);

                float pulse = pow(sin((vUV.x * 16.0 - u_time * 2.5) * 3.14159) * 0.5 + 0.5, 5.0);

                vec3 emberColor = vec3(1.0, 0.35, 0.1);
                vec3 rippleColor = vec3(1.0, 0.8, 0.3);
                vec3 color = mix(emberColor, rippleColor, u_ripple);

                float brightness = (0.25 + pulse * 0.75) * (1.0 + u_ripple * 2.0);
                float alpha = radialFade * brightness;

                FragColor = vec4(color * brightness, alpha);
            }
            """;

    private static final int SEGMENTS = 128;
    private static final float HALF_WIDTH = 0.015f;

    private record RingBuffers(int vaoId, int vboId, int eboId, int indexCount) {
    }

    private final Shader shader;
    private final RingBuffers[] rings;

    OrbitRingRenderer(float[] radii) {
        shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);
        rings = new RingBuffers[radii.length];

        for (int i = 0; i < radii.length; i++) {
            OrbitRingMesh.MeshData mesh = OrbitRingMesh.generate(radii[i], HALF_WIDTH, SEGMENTS);

            int vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            int vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);

            int eboId = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices(), GL_STATIC_DRAW);

            int stride = 8 * Float.BYTES;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
            glEnableVertexAttribArray(2);

            glBindVertexArray(0);
            rings[i] = new RingBuffers(vaoId, vboId, eboId, mesh.indices().length);
        }
    }

    void beginBatch(OverlayCamera camera, double elapsedSeconds) {
        shader.use();
        shader.setMat4("u_view", camera.viewMatrix());
        shader.setMat4("u_projection", camera.projectionMatrix());
        shader.setFloat("u_time", (float) elapsedSeconds);
    }

    /** @param ringIndex same index as the radius passed into the
     *                   constructor's {@code radii} array
     * @param ripple     0..1 -- a brief post-click brightness/color boost
     *                   (see {@link CelestialSystem}'s per-planet click
     *                   decay), 0 the rest of the time */
    void draw(int ringIndex, float ripple) {
        shader.setFloat("u_ripple", ripple);
        RingBuffers ring = rings[ringIndex];
        glBindVertexArray(ring.vaoId());
        glDrawElements(GL_TRIANGLES, ring.indexCount(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    void destroy() {
        for (RingBuffers ring : rings) {
            glDeleteBuffers(ring.vboId());
            glDeleteBuffers(ring.eboId());
            glDeleteVertexArrays(ring.vaoId());
        }
        shader.destroy();
    }
}
