package dev.petrolal.dotfiles.overlay

import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * 2D screen-space HUD: a glowing rounded-pill border anchored next to each
 * planet, a thin guide line connecting the pill to the planet's projected
 * screen position, and the planet's name/command drawn on top via an
 * injected [TextRenderer] (this class owns no glyph rendering of its
 * own). Pixel-space orthographic projection, same convention as
 * `TextRenderer` (`ortho(0, w, h, 0, -1, 1)`) so the two line
 * up without a separate coordinate mapping.
 * 
 * Text width isn't measured exactly (no measurement API on
 * `TextRenderer`, see its javadoc) -- the pill is sized from a fixed
 * average-character-width estimate, which is good enough for a glow border
 * around short labels like these and not worth adding real text metrics
 * for yet.
 */
class HudLabelRenderer {
    private val pillShader: Shader
    private val pillVaoId: Int
    private val pillVboId: Int
    private val pillEboId: Int

    private val lineShader: Shader
    private val lineVaoId: Int
    private val lineVboId: Int

    private val projection = Matrix4f()

    init {
        pillShader = Shader(PILL_VERTEX_SOURCE, PILL_FRAGMENT_SOURCE)
        pillVaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(pillVaoId)
        pillVboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pillVboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, PILL_VERTICES, GL15.GL_STATIC_DRAW)
        pillEboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, pillEboId)
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, PILL_INDICES, GL15.GL_STATIC_DRAW)
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * java.lang.Float.BYTES, 0)
        GL20.glEnableVertexAttribArray(0)
        GL30.glBindVertexArray(0)

        lineShader = Shader(LINE_VERTEX_SOURCE, LINE_FRAGMENT_SOURCE)
        lineVaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(lineVaoId)
        lineVboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4L * java.lang.Float.BYTES, GL15.GL_DYNAMIC_DRAW) // 2 points x (x,y)
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * java.lang.Float.BYTES, 0)
        GL20.glEnableVertexAttribArray(0)
        GL30.glBindVertexArray(0)
    }

    fun resize(framebufferWidth: Int, framebufferHeight: Int) {
        projection.identity().ortho(0f, framebufferWidth.toFloat(), framebufferHeight.toFloat(), 0f, -1f, 1f)
    }

    /** Draws the connecting guide line from the planet's projected screen
     * position to the label anchor.  */
    fun drawConnectingLine(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        r: Float,
        g: Float,
        b: Float,
        alpha: Float
    ) {
        val points = floatArrayOf(fromX, fromY, toX, toY)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVboId)
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, points)

        lineShader.use()
        lineShader.setMat4("u_projection", projection)
        lineShader.setVec3("u_color", r, g, b)
        lineShader.setFloat("u_alpha", alpha)

        GL30.glBindVertexArray(lineVaoId)
        GL11.glDrawArrays(GL11.GL_LINES, 0, 2)
        GL30.glBindVertexArray(0)
    }

    /** Draws the pill-shaped glow border centered at (centerX, centerY),
     * sized to fit `text`, brighter/wider when `hovered`.  */
    fun drawPillBorder(
        centerX: Float,
        centerY: Float,
        text: String,
        r: Float,
        g: Float,
        b: Float,
        hovered: Boolean
    ) {
        var width: Float = text.length * AVERAGE_CHAR_WIDTH_PX + PILL_PADDING_PX * 2f
        var height: Float = PILL_HEIGHT_PX
        if (hovered) {
            width *= 1.15f
            height *= 1.1f
        }

        pillShader.use()
        pillShader.setMat4("u_projection", projection)
        pillShader.setVec2("u_center", centerX, centerY)
        pillShader.setVec2("u_size", width, height)
        pillShader.setVec3("u_glow_color", r, g, b)
        pillShader.setFloat("u_intensity", if (hovered) 1.6f else 1.0f)

        GL30.glBindVertexArray(pillVaoId)
        GL11.glDrawElements(GL11.GL_TRIANGLES, PILL_INDICES.size, GL11.GL_UNSIGNED_INT, 0)
        GL30.glBindVertexArray(0)
    }

    fun destroy() {
        GL15.glDeleteBuffers(pillVboId)
        GL15.glDeleteBuffers(pillEboId)
        GL30.glDeleteVertexArrays(pillVaoId)
        pillShader.destroy()
        GL15.glDeleteBuffers(lineVboId)
        GL30.glDeleteVertexArrays(lineVaoId)
        lineShader.destroy()
    }

    companion object {
        private val PILL_VERTEX_SOURCE = """
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
            """.trimIndent()

        private val PILL_FRAGMENT_SOURCE = """
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
            """.trimIndent()

        private val LINE_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            uniform mat4 u_projection;

            void main() {
                gl_Position = u_projection * vec4(aPos, 0.0, 1.0);
            }
            """.trimIndent()

        private val LINE_FRAGMENT_SOURCE = """
            #version 330 core
            out vec4 FragColor;
            uniform vec3 u_color;
            uniform float u_alpha;

            void main() {
                FragColor = vec4(u_color, u_alpha);
            }
            """.trimIndent()

        private val PILL_VERTICES = floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f)
        private val PILL_INDICES = intArrayOf(0, 1, 2, 2, 3, 0)

        /** Rough average glyph advance for the font/size this overlay uses --
         * see the class javadoc on why this is an estimate, not a measurement.  */
        private const val AVERAGE_CHAR_WIDTH_PX = 10f
        private const val PILL_HEIGHT_PX = 26f
        private const val PILL_PADDING_PX = 16f

        /** Pill width for a given label, in pixels -- lets the caller center
         * text inside the pill it just asked for.  */
        fun pillWidth(text: String, hovered: Boolean): Float {
            val width: Float = text.length * AVERAGE_CHAR_WIDTH_PX + PILL_PADDING_PX * 2f
            return if (hovered) width * 1.15f else width
        }
    }
}
