package dev.petrolal.dotfiles.overlay

import org.lwjgl.opengl.*
import java.nio.ByteBuffer

/**
 * Renders the 3D scene (heart, syringe rack, and anything added later) into
 * an offscreen color+depth framebuffer, then composites it to the real
 * window with full-screen GLSL effects (chromatic aberration, vignette,
 * scanlines) -- see docs/biopunk_plan.md. Call order each frame:
 * [beginSceneRender], draw the 3D scene as usual, then
 * [endSceneRenderAndComposite].
 * 
 * `GL_RGBA8`, not `GL_RGBA16F`: nothing rendered here needs
 * HDR range (no bloom/tonemap pass consumes over-1.0 values), and 8-bit
 * avoids depending on a renderable float-texture format that isn't
 * guaranteed on every driver this runs on (see NativeLibraryPaths/
 * docs/migration_plan.md's NVIDIA PRIME notes) -- there's a concrete reason
 * to need 16F before paying for it, not yet.
 */
class PostProcessPipeline(width: Int, height: Int) {
    private val compositeShader: Shader
    private val quadVaoId: Int
    private val quadVboId: Int
    private val quadEboId: Int

    private val fboId: Int
    private var colorTextureId = 0
    private var depthStencilRenderbufferId = 0
    private var width = 0
    private var height = 0

    init {
        compositeShader = Shader(QUAD_VERTEX_SOURCE, COMPOSITE_FRAGMENT_SOURCE)

        quadVaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(quadVaoId)

        quadVboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, QUAD_VERTICES, GL15.GL_STATIC_DRAW)

        quadEboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadEboId)
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, QUAD_INDICES, GL15.GL_STATIC_DRAW)

        val stride = 4 * java.lang.Float.BYTES
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0)
        GL20.glEnableVertexAttribArray(0)
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * java.lang.Float.BYTES)
        GL20.glEnableVertexAttribArray(1)
        GL30.glBindVertexArray(0)

        fboId = GL30.glGenFramebuffers()
        createAttachments(width, height)

        compositeShader.use()
        compositeShader.setInt("u_scene_texture", 0)
    }

    /** (Re)creates the color texture and depth/stencil renderbuffer at the
     * given size and attaches them to [fboId]. Called from the
     * constructor and from [resize] -- the FBO object itself is
     * never recreated, only its attachments, since a window resize doesn't
     * invalidate the FBO's identity, just the size of what's bound to it.  */
    private fun createAttachments(newWidth: Int, newHeight: Int) {
        width = maxOf(1, newWidth)
        height = maxOf(1, newHeight)

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId)

        colorTextureId = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            null as ByteBuffer?
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            colorTextureId,
            0
        )

        depthStencilRenderbufferId = GL30.glGenRenderbuffers()
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilRenderbufferId)
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height)
        GL30.glFramebufferRenderbuffer(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_STENCIL_ATTACHMENT,
            GL30.GL_RENDERBUFFER,
            depthStencilRenderbufferId
        )

        val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
        check(status == GL30.GL_FRAMEBUFFER_COMPLETE) {
            "Post-process framebuffer incomplete: 0x${Integer.toHexString(status)}"
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }

    /** Call from the GLFW framebuffer-size callback, same as
     * `camera.resize`/`textRenderer.resize` -- deletes and
     * recreates the color texture/renderbuffer at the new size so they
     * never stay stale relative to the window.  */
    fun resize(newWidth: Int, newHeight: Int) {
        GL11.glDeleteTextures(colorTextureId)
        GL30.glDeleteRenderbuffers(depthStencilRenderbufferId)
        createAttachments(newWidth, newHeight)
    }

    /** Binds the offscreen framebuffer and clears it -- call before drawing
     * the 3D scene for this frame.  */
    fun beginSceneRender() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId)
        GL11.glViewport(0, 0, width, height)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
    }

    /** Unbinds back to the default framebuffer and draws the full-screen
     * composite quad sampling what was just rendered into the offscreen
     * color texture. Restores `GL_DEPTH_TEST` before returning so the
     * next frame's [beginSceneRender] starts from the same state
     * this one did -- the composite pass itself needs it off (the quad is
     * drawn at a fixed NDC depth with nothing else in that buffer to test
     * against), but every 3D draw call in between expects it on.  */
    fun endSceneRenderAndComposite(elapsedSeconds: Double, cpuLoad: Float, windowWidth: Int, windowHeight: Int) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
        GL11.glViewport(0, 0, windowWidth, windowHeight)
        GL11.glDisable(GL11.GL_DEPTH_TEST)

        compositeShader.use()
        compositeShader.setFloat("u_time", elapsedSeconds.toFloat())
        compositeShader.setFloat("u_cpu_load", cpuLoad.coerceIn(0f, 1f))
        compositeShader.setVec2("u_resolution", windowWidth.toFloat(), windowHeight.toFloat())

        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId)

        GL30.glBindVertexArray(quadVaoId)
        GL11.glDrawElements(GL11.GL_TRIANGLES, QUAD_INDICES.size, GL11.GL_UNSIGNED_INT, 0)
        GL30.glBindVertexArray(0)

        GL11.glEnable(GL11.GL_DEPTH_TEST)
    }

    fun destroy() {
        GL11.glDeleteTextures(colorTextureId)
        GL30.glDeleteRenderbuffers(depthStencilRenderbufferId)
        GL30.glDeleteFramebuffers(fboId)
        GL15.glDeleteBuffers(quadVboId)
        GL15.glDeleteBuffers(quadEboId)
        GL30.glDeleteVertexArrays(quadVaoId)
        compositeShader.destroy()
    }

    companion object {
        private val QUAD_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUV;

            out vec2 vUV;

            void main() {
                vUV = aUV;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """.trimIndent()

        private val COMPOSITE_FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;

            uniform sampler2D u_scene_texture;
            uniform float u_time;
            uniform vec2 u_resolution;
            uniform float u_cpu_load;

            void main() {
                vec2 center = vec2(0.5);
                vec2 toCenter = vUV - center;
                float dist = length(toCenter);
                vec2 dir = dist > 0.0001 ? toCenter / dist : vec2(0.0);

                vec2 uv_r = vUV + dir * 0.003 * dist;
                vec2 uv_b = vUV - dir * 0.003 * dist;

                float r = texture(u_scene_texture, uv_r).r;
                float g = texture(u_scene_texture, vUV).g;
                float b = texture(u_scene_texture, uv_b).b;
                float a = texture(u_scene_texture, vUV).a;
                vec3 color = vec3(r, g, b);

                float vignette = smoothstep(0.85, 0.35, dist * (1.0 + sin(u_time * 2.0) * 0.03));
                color *= mix(0.55, 1.0, vignette);

                color -= sin(vUV.y * u_resolution.y * 1.5) * 0.025;

                // Subtle hint that the whole frame reads as "under load",
                // not just the heart -- a slight warm crimson bias, barely
                // visible at low u_cpu_load, more so as it climbs.
                color = mix(color, color * vec3(1.05, 0.97, 0.95), u_cpu_load);

                FragColor = vec4(color, a);
            }
            """.trimIndent()

        private val QUAD_VERTICES = floatArrayOf(
            // x,    y,    u,   v
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            1f, 1f, 1f, 1f,
            -1f, 1f, 0f, 1f,
        )
        private val QUAD_INDICES = intArrayOf(0, 1, 2, 2, 3, 0)
    }
}
