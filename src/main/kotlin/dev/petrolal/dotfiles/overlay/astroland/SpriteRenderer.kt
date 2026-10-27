package dev.petrolal.dotfiles.overlay.astroland

import dev.petrolal.dotfiles.overlay.Shader
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Draws one textured, rotatable quad per call in screen-pixel space
 * (orthographic, origin top-left) -- the standalone-overlay equivalent of
 * the original's `CTexPassElement` render-pass draws, which went through
 * Hyprland's own compositor renderer instead.
 */
class SpriteRenderer {
    private val shader = Shader(VERTEX_SOURCE, FRAGMENT_SOURCE)
    private val vao: Int
    private val vbo: Int
    private val projection = Matrix4f()

    init {
        vao = GL30.glGenVertexArrays()
        vbo = GL15.glGenBuffers()
        GL30.glBindVertexArray(vao)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
        // unit quad centered at origin: pos.xy, uv.xy
        val quad = floatArrayOf(
            -0.5f, -0.5f, 0f, 1f,
            0.5f, -0.5f, 1f, 1f,
            0.5f, 0.5f, 1f, 0f,
            -0.5f, -0.5f, 0f, 1f,
            0.5f, 0.5f, 1f, 0f,
            -0.5f, 0.5f, 0f, 0f,
        )
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quad, GL15.GL_STATIC_DRAW)
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * 4, 0)
        GL20.glEnableVertexAttribArray(0)
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * 4, (2 * 4).toLong())
        GL20.glEnableVertexAttribArray(1)
        GL30.glBindVertexArray(0)
    }

    fun resize(width: Int, height: Int) {
        projection.identity().ortho(0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
    }

    /** [centerX]/[centerY] and [width]/[height] in screen pixels; [rotRadians] counter-clockwise. */
    fun draw(texture: Texture, centerX: Float, centerY: Float, width: Float, height: Float, rotRadians: Float, alpha: Float = 1f) {
        shader.use()
        val model = Matrix4f()
            .translate(centerX, centerY, 0f)
            .rotateZ(-rotRadians)
            .scale(width, height, 1f)
        shader.setMat4("uProjection", projection)
        shader.setMat4("uModel", model)
        shader.setFloat("uAlpha", alpha)
        shader.setInt("uTex", 0)

        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.id)

        GL30.glBindVertexArray(vao)
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6)
        GL30.glBindVertexArray(0)
    }

    fun destroy() {
        GL15.glDeleteBuffers(vbo)
        GL30.glDeleteVertexArrays(vao)
        shader.destroy()
    }

    companion object {
        private const val VERTEX_SOURCE = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aUv;
            uniform mat4 uProjection;
            uniform mat4 uModel;
            out vec2 vUv;
            void main() {
                gl_Position = uProjection * uModel * vec4(aPos, 0.0, 1.0);
                vUv = aUv;
            }
        """

        private const val FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uTex;
            uniform float uAlpha;
            void main() {
                vec4 texel = texture(uTex, vUv);
                fragColor = vec4(texel.rgb, texel.a * uAlpha);
            }
        """
    }
}
