package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Draws one app node's icon as a texture-sampled quad in world space, using
 * the same unit-quad geometry as {@link QuadRenderer} but a texture instead
 * of a flat color. Kept as its own small class rather than bolting a
 * "useTexture" branch onto {@code QuadRenderer}'s shader, since the two
 * proved separate milestones (flat quads, then real icon textures -- see
 * docs/checklist.md) and have genuinely different fragment shaders.
 */
final class IconRenderer {

    private static final String VERTEX_SOURCE = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aUV;

            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform mat4 uModel;

            out vec2 vUV;

            void main() {
                gl_Position = uProjection * uView * uModel * vec4(aPos, 1.0);
                vUV = aUV;
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vUV;
            out vec4 fragColor;

            uniform sampler2D uIcon;

            void main() {
                fragColor = texture(uIcon, vUV);
            }
            """;

    private static final float[] VERTICES = {
            // x,     y,    z,    u,    v  (V flipped vs QuadRenderer: image row 0 is the top)
            -0.5f, -0.5f, 0f,   0f, 1f,
             0.5f, -0.5f, 0f,   1f, 1f,
             0.5f,  0.5f, 0f,   1f, 0f,
            -0.5f,  0.5f, 0f,   0f, 0f,
    };

    private static final int[] INDICES = { 0, 1, 2, 2, 3, 0 };

    private final Shader shader;
    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final Matrix4f model = new Matrix4f();

    IconRenderer() {
        shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, VERTICES, GL_STATIC_DRAW);

        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, INDICES, GL_STATIC_DRAW);

        int stride = 5 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        shader.use();
        shader.setInt("uIcon", 0);
    }

    void draw(OverlayCamera camera, IconLoader.IconTexture icon, float x, float y, float z, float width, float height) {
        model.identity().translate(x, y, z).scale(width, height, 1f);

        shader.use();
        shader.setMat4("uProjection", camera.projectionMatrix());
        shader.setMat4("uView", camera.viewMatrix());
        shader.setMat4("uModel", model);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, icon.textureId());

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, INDICES.length, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    void destroy() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        glDeleteVertexArrays(vaoId);
        shader.destroy();
    }
}
