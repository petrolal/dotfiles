package dev.petrolal.dotfiles.overlay;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * The "Daemon Star" at the center of the Astroland system: a procedurally
 * noise-turbulent plasma sphere (fBm over 4 octaves, no texture) with a
 * Fresnel corona, colored from void-black/occult-violet (idle) to
 * crimson/amber (loaded) by live CPU telemetry. Replaces the earlier
 * biopunk {@code HeartRenderer} (same role: the one telemetry-reactive
 * centerpiece) -- see docs/biopunk_plan.md for that lineage.
 */
final class StarRenderer {

    private static final String VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aTexCoords;

            out vec3 vFragPos;
            out vec3 vNormal;

            uniform mat4 u_model;
            uniform mat4 u_view;
            uniform mat4 u_projection;
            uniform float u_time;
            uniform float u_cpu_load;

            void main() {
                float expansion = sin(u_time * (1.5 + u_cpu_load * 4.0)) * 0.04 * (1.0 + u_cpu_load);
                vec3 displacedPos = aPos + aNormal * expansion;

                vFragPos = vec3(u_model * vec4(displacedPos, 1.0));
                vNormal = mat3(transpose(inverse(u_model))) * aNormal;
                gl_Position = u_projection * u_view * vec4(vFragPos, 1.0);
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 330 core
            in vec3 vFragPos;
            in vec3 vNormal;
            out vec4 FragColor;

            uniform vec3 u_cam_pos;
            uniform float u_time;
            uniform float u_cpu_load;

            float hash(vec3 p) {
                p = fract(p * 0.3183099 + 0.1);
                p *= 17.0;
                return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
            }

            float valueNoise(vec3 p) {
                vec3 i = floor(p);
                vec3 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(
                    mix(mix(hash(i + vec3(0,0,0)), hash(i + vec3(1,0,0)), f.x),
                        mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
                    mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                        mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y),
                    f.z);
            }

            float fbm(vec3 p) {
                float value = 0.0;
                float amplitude = 0.5;
                for (int i = 0; i < 4; i++) {
                    value += amplitude * valueNoise(p);
                    p *= 2.0;
                    amplitude *= 0.5;
                }
                return value;
            }

            void main() {
                vec3 norm = normalize(vNormal);
                vec3 viewDir = normalize(u_cam_pos - vFragPos);

                float turbulence = fbm(vNormal * 3.0 + vec3(0.0, 0.0, u_time * 0.4));

                vec3 voidBlack = vec3(0.02, 0.0, 0.05);
                vec3 occultViolet = vec3(0.35, 0.05, 0.55);
                vec3 crimsonAmber = mix(vec3(0.9, 0.15, 0.1), vec3(1.0, 0.65, 0.1), 0.5);

                vec3 plasma = mix(voidBlack, occultViolet, turbulence);
                plasma = mix(plasma, crimsonAmber, u_cpu_load * (0.5 + 0.5 * turbulence));

                // A 2.2 exponent with an uncapped x2.0 multiplier let the
                // corona saturate most of the visible disc instead of just
                // the rim -- a sphere's true silhouette (as seen from a
                // finite camera distance) sits much closer to the
                // facing/center direction than "90 degrees" intuition
                // suggests, so fresnel ramps up well before the edge. A
                // sharper exponent plus clamping keeps the plasma's own
                // turbulence visible instead of being washed out.
                float fresnel = pow(1.0 - max(dot(norm, viewDir), 0.0), 4.0);
                vec3 corona = mix(occultViolet, crimsonAmber, u_cpu_load) * fresnel * 1.1;

                FragColor = vec4(clamp(plasma + corona, 0.0, 1.0), 1.0);
            }
            """;

    private static final int LATITUDE_BANDS = 32;
    private static final int LONGITUDE_BANDS = 32;

    private final Shader shader;
    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final int indexCount;
    private final Matrix4f model = new Matrix4f();

    StarRenderer() {
        shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);

        SphereMesh.MeshData mesh = SphereMesh.generate(1f, LATITUDE_BANDS, LONGITUDE_BANDS);
        indexCount = mesh.indices().length;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);

        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices(), GL_STATIC_DRAW);

        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }

    void draw(OverlayCamera camera, float radius, double elapsedSeconds, float cpuLoad) {
        model.identity().scale(radius, radius, radius);
        Vector3f eye = camera.eyePosition();

        shader.use();
        shader.setMat4("u_model", model);
        shader.setMat4("u_view", camera.viewMatrix());
        shader.setMat4("u_projection", camera.projectionMatrix());
        shader.setFloat("u_time", (float) elapsedSeconds);
        shader.setFloat("u_cpu_load", Math.clamp(cpuLoad, 0f, 1f));
        shader.setVec3("u_cam_pos", eye.x, eye.y, eye.z);

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    void destroy() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        glDeleteVertexArrays(vaoId);
        shader.destroy();
    }
}
