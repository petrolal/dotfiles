package dev.petrolal.dotfiles.overlay

import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Draws one `GL_LINES` energy ray from the central star to each
 * [PlanetNode]'s fixed position -- the "this node belongs to the
 * core" cue the pure sphere-launcher didn't need but a wireframe-polyhedron
 * orrery reads as missing without. One static 2-vertex VAO per node (star
 * and planet positions are both fixed, so the geometry never changes, same
 * "build once at the real radius" reasoning as [OrbitRingRenderer]),
 * redrawn per-frame with brightness/pulse uniforms only.
 */
class VectorBeamRenderer(corePosition: Vector3f, nodePositions: Array<Vector3f>) {
    @JvmRecord
    private data class BeamBuffers(val vaoId: Int, val vboId: Int)

    private val shader: Shader = Shader(VERTEX_SOURCE, FRAGMENT_SOURCE)
    private val beams: Array<BeamBuffers> = Array(nodePositions.size) { i ->
        val target = nodePositions[i]
        val vertices = floatArrayOf(
            corePosition.x, corePosition.y, corePosition.z,
            target.x, target.y, target.z,
        )

        val vaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(vaoId)

        val vboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW)

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0)
        GL20.glEnableVertexAttribArray(0)

        GL30.glBindVertexArray(0)
        BeamBuffers(vaoId, vboId)
    }

    fun beginBatch(camera: OverlayCamera, elapsedSeconds: Double) {
        shader.use()
        shader.setMat4("u_view", camera.viewMatrix())
        shader.setMat4("u_projection", camera.projectionMatrix())
        shader.setFloat("u_time", elapsedSeconds.toFloat())
    }

    /** @param beamIndex same index as the node position passed into the
     * constructor's `nodePositions` array
     * @param glowColor  the destination node's own emissive glow tint
     * @param hovered    true intensifies brightness and pulse, matching the
     * node's own hover state
     */
    fun draw(beamIndex: Int, glowColor: Vector3f, hovered: Boolean) {
        shader.setVec3("u_color", glowColor.x, glowColor.y, glowColor.z)
        shader.setFloat("u_intensity", if (hovered) 1.1f else 0.45f)
        GL11.glLineWidth(if (hovered) 2f else 1f)
        GL30.glBindVertexArray(beams[beamIndex].vaoId)
        GL11.glDrawArrays(GL11.GL_LINES, 0, 2)
        GL30.glBindVertexArray(0)
    }

    fun endBatch() {
        GL11.glLineWidth(1f)
    }

    fun destroy() {
        for (beam in beams) {
            GL15.glDeleteBuffers(beam.vboId)
            GL30.glDeleteVertexArrays(beam.vaoId)
        }
        shader.destroy()
    }

    companion object {
        private val VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPos;

            uniform mat4 u_view;
            uniform mat4 u_projection;

            void main() {
                gl_Position = u_projection * u_view * vec4(aPos, 1.0);
            }
            """.trimIndent()

        private val FRAGMENT_SOURCE = """
            #version 330 core
            out vec4 FragColor;

            uniform vec3 u_color;
            uniform float u_intensity;
            uniform float u_time;

            void main() {
                vec3 outColor = u_color * (u_intensity + 0.2 * sin(u_time * 4.0));
                FragColor = vec4(outColor, 1.0);
            }
            """.trimIndent()
    }
}
