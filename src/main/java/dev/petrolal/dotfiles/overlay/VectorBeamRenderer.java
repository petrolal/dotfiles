package dev.petrolal.dotfiles.overlay;

import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Draws one {@code GL_LINES} energy ray from the central star to each
 * {@link PlanetNode}'s fixed position -- the "this node belongs to the
 * core" cue the pure sphere-launcher didn't need but a wireframe-polyhedron
 * orrery reads as missing without. One static 2-vertex VAO per node (star
 * and planet positions are both fixed, so the geometry never changes, same
 * "build once at the real radius" reasoning as {@link OrbitRingRenderer}),
 * redrawn per-frame with brightness/pulse uniforms only.
 */
final class VectorBeamRenderer {

    private static final String VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPos;

            uniform mat4 u_view;
            uniform mat4 u_projection;

            void main() {
                gl_Position = u_projection * u_view * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 330 core
            out vec4 FragColor;

            uniform vec3 u_color;
            uniform float u_intensity;
            uniform float u_time;

            void main() {
                vec3 outColor = u_color * (u_intensity + 0.2 * sin(u_time * 4.0));
                FragColor = vec4(outColor, 1.0);
            }
            """;

    private record BeamBuffers(int vaoId, int vboId) {
    }

    private final Shader shader;
    private final BeamBuffers[] beams;

    VectorBeamRenderer(Vector3f corePosition, Vector3f[] nodePositions) {
        shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);
        beams = new BeamBuffers[nodePositions.length];

        for (int i = 0; i < nodePositions.length; i++) {
            Vector3f target = nodePositions[i];
            float[] vertices = {
                    corePosition.x, corePosition.y, corePosition.z,
                    target.x, target.y, target.z,
            };

            int vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            int vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            glBindVertexArray(0);
            beams[i] = new BeamBuffers(vaoId, vboId);
        }
    }

    void beginBatch(OverlayCamera camera, double elapsedSeconds) {
        shader.use();
        shader.setMat4("u_view", camera.viewMatrix());
        shader.setMat4("u_projection", camera.projectionMatrix());
        shader.setFloat("u_time", (float) elapsedSeconds);
    }

    /** @param beamIndex same index as the node position passed into the
     *                   constructor's {@code nodePositions} array
     * @param glowColor  the destination node's own emissive glow tint
     * @param hovered    true intensifies brightness and pulse, matching the
     *                   node's own hover state */
    void draw(int beamIndex, Vector3f glowColor, boolean hovered) {
        shader.setVec3("u_color", glowColor.x, glowColor.y, glowColor.z);
        shader.setFloat("u_intensity", hovered ? 1.1f : 0.45f);
        glLineWidth(hovered ? 2f : 1f);
        glBindVertexArray(beams[beamIndex].vaoId());
        glDrawArrays(GL_LINES, 0, 2);
        glBindVertexArray(0);
    }

    void endBatch() {
        glLineWidth(1f);
    }

    void destroy() {
        for (BeamBuffers beam : beams) {
            glDeleteBuffers(beam.vboId());
            glDeleteVertexArrays(beam.vaoId());
        }
        shader.destroy();
    }
}
