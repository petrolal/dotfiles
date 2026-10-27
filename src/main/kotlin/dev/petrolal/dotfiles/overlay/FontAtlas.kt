package dev.petrolal.dotfiles.overlay

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.stb.STBTTBakedChar
import org.lwjgl.stb.STBTruetype
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bakes one TrueType font into a single-channel bitmap atlas via
 * `stb_truetype`'s one-call baking API (good enough for flat app-node
 * labels; no SDF/multi-size atlas needed here). The font file itself is
 * resolved through fontconfig (`fc-match`) rather than a hardcoded Nix
 * store path, since that path's hash changes on every nixpkgs update.
 */
class FontAtlas(fontFamily: String, val pixelHeight: Float) {
    val bakedChars: STBTTBakedChar.Buffer
    val textureId: Int

    val bitmapWidth: Int get() = BITMAP_WIDTH
    val bitmapHeight: Int get() = BITMAP_HEIGHT
    val firstChar: Int get() = FIRST_CHAR
    val charCount: Int get() = CHAR_COUNT

    init {
        val ttf = readFontFile(resolveFontFile(fontFamily))
        val bitmap = BufferUtils.createByteBuffer(BITMAP_WIDTH * BITMAP_HEIGHT)
        bakedChars = STBTTBakedChar.malloc(CHAR_COUNT)

        val result = STBTruetype.stbtt_BakeFontBitmap(
            ttf,
            pixelHeight, bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, FIRST_CHAR, bakedChars
        )
        check(result > 0) { "Font atlas too small to bake '$fontFamily' at size $pixelHeight" }

        textureId = uploadAtlasTexture(bitmap)
    }

    fun destroy() {
        GL11.glDeleteTextures(textureId)
        bakedChars.free()
    }

    companion object {
        const val BITMAP_WIDTH = 512
        const val BITMAP_HEIGHT = 512
        const val FIRST_CHAR = 32 // ' '
        const val CHAR_COUNT = 96 // ASCII 32..127

        private fun uploadAtlasTexture(bitmap: ByteBuffer): Int {
            val id = GL11.glGenTextures()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1) // single-channel rows aren't 4-byte aligned in general
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RED,
                BITMAP_WIDTH,
                BITMAP_HEIGHT,
                0,
                GL11.GL_RED,
                GL11.GL_UNSIGNED_BYTE,
                bitmap
            )
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
            return id
        }

        private fun resolveFontFile(fontFamily: String): String {
            try {
                val process = ProcessBuilder("fc-match", "-f", "%{file}", fontFamily).start()
                val path: String? = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.readLine()
                }
                process.waitFor()
                if (!path.isNullOrBlank()) {
                    return path.trim()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: IOException) {
            }
            throw IllegalStateException("Could not resolve a font file for family '$fontFamily' via fc-match")
        }

        private fun readFontFile(path: String): ByteBuffer {
            try {
                val bytes = Files.readAllBytes(Path.of(path))
                val buffer = BufferUtils.createByteBuffer(bytes.size)
                buffer.put(bytes).flip()
                return buffer
            } catch (e: IOException) {
                throw IllegalStateException("Failed to read font file: $path", e)
            }
        }
    }
}
