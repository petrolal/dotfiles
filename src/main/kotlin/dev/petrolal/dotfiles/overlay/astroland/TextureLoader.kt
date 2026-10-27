package dev.petrolal.dotfiles.overlay.astroland

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.stb.STBImage
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageInputStream

/** A GL texture plus the pixel size it was uploaded at (needed for sprite sizing, à la the original's `tex->m_size`). */
data class Texture(val id: Int, val width: Int, val height: Int)

/** One decoded frame of an animated GIF: its texture and how long (seconds) to hold it. */
data class GifFrame(val texture: Texture, val delaySeconds: Float)

/**
 * Loads astroland's PNG sprites and GIFs from the classpath, under `resources/astroland`.
 * The original loads PNGs via cairo and animates GIFs frame-by-frame through
 * `gifdec.c`; here PNGs go through stb_image (already vendored for this
 * project's other overlay textures) and GIFs through javax.imageio's GIF
 * reader, which already exposes per-frame delay/disposal metadata so there's
 * no need to port `gifdec` itself.
 */
object TextureLoader {
    private val pngCache = HashMap<String, Texture>()

    fun loadPng(resourcePath: String): Texture = pngCache.getOrPut(resourcePath) {
        val bytes = readResourceBytes(resourcePath)
        val buffer = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()

        val width = IntArray(1)
        val height = IntArray(1)
        val channels = IntArray(1)
        val pixels = STBImage.stbi_load_from_memory(buffer, width, height, channels, 4)
            ?: throw IllegalStateException("Failed to decode $resourcePath: ${STBImage.stbi_failure_reason()}")
        try {
            uploadRgba(pixels, width[0], height[0])
        } finally {
            STBImage.stbi_image_free(pixels)
        }
    }

    fun loadGifFrames(resourcePath: String): List<GifFrame> {
        val readers = ImageIO.getImageReadersBySuffix("gif")
        check(readers.hasNext()) { "No GIF reader registered" }
        val reader: ImageReader = readers.next()

        readResourceStream(resourcePath).use { input ->
            reader.setInput(MemoryCacheImageInputStream(input), false)
            val frameCount = reader.getNumImages(true)
            val frames = ArrayList<GifFrame>(frameCount)
            for (i in 0 until frameCount) {
                val image: BufferedImage = reader.read(i)
                val delay = readFrameDelaySeconds(reader, i)
                frames.add(GifFrame(uploadBufferedImage(image), delay))
            }
            return frames
        }
    }

    private fun readFrameDelaySeconds(reader: ImageReader, index: Int): Float {
        val metadata = reader.getImageMetadata(index)
        val root = metadata.getAsTree(metadata.nativeMetadataFormatName) as IIOMetadataNode
        for (i in 0 until root.length) {
            val node = root.item(i)
            if (node.nodeName == "GraphicControlExtension") {
                val delayCentiseconds = (node as IIOMetadataNode).getAttribute("delayTime").toIntOrNull() ?: 10
                return delayCentiseconds / 100f
            }
        }
        return 0.1f
    }

    private fun uploadBufferedImage(image: BufferedImage): Texture {
        val width = image.width
        val height = image.height
        val buffer = BufferUtils.createByteBuffer(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = image.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                buffer.put(r.toByte()).put(g.toByte()).put(b.toByte()).put(a.toByte())
            }
        }
        buffer.flip()
        return uploadRgba(buffer, width, height)
    }

    private fun uploadRgba(pixels: java.nio.ByteBuffer, width: Int, height: Int): Texture {
        val id = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        return Texture(id, width, height)
    }

    fun readResourceBytes(resourcePath: String): ByteArray {
        readResourceStream(resourcePath).use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            return out.toByteArray()
        }
    }

    private fun readResourceStream(resourcePath: String) =
        TextureLoader::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Missing classpath resource: $resourcePath")
}
