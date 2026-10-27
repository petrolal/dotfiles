package dev.petrolal.dotfiles.overlay

import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * The "Daemon Star" at the center of the Astroland system: a procedurally
 * noise-turbulent plasma sphere (fBm over 4 octaves, no texture) with a
 * Fresnel corona, colored from void-black/occult-violet (idle) to
 * crimson/amber (loaded) by live CPU telemetry. Replaces the earlier
 * biopunk `HeartRenderer` (same role: the one telemetry-reactive
 * centerpiece) -- see docs/biopunk_plan.md for that lineage.
 */
class StarRenderer {
    private val shader: Shader
    private val vaoId: Int
    private val vboId: Int
    private val eboId: Int
    private val indexCount: Int
    private val model = Matrix4f()

    init {
        shader = Shader(VERTEX_SOURCE, FRAGMENT_SOURCE)

        val mesh = SphereMesh.generate(1f, LATITUDE_BANDS, LONGITUDE_BANDS)
        indexCount = mesh.indices.size

        vaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(vaoId)

        vboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.vertices, GL15.GL_STATIC_DRAW)

        eboId = GL15.glGenBuffers()
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
    }

    fun draw(camera: OverlayCamera, radius: Float, elapsedSeconds: Double, cpuLoad: Float) {
        model.identity().scale(radius, radius, radius)
        val eye = camera.eyePosition()

        shader.use()
        shader.setMat4("u_model", model)
        shader.setMat4("u_view", camera.viewMatrix())
        shader.setMat4("u_projection", camera.projectionMatrix())
        shader.setFloat("u_time", elapsedSeconds.toFloat())
        shader.setFloat("u_cpu_load", cpuLoad.coerceIn(0f, 1f))
        shader.setVec3("u_cam_pos", eye.x, eye.y, eye.z)

        GL30.glBindVertexArray(vaoId)
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0)
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
            """.trimIndent()

        private val FRAGMENT_SOURCE = """
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
            """.trimIndent()

        private const val LATITUDE_BANDS = 32
        private const val LONGITUDE_BANDS = 32
    }
}
