package dev.petrolal.dotfiles.overlay

import dev.petrolal.dotfiles.overlay.PolyhedronMesh.FacetData
import dev.petrolal.dotfiles.overlay.PolyhedronMesh.WireframeData
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import java.util.EnumMap

/**
 * Shared renderer for every [PlanetNode] -- one facet shader + one
 * wireframe shader, each with one VAO per [PolyhedronMesh.Shape]
 * (there are only three, so caching per-shape rather than per-node is
 * enough; several nodes of the same shape reuse the same VAO, same "shared
 * mesh, per-draw uniforms" pattern [OrbitRingRenderer] uses per
 * radius). Each draw is two passes: a translucent obsidian/amber facet body,
 * then glowing wireframe edges on top. Each planet's position is fixed (see
 * [PlanetNode.position]); the only per-frame motion is a cosmetic
 * self-rotation spin, which never moves the click target.
 */
class PlanetRenderer {
    @JvmRecord
    private data class MeshBuffers(val vaoId: Int, val vboId: Int, val eboId: Int, val indexCount: Int)

    private val facetShader: Shader = Shader(FACET_VERTEX_SOURCE, FACET_FRAGMENT_SOURCE)
    private val wireframeShader: Shader = Shader(WIREFRAME_VERTEX_SOURCE, WIREFRAME_FRAGMENT_SOURCE)
    private val facetMeshes: MutableMap<PolyhedronMesh.Shape, MeshBuffers> =
        EnumMap(PolyhedronMesh.Shape::class.java)
    private val wireframeMeshes: MutableMap<PolyhedronMesh.Shape, MeshBuffers> =
        EnumMap(PolyhedronMesh.Shape::class.java)
    private val model = Matrix4f()

    init {
        for (shape in PolyhedronMesh.Shape.entries) {
            facetMeshes[shape] = uploadFacets(PolyhedronMesh.generateFacets(shape))
            wireframeMeshes[shape] = uploadWireframe(PolyhedronMesh.generateWireframe(shape))
        }
    }

    fun beginBatch(camera: OverlayCamera) {
        val eye = camera.eyePosition()
        facetShader.use()
        facetShader.setMat4("u_view", camera.viewMatrix())
        facetShader.setMat4("u_projection", camera.projectionMatrix())
        facetShader.setVec3("u_cam_pos", eye.x, eye.y, eye.z)

        wireframeShader.use()
        wireframeShader.setMat4("u_view", camera.viewMatrix())
        wireframeShader.setMat4("u_projection", camera.projectionMatrix())
    }

    fun draw(planet: PlanetNode, position: Vector3f, elapsedSeconds: Double, hovered: Boolean) {
        val spinAngle = (planet.angularVelocityRadiansPerSecond * elapsedSeconds).toFloat()
        model.identity()
            .translate(position.x, position.y, position.z)
            .rotateY(spinAngle)
            .scale(planet.sphereRadius, planet.sphereRadius, planet.sphereRadius)

        val color = planet.baseColor
        val glow = planet.emissiveGlow
        val hoverAmount = if (hovered) 1f else 0f

        val facet = facetMeshes.getValue(planet.shape)
        facetShader.use()
        facetShader.setMat4("u_model", model)
        facetShader.setVec3("u_base_color", color.x, color.y, color.z)
        facetShader.setVec3("u_emissive_glow", glow.x, glow.y, glow.z)
        facetShader.setFloat("u_hovered", hoverAmount)
        facetShader.setFloat("u_time", elapsedSeconds.toFloat())
        GL30.glBindVertexArray(facet.vaoId)
        GL11.glDrawElements(GL11.GL_TRIANGLES, facet.indexCount, GL11.GL_UNSIGNED_INT, 0)

        val wireframe = wireframeMeshes.getValue(planet.shape)
        wireframeShader.use()
        wireframeShader.setMat4("u_model", model)
        wireframeShader.setVec3("u_color", glow.x, glow.y, glow.z)
        wireframeShader.setFloat("u_intensity", 0.6f + hoverAmount * 0.8f)
        wireframeShader.setFloat("u_time", elapsedSeconds.toFloat())
        GL11.glLineWidth(if (hovered) 2.5f else 1.5f)
        GL30.glBindVertexArray(wireframe.vaoId)
        GL11.glDrawElements(GL11.GL_LINES, wireframe.indexCount, GL11.GL_UNSIGNED_INT, 0)

        GL30.glBindVertexArray(0)
    }

