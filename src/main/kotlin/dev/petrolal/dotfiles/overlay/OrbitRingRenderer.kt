package dev.petrolal.dotfiles.overlay

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Draws one glowing bio-luminescent ribbon per orbital lane. Builds one
 * mesh/VAO per distinct `orbitalRadius` at construction time, each
 * generated directly at its real-world radius (see [OrbitRingMesh]) --
 * deliberately NOT a single shared unit-radius mesh scaled per draw call,
 * because scaling would also scale the ribbon's half-width, breaking the
 * "constant width regardless of radius" look a shared, translate-only mesh
 * gives. Only a handful of rings ever exist (one per [PlanetNode]),
 * so a few KB of extra VBO data per ring is a non-issue.
 */
class OrbitRingRenderer(radii: FloatArray) {
    @JvmRecord
    private data class RingBuffers(val vaoId: Int, val vboId: Int, val eboId: Int, val indexCount: Int)

    private val shader: Shader = Shader(VERTEX_SOURCE, FRAGMENT_SOURCE)
    private val rings: Array<RingBuffers> = Array(radii.size) { i ->
        val mesh = OrbitRingMesh.generate(radii[i], HALF_WIDTH, SEGMENTS)

        val vaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(vaoId)

        val vboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.vertices, GL15.GL_STATIC_DRAW)

        val eboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId)
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.indices, GL15.GL_STATIC_DRAW)

        val stride = 8 * java.lang.Float.BYTES
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0)
        GL20.glEnableVertexAttribArray(0)
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3L * java.lang.Float.BYTES)
        GL20.glEnableVertexAttribArray(1)
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 6L * java.lang.Float.BYTES)
        GL20.glEnableVertexAttribArray(2)

        GL30.glBindVertexArray(0)
        RingBuffers(vaoId, vboId, eboId, mesh.indices.size)
    }

    fun beginBatch(camera: OverlayCamera, elapsedSeconds: Double) {
        shader.use()
        shader.setMat4("u_view", camera.viewMatrix())
        shader.setMat4("u_projection", camera.projectionMatrix())
        shader.setFloat("u_time", elapsedSeconds.toFloat())
    }

    /** @param ringIndex same index as the radius passed into the
     * constructor's `radii` array
     * @param ripple     0..1 -- a brief post-click brightness/color boost
     * (see [CelestialSystem]'s per-planet click
     * decay), 0 the rest of the time
     */
    fun draw(ringIndex: Int, ripple: Float) {
        shader.setFloat("u_ripple", ripple)
        val ring = rings[ringIndex]
        GL30.glBindVertexArray(ring.vaoId)
        GL11.glDrawElements(GL11.GL_TRIANGLES, ring.indexCount, GL11.GL_UNSIGNED_INT, 0)
        GL30.glBindVertexArray(0)
    }

    fun destroy() {
        for (ring in rings) {
            GL15.glDeleteBuffers(ring.vboId)
            GL15.glDeleteBuffers(ring.eboId)
            GL30.glDeleteVertexArrays(ring.vaoId)
        }
        shader.destroy()
    }

    companion object {
        private val VERTEX_SOURCE = """
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
            """.trimIndent()

        private val FRAGMENT_SOURCE = """
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
            """.trimIndent()

        private const val SEGMENTS = 128
        private const val HALF_WIDTH = 0.015f
    }
}
