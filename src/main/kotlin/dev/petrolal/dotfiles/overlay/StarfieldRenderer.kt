package dev.petrolal.dotfiles.overlay

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Full-screen background pass: a procedural (hash-noise, no texture)
 * starfield with sparse twinkling points over a faint nebula-dust base.
 * Drawn first each frame, before the 3D scene, with depth writes disabled
 * so it never occludes anything and is never occluded -- it's a backdrop,
 * not a scene object.
 */
class StarfieldRenderer {
    private val shader: Shader
    private val vaoId: Int
    private val vboId: Int
    private val eboId: Int

    init {
        shader = Shader(VERTEX_SOURCE, FRAGMENT_SOURCE)

        vaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(vaoId)

        vboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, QUAD_VERTICES, GL15.GL_STATIC_DRAW)

        eboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId)
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, QUAD_INDICES, GL15.GL_STATIC_DRAW)

        val stride = 4 * java.lang.Float.BYTES
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0)
        GL20.glEnableVertexAttribArray(0)
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * java.lang.Float.BYTES)
        GL20.glEnableVertexAttribArray(1)
        GL30.glBindVertexArray(0)
    }

    fun draw(elapsedSeconds: Double, framebufferWidth: Int, framebufferHeight: Int) {
        shader.use()
        shader.setFloat("u_time", elapsedSeconds.toFloat())
        shader.setVec2("u_resolution", framebufferWidth.toFloat(), framebufferHeight.toFloat())

        GL30.glBindVertexArray(vaoId)
        GL11.glDrawElements(GL11.GL_TRIANGLES, QUAD_INDICES.size, GL11.GL_UNSIGNED_INT, 0)
        GL30.glBindVertexArray(0)
    }

    fun destroy() {
        GL15.glDeleteBuffers(vboId)
        GL15.glDeleteBuffers(eboId)
        GL30.glDeleteVertexArrays(vaoId)
        shader.destroy()
    }

    companion object {
        private val VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUV;
            out vec2 vUV;

            void main() {
                vUV = aUV;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """.trimIndent()

        private val FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;

            uniform float u_time;
            uniform vec2 u_resolution;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            void main() {
                vec2 uv = vUV * (u_resolution / min(u_resolution.x, u_resolution.y));
                vec3 color = vec3(0.012, 0.0, 0.03);

                float dust = hash21(floor(uv * 6.0));
                color += vec3(0.08, 0.02, 0.12) * dust * 0.3;

                // One candidate point per cell, not the whole cell: a star
                // falls off with distance from that point instead of
                // filling the cell as a solid square.
                vec2 cell = floor(uv * 60.0);
                vec2 cellUv = fract(uv * 60.0);
                float starChance = hash21(cell);
                if (starChance > 0.985) {
                    vec2 starPoint = vec2(hash21(cell + 0.17), hash21(cell + 0.59));
                    float distanceFromStar = length(cellUv - starPoint);
                    float twinkle = 0.5 + 0.5 * sin(u_time * 3.0 + starChance * 50.0);
                    float brightness = smoothstep(0.05, 0.0, distanceFromStar) * twinkle;
                    color += vec3(1.0) * brightness;
                }

                FragColor = vec4(color, 1.0);
            }
            """.trimIndent()

        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            1f, 1f, 1f, 1f,
            -1f, 1f, 0f, 1f,
        )
        private val QUAD_INDICES = intArrayOf(0, 1, 2, 2, 3, 0)
    }
}
