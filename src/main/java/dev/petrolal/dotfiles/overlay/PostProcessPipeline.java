package dev.petrolal.dotfiles.overlay;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders the 3D scene (heart, syringe rack, and anything added later) into
 * an offscreen color+depth framebuffer, then composites it to the real
 * window with full-screen GLSL effects (chromatic aberration, vignette,
 * scanlines) -- see docs/biopunk_plan.md. Call order each frame:
 * {@link #beginSceneRender()}, draw the 3D scene as usual, then
 * {@link #endSceneRenderAndComposite}.
 *
 * <p>{@code GL_RGBA8}, not {@code GL_RGBA16F}: nothing rendered here needs
 * HDR range (no bloom/tonemap pass consumes over-1.0 values), and 8-bit
 * avoids depending on a renderable float-texture format that isn't
 * guaranteed on every driver this runs on (see NativeLibraryPaths/
 * docs/migration_plan.md's NVIDIA PRIME notes) -- there's a concrete reason
 * to need 16F before paying for it, not yet.
 */
final class PostProcessPipeline {

    private static final String QUAD_VERTEX_SOURCE = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUV;

            out vec2 vUV;

            void main() {
                vUV = aUV;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """;

    private static final String COMPOSITE_FRAGMENT_SOURCE = """
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
            """;

    private static final float[] QUAD_VERTICES = {
            // x,    y,    u,   v
            -1f, -1f,  0f,  0f,
             1f, -1f,  1f,  0f,
             1f,  1f,  1f,  1f,
            -1f,  1f,  0f,  1f,
    };
    private static final int[] QUAD_INDICES = { 0, 1, 2, 2, 3, 0 };

    private final Shader compositeShader;
    private final int quadVaoId;
    private final int quadVboId;
    private final int quadEboId;

    private int fboId;
    private int colorTextureId;
    private int depthStencilRenderbufferId;
    private int width;
    private int height;

    PostProcessPipeline(int width, int height) {
        compositeShader = new Shader(QUAD_VERTEX_SOURCE, COMPOSITE_FRAGMENT_SOURCE);

        quadVaoId = glGenVertexArrays();
        glBindVertexArray(quadVaoId);

        quadVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVboId);
        glBufferData(GL_ARRAY_BUFFER, QUAD_VERTICES, GL_STATIC_DRAW);

        quadEboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadEboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, QUAD_INDICES, GL_STATIC_DRAW);

        int stride = 4 * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);

        fboId = glGenFramebuffers();
        createAttachments(width, height);

        compositeShader.use();
        compositeShader.setInt("u_scene_texture", 0);
    }

    /** (Re)creates the color texture and depth/stencil renderbuffer at the
     * given size and attaches them to {@link #fboId}. Called from the
     * constructor and from {@link #resize} -- the FBO object itself is
     * never recreated, only its attachments, since a window resize doesn't
     * invalidate the FBO's identity, just the size of what's bound to it. */
    private void createAttachments(int newWidth, int newHeight) {
        width = Math.max(1, newWidth);
        height = Math.max(1, newHeight);

        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        colorTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTextureId, 0);

        depthStencilRenderbufferId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthStencilRenderbufferId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthStencilRenderbufferId);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Post-process framebuffer incomplete: 0x" + Integer.toHexString(status));
        }

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** Call from the GLFW framebuffer-size callback, same as
     * {@code camera.resize}/{@code textRenderer.resize} -- deletes and
     * recreates the color texture/renderbuffer at the new size so they
     * never stay stale relative to the window. */
    void resize(int newWidth, int newHeight) {
        glDeleteTextures(colorTextureId);
        glDeleteRenderbuffers(depthStencilRenderbufferId);
        createAttachments(newWidth, newHeight);
    }

    /** Binds the offscreen framebuffer and clears it -- call before drawing
     * the 3D scene for this frame. */
    void beginSceneRender() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /** Unbinds back to the default framebuffer and draws the full-screen
     * composite quad sampling what was just rendered into the offscreen
     * color texture. Restores {@code GL_DEPTH_TEST} before returning so the
     * next frame's {@link #beginSceneRender} starts from the same state
     * this one did -- the composite pass itself needs it off (the quad is
     * drawn at a fixed NDC depth with nothing else in that buffer to test
     * against), but every 3D draw call in between expects it on. */
    void endSceneRenderAndComposite(double elapsedSeconds, float cpuLoad, int windowWidth, int windowHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, windowWidth, windowHeight);
        glDisable(GL_DEPTH_TEST);

        compositeShader.use();
        compositeShader.setFloat("u_time", (float) elapsedSeconds);
        compositeShader.setFloat("u_cpu_load", Math.clamp(cpuLoad, 0f, 1f));
        compositeShader.setVec2("u_resolution", windowWidth, windowHeight);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTextureId);

        glBindVertexArray(quadVaoId);
        glDrawElements(GL_TRIANGLES, QUAD_INDICES.length, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        glEnable(GL_DEPTH_TEST);
    }

    void destroy() {
        glDeleteTextures(colorTextureId);
        glDeleteRenderbuffers(depthStencilRenderbufferId);
        glDeleteFramebuffers(fboId);
        glDeleteBuffers(quadVboId);
        glDeleteBuffers(quadEboId);
        glDeleteVertexArrays(quadVaoId);
        compositeShader.destroy();
    }
}
