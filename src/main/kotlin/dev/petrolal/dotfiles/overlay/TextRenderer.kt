package dev.petrolal.dotfiles.overlay

import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack

/**
 * Draws text against a [FontAtlas] in screen-space (orthographic
 * projection over framebuffer pixels), not world-space. Used to label the
 * syringe rack's slots (see [SyringeRack]), projected from each
 * slot's live world position via [OverlayCamera.worldToScreen].
 */
class TextRenderer(private val atlas: FontAtlas) {
    private val shader: Shader
    private val vaoId: Int
    private val vboId: Int
    private val projection = Matrix4f()
    private val vertexBuffer = BufferUtils.createFloatBuffer(MAX_CHARS_PER_DRAW * VERTICES_PER_CHAR * FLOATS_PER_VERTEX)

    init {
        shader = Shader(VERTEX_SOURCE, FRAGMENT_SOURCE)

        vaoId = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(vaoId)

        vboId = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer.capacity().toLong() * java.lang.Float.BYTES, GL15.GL_DYNAMIC_DRAW)

        val stride = FLOATS_PER_VERTEX * java.lang.Float.BYTES
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0)
        GL20.glEnableVertexAttribArray(0)
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * java.lang.Float.BYTES)
        GL20.glEnableVertexAttribArray(1)

        GL30.glBindVertexArray(0)

        shader.use()
        shader.setInt("uAtlas", 0) // texture unit 0, bound in draw()
    }

    fun resize(framebufferWidth: Int, framebufferHeight: Int) {
        projection.identity().ortho(0f, framebufferWidth.toFloat(), framebufferHeight.toFloat(), 0f, -1f, 1f)
    }

    /** Draws left-aligned text with its baseline at (x, y), in framebuffer pixels.  */
    fun draw(
        text: String,
        x: Float,
        y: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        vertexBuffer.clear()
        var vertexCount = 0

        MemoryStack.stackPush().use { stack ->
            val xpos = stack.floats(x)
            val ypos = stack.floats(y)
            val quad = STBTTAlignedQuad.malloc(stack)

            var charsWritten = 0
            var i = 0
            while (i < text.length && charsWritten < MAX_CHARS_PER_DRAW) {
                val c = text[i].code
                if (c < atlas.firstChar || c >= atlas.firstChar + atlas.charCount) {
                    i++
                    continue
                }
                STBTruetype.stbtt_GetBakedQuad(
                    atlas.bakedChars, atlas.bitmapWidth, atlas.bitmapHeight,
                    c - atlas.firstChar, xpos, ypos, quad, true
                )

                putVertex(quad.x0(), quad.y0(), quad.s0(), quad.t0())
                putVertex(quad.x1(), quad.y0(), quad.s1(), quad.t0())
                putVertex(quad.x1(), quad.y1(), quad.s1(), quad.t1())

                putVertex(quad.x0(), quad.y0(), quad.s0(), quad.t0())
                putVertex(quad.x1(), quad.y1(), quad.s1(), quad.t1())
                putVertex(quad.x0(), quad.y1(), quad.s0(), quad.t1())

                charsWritten++
                vertexCount += VERTICES_PER_CHAR
                i++
            }
        }
        if (vertexCount == 0) {
            return
        }
        vertexBuffer.flip()

        shader.use()
        shader.setMat4("uProjection", projection)
        shader.setVec4("uColor", r, g, b, a)

        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.textureId)

        GL30.glBindVertexArray(vaoId)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertexBuffer)
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount)
        GL30.glBindVertexArray(0)
    }

    private fun putVertex(x: Float, y: Float, u: Float, v: Float) {
        vertexBuffer.put(x).put(y).put(u).put(v)
    }

    fun destroy() {
        GL15.glDeleteBuffers(vboId)
        GL30.glDeleteVertexArrays(vaoId)
        shader.destroy()
    }

    companion object {
        private const val MAX_CHARS_PER_DRAW = 256
        private const val FLOATS_PER_VERTEX = 4 // x, y, u, v
        private const val VERTICES_PER_CHAR = 6

        private val VERTEX_SOURCE = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aUV;

            uniform mat4 uProjection;

            out vec2 vUV;

            void main() {
                gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
                vUV = aUV;
            }
            """.trimIndent()

        private val FRAGMENT_SOURCE = """
            #version 330 core
            in vec2 vUV;
            out vec4 fragColor;

            uniform sampler2D uAtlas;
            uniform vec4 uColor;

            void main() {
                float alpha = texture(uAtlas, vUV).r;
                fragColor = vec4(uColor.rgb, uColor.a * alpha);
            }
            """.trimIndent()
    }
}
