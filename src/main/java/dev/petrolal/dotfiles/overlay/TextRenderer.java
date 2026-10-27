package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad;

/**
 * Draws text against a {@link FontAtlas} in screen-space (orthographic
 * projection over framebuffer pixels), not world-space. Used to label the
 * syringe rack's slots (see {@link SyringeRack}), projected from each
 * slot's live world position via {@link OverlayCamera#worldToScreen}.
 */
final class TextRenderer {

    private static final int MAX_CHARS_PER_DRAW = 256;
    private static final int FLOATS_PER_VERTEX = 4; // x, y, u, v
    private static final int VERTICES_PER_CHAR = 6;

    private static final String VERTEX_SOURCE = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aUV;

            uniform mat4 uProjection;

            out vec2 vUV;

            void main() {
                gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
                vUV = aUV;
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vUV;
            out vec4 fragColor;

            uniform sampler2D uAtlas;
            uniform vec4 uColor;

            void main() {
                float alpha = texture(uAtlas, vUV).r;
                fragColor = vec4(uColor.rgb, uColor.a * alpha);
            }
            """;

    private final FontAtlas atlas;
    private final Shader shader;
    private final int vaoId;
    private final int vboId;
    private final Matrix4f projection = new Matrix4f();
    private final FloatBuffer vertexBuffer =
            BufferUtils.createFloatBuffer(MAX_CHARS_PER_DRAW * VERTICES_PER_CHAR * FLOATS_PER_VERTEX);

    TextRenderer(FontAtlas atlas) {
        this.atlas = atlas;
        shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, (long) vertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        shader.use();
        shader.setInt("uAtlas", 0); // texture unit 0, bound in draw()
    }

    void resize(int framebufferWidth, int framebufferHeight) {
        projection.identity().ortho(0, framebufferWidth, framebufferHeight, 0, -1f, 1f);
    }

    /** Draws left-aligned text with its baseline at (x, y), in framebuffer pixels. */
    void draw(String text, float x, float y, float r, float g, float b, float a) {
        vertexBuffer.clear();
        int vertexCount = 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xpos = stack.floats(x);
            FloatBuffer ypos = stack.floats(y);
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);

            int charsWritten = 0;
            for (int i = 0; i < text.length() && charsWritten < MAX_CHARS_PER_DRAW; i++) {
                int c = text.charAt(i);
                if (c < atlas.firstChar() || c >= atlas.firstChar() + atlas.charCount()) {
                    continue;
                }
                stbtt_GetBakedQuad(atlas.bakedChars(), atlas.bitmapWidth(), atlas.bitmapHeight(),
                        c - atlas.firstChar(), xpos, ypos, quad, true);

                putVertex(quad.x0(), quad.y0(), quad.s0(), quad.t0());
                putVertex(quad.x1(), quad.y0(), quad.s1(), quad.t0());
                putVertex(quad.x1(), quad.y1(), quad.s1(), quad.t1());

                putVertex(quad.x0(), quad.y0(), quad.s0(), quad.t0());
                putVertex(quad.x1(), quad.y1(), quad.s1(), quad.t1());
                putVertex(quad.x0(), quad.y1(), quad.s0(), quad.t1());

                charsWritten++;
                vertexCount += VERTICES_PER_CHAR;
            }
        }

        if (vertexCount == 0) {
            return;
        }
        vertexBuffer.flip();

        shader.use();
        shader.setMat4("uProjection", projection);
        shader.setVec4("uColor", r, g, b, a);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlas.textureId());

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    private void putVertex(float x, float y, float u, float v) {
        vertexBuffer.put(x).put(y).put(u).put(v);
    }

    void destroy() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        shader.destroy();
    }
}
