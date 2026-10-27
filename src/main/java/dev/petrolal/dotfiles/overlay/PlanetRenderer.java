package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Shared renderer for every {@link PlanetNode} -- one facet shader + one
 * wireframe shader, each with one VAO per {@link PolyhedronMesh.Shape}
 * (there are only three, so caching per-shape rather than per-node is
 * enough; several nodes of the same shape reuse the same VAO, same "shared
 * mesh, per-draw uniforms" pattern {@link OrbitRingRenderer} uses per
 * radius). Each draw is two passes: a translucent obsidian/amber facet body,
 * then glowing wireframe edges on top. Each planet's position is fixed (see
 * {@link PlanetNode#position()}); the only per-frame motion is a cosmetic
 * self-rotation spin, which never moves the click target.
 */
final class PlanetRenderer {

    private static final String FACET_VERTEX_SOURCE = """
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
            """;

    /** Deep obsidian/charcoal interior, sharp glowing amber-tinted-by-the
     * node's-own-color Fresnel rim, translucent (alpha rises toward the
     * silhouette edge so the body still reads as faceted glass, not a flat
     * cutout). */
    private static final String FACET_FRAGMENT_SOURCE = """
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
            """;

    private static final String WIREFRAME_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPos;

            uniform mat4 u_model;
            uniform mat4 u_view;
            uniform mat4 u_projection;

            void main() {
                gl_Position = u_projection * u_view * u_model * vec4(aPos, 1.0);
            }
            """;

    private static final String WIREFRAME_FRAGMENT_SOURCE = """
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

    private record MeshBuffers(int vaoId, int vboId, int eboId, int indexCount) {
    }

    private final Shader facetShader;
    private final Shader wireframeShader;
    private final Map<PolyhedronMesh.Shape, MeshBuffers> facetMeshes = new EnumMap<>(PolyhedronMesh.Shape.class);
    private final Map<PolyhedronMesh.Shape, MeshBuffers> wireframeMeshes = new EnumMap<>(PolyhedronMesh.Shape.class);
    private final Matrix4f model = new Matrix4f();

    PlanetRenderer() {
        facetShader = new Shader(FACET_VERTEX_SOURCE, FACET_FRAGMENT_SOURCE);
        wireframeShader = new Shader(WIREFRAME_VERTEX_SOURCE, WIREFRAME_FRAGMENT_SOURCE);

        for (PolyhedronMesh.Shape shape : PolyhedronMesh.Shape.values()) {
            facetMeshes.put(shape, uploadFacets(PolyhedronMesh.generateFacets(shape)));
            wireframeMeshes.put(shape, uploadWireframe(PolyhedronMesh.generateWireframe(shape)));
        }
    }

    private static MeshBuffers uploadFacets(PolyhedronMesh.FacetData mesh) {
        int vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        int vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);

        int eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices(), GL_STATIC_DRAW);

        int stride = 6 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
        return new MeshBuffers(vaoId, vboId, eboId, mesh.indices().length);
    }

    private static MeshBuffers uploadWireframe(PolyhedronMesh.WireframeData mesh) {
        int vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        int vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);

        int eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices(), GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
        return new MeshBuffers(vaoId, vboId, eboId, mesh.indices().length);
    }

    void beginBatch(OverlayCamera camera) {
        Vector3f eye = camera.eyePosition();
        facetShader.use();
        facetShader.setMat4("u_view", camera.viewMatrix());
        facetShader.setMat4("u_projection", camera.projectionMatrix());
        facetShader.setVec3("u_cam_pos", eye.x, eye.y, eye.z);

        wireframeShader.use();
        wireframeShader.setMat4("u_view", camera.viewMatrix());
        wireframeShader.setMat4("u_projection", camera.projectionMatrix());
    }

    void draw(PlanetNode planet, Vector3f position, double elapsedSeconds, boolean hovered) {
        float spinAngle = (float) (planet.angularVelocityRadiansPerSecond() * elapsedSeconds);
        model.identity()
                .translate(position.x, position.y, position.z)
                .rotateY(spinAngle)
                .scale(planet.sphereRadius(), planet.sphereRadius(), planet.sphereRadius());

        Vector3f color = planet.baseColor();
        Vector3f glow = planet.emissiveGlow();
        float hoverAmount = hovered ? 1f : 0f;

        MeshBuffers facet = facetMeshes.get(planet.shape());
        facetShader.use();
        facetShader.setMat4("u_model", model);
        facetShader.setVec3("u_base_color", color.x, color.y, color.z);
        facetShader.setVec3("u_emissive_glow", glow.x, glow.y, glow.z);
        facetShader.setFloat("u_hovered", hoverAmount);
        facetShader.setFloat("u_time", (float) elapsedSeconds);
        glBindVertexArray(facet.vaoId());
        glDrawElements(GL_TRIANGLES, facet.indexCount(), GL_UNSIGNED_INT, 0);

        MeshBuffers wireframe = wireframeMeshes.get(planet.shape());
        wireframeShader.use();
        wireframeShader.setMat4("u_model", model);
        wireframeShader.setVec3("u_color", glow.x, glow.y, glow.z);
        wireframeShader.setFloat("u_intensity", 0.6f + hoverAmount * 0.8f);
        wireframeShader.setFloat("u_time", (float) elapsedSeconds);
        glLineWidth(hovered ? 2.5f : 1.5f);
        glBindVertexArray(wireframe.vaoId());
        glDrawElements(GL_LINES, wireframe.indexCount(), GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);
    }

    void endBatch() {
        glLineWidth(1f);
        glBindVertexArray(0);
    }

    void destroy() {
        for (MeshBuffers mesh : facetMeshes.values()) {
            glDeleteBuffers(mesh.vboId());
            glDeleteBuffers(mesh.eboId());
            glDeleteVertexArrays(mesh.vaoId());
        }
        for (MeshBuffers mesh : wireframeMeshes.values()) {
            glDeleteBuffers(mesh.vboId());
            glDeleteBuffers(mesh.eboId());
            glDeleteVertexArrays(mesh.vaoId());
        }
        facetShader.destroy();
        wireframeShader.destroy();
    }
}