    fun endBatch() {
        GL11.glLineWidth(1f)
        GL30.glBindVertexArray(0)
    }

    fun destroy() {
        for (mesh in facetMeshes.values) {
            GL15.glDeleteBuffers(mesh.vboId)
            GL15.glDeleteBuffers(mesh.eboId)
            GL30.glDeleteVertexArrays(mesh.vaoId)
        }
        for (mesh in wireframeMeshes.values) {
            GL15.glDeleteBuffers(mesh.vboId)
            GL15.glDeleteBuffers(mesh.eboId)
            GL30.glDeleteVertexArrays(mesh.vaoId)
        }
        facetShader.destroy()
        wireframeShader.destroy()
    }

    companion object {
        private val FACET_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;

            out vec3 vFragPos;
            out vec3 vNormal;

            uniform mat4 u_model;
            uniform mat4 u_view;
            uniform mat4 u_projection;

            void main() {
                vFragPos = vec3(u_model * vec4(aPos, 1.0));
                vNormal = mat3(transpose(inverse(u_model))) * aNormal;
                gl_Position = u_projection * u_view * vec4(vFragPos, 1.0);
            }
            """.trimIndent()

        /** Deep obsidian/charcoal interior, sharp glowing amber-tinted-by-the
         * node's-own-color Fresnel rim, translucent (alpha rises toward the
         * silhouette edge so the body still reads as faceted glass, not a flat
         * cutout).  */
        private val FACET_FRAGMENT_SOURCE = """
            #version 330 core
            in vec3 vFragPos;
            in vec3 vNormal;
            out vec4 FragColor;

            uniform vec3 u_cam_pos;
            uniform vec3 u_base_color;
            uniform vec3 u_emissive_glow;
            uniform float u_hovered;
            uniform float u_time;

            void main() {
                vec3 norm = normalize(vNormal);
                vec3 viewDir = normalize(u_cam_pos - vFragPos);

                vec3 obsidian = vec3(0.05, 0.045, 0.07);
                vec3 interior = mix(obsidian, u_base_color * 0.35, 0.4);

                float rim = pow(1.0 - max(dot(norm, viewDir), 0.0), 3.0);
                float hoverBoost = 1.0 + u_hovered * 1.5;
                vec3 rimGlow = u_emissive_glow * rim * hoverBoost
                        * (0.85 + 0.15 * sin(u_time * 4.0));

                float alpha = 0.35 + rim * 0.45 + u_hovered * 0.15;
                FragColor = vec4(interior + rimGlow, clamp(alpha, 0.0, 1.0));
            }
            """.trimIndent()

        private val WIREFRAME_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPos;

            uniform mat4 u_model;
            uniform mat4 u_view;
            uniform mat4 u_projection;

            void main() {
                gl_Position = u_projection * u_view * u_model * vec4(aPos, 1.0);
            }
            """.trimIndent()

        private val WIREFRAME_FRAGMENT_SOURCE = """
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

        private fun uploadFacets(mesh: FacetData): MeshBuffers {
            val vaoId = GL30.glGenVertexArrays()
            GL30.glBindVertexArray(vaoId)

            val vboId = GL15.glGenBuffers()
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.vertices, GL15.GL_STATIC_DRAW)

            val eboId = GL15.glGenBuffers()
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId)
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.indices, GL15.GL_STATIC_DRAW)

            val stride = 6 * java.lang.Float.BYTES
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0)
            GL20.glEnableVertexAttribArray(0)
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3L * java.lang.Float.BYTES)
            GL20.glEnableVertexAttribArray(1)

            GL30.glBindVertexArray(0)
            return MeshBuffers(vaoId, vboId, eboId, mesh.indices.size)
        }

        private fun uploadWireframe(mesh: WireframeData): MeshBuffers {
            val vaoId = GL30.glGenVertexArrays()
            GL30.glBindVertexArray(vaoId)

            val vboId = GL15.glGenBuffers()
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.vertices, GL15.GL_STATIC_DRAW)

            val eboId = GL15.glGenBuffers()
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId)
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, mesh.indices, GL15.GL_STATIC_DRAW)

            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0)
            GL20.glEnableVertexAttribArray(0)

            GL30.glBindVertexArray(0)
            return MeshBuffers(vaoId, vboId, eboId, mesh.indices.size)
        }
    }
}
