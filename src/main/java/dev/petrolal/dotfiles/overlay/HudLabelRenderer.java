package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * 2D screen-space HUD: a glowing rounded-pill border anchored next to each
 * planet, a thin guide line connecting the pill to the planet's projected
 * screen position, and the planet's name/command drawn on top via an
 * injected {@link TextRenderer} (this class owns no glyph rendering of its
 * own). Pixel-space orthographic projection, same convention as
 * {@code TextRenderer} ({@code ortho(0, w, h, 0, -1, 1)}) so the two line
 * up without a separate coordinate mapping.
 *
 * <p>Text width isn't measured exactly (no measurement API on
 * {@code TextRenderer}, see its javadoc) -- the pill is sized from a fixed
 * average-character-width estimate, which is good enough for a glow border
 * around short labels like these and not worth adding real text metrics
 * for yet.
 */
final class HudLabelRenderer {

    private static final String PILL_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec2 aLocalPos; // -0.5 .. 0.5

            uniform mat4 u_projection;
            uniform vec2 u_center;
            uniform vec2 u_size;

            out vec2 vLocalPos;

            void main() {
                vLocalPos = aLocalPos;
                vec2 pixelPos = u_center + aLocalPos * u_size;
                gl_Position = u_projection * vec4(pixelPos, 0.0, 1.0);
            }
            """;

    private static final String PILL_FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vLocalPos;
            out vec4 FragColor;

            uniform vec2 u_size;
            uniform vec3 u_glow_color;
            uniform float u_intensity;

            void main() {
                vec2 p = vLocalPos * u_size;
                float radius = min(u_size.y, 18.0) * 0.5;
                vec2 halfSize = u_size * 0.5 - vec2(radius);
                vec2 d = abs(p) - halfSize;
                float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;

                float stroke = smoothstep(2.0, 0.0, abs(dist));
                FragColor = vec4(u_glow_color * stroke * u_intensity, stroke);
            }
            """;

    private static final String LINE_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            uniform mat4 u_projection;

            void main() {
                gl_Position = u_projection * vec4(aPos, 0.0, 1.0);
            }
            """;

    private static final String LINE_FRAGMENT_SOURCE = """
            #version 330 core
            out vec4 FragColor;
            uniform vec3 u_color;
            uniform float u_alpha;

            void main() {
                FragColor = vec4(u_color, u_alpha);
            }
            """;

    private static final float[] PILL_VERTICES = { -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f };
    private static final int[] PILL_INDICES = { 0, 1, 2, 2, 3, 0 };
    /** Rough average glyph advance for the font/size this overlay uses --
     * see the class javadoc on why this is an estimate, not a measurement. */
    private static final float AVERAGE_CHAR_WIDTH_PX = 10f;
    private static final float PILL_HEIGHT_PX = 26f;
    private static final float PILL_PADDING_PX = 16f;

    private final Shader pillShader;
    private final int pillVaoId;
    private final int pillVboId;
    private final int pillEboId;

    private final Shader lineShader;
    private final int lineVaoId;
    private final int lineVboId;

    private final Matrix4f projection = new Matrix4f();

    HudLabelRenderer() {
        pillShader = new Shader(PILL_VERTEX_SOURCE, PILL_FRAGMENT_SOURCE);
        pillVaoId = glGenVertexArrays();
        glBindVertexArray(pillVaoId);
        pillVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, pillVboId);
        glBufferData(GL_ARRAY_BUFFER, PILL_VERTICES, GL_STATIC_DRAW);
        pillEboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, pillEboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, PILL_INDICES, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        lineShader = new Shader(LINE_VERTEX_SOURCE, LINE_FRAGMENT_SOURCE);
        lineVaoId = glGenVertexArrays();
        glBindVertexArray(lineVaoId);
        lineVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, lineVboId);
        glBufferData(GL_ARRAY_BUFFER, 4L * Float.BYTES, GL_DYNAMIC_DRAW); // 2 points x (x,y)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    void resize(int framebufferWidth, int framebufferHeight) {
        projection.identity().ortho(0, framebufferWidth, framebufferHeight, 0, -1f, 1f);
    }

    /** Draws the connecting guide line from the planet's projected screen
     * position to the label anchor. */
    void drawConnectingLine(float fromX, float fromY, float toX, float toY, float r, float g, float b, float alpha) {
        float[] points = { fromX, fromY, toX, toY };
        glBindBuffer(GL_ARRAY_BUFFER, lineVboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, points);

        lineShader.use();
        lineShader.setMat4("u_projection", projection);
        lineShader.setVec3("u_color", r, g, b);
        lineShader.setFloat("u_alpha", alpha);

        glBindVertexArray(lineVaoId);
        glDrawArrays(GL_LINES, 0, 2);
        glBindVertexArray(0);
    }

    /** Draws the pill-shaped glow border centered at (centerX, centerY),
     * sized to fit {@code text}, brighter/wider when {@code hovered}. */
    void drawPillBorder(float centerX, float centerY, String text, float r, float g, float b, boolean hovered) {
        float width = text.length() * AVERAGE_CHAR_WIDTH_PX + PILL_PADDING_PX * 2f;
        float height = PILL_HEIGHT_PX;
        if (hovered) {
            width *= 1.15f;
            height *= 1.1f;
        }

        pillShader.use();
        pillShader.setMat4("u_projection", projection);
        pillShader.setVec2("u_center", centerX, centerY);
        pillShader.setVec2("u_size", width, height);
        pillShader.setVec3("u_glow_color", r, g, b);
        pillShader.setFloat("u_intensity", hovered ? 1.6f : 1.0f);

        glBindVertexArray(pillVaoId);
        glDrawElements(GL_TRIANGLES, PILL_INDICES.length, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    /** Pill width for a given label, in pixels -- lets the caller center
     * text inside the pill it just asked for. */
    static float pillWidth(String text, boolean hovered) {
        float width = text.length() * AVERAGE_CHAR_WIDTH_PX + PILL_PADDING_PX * 2f;
        return hovered ? width * 1.15f : width;
    }

    void destroy() {
        glDeleteBuffers(pillVboId);
        glDeleteBuffers(pillEboId);
        glDeleteVertexArrays(pillVaoId);
        pillShader.destroy();
        glDeleteBuffers(lineVboId);
        glDeleteVertexArrays(lineVaoId);
        lineShader.destroy();
    }
}
