package dev.petrolal.dotfiles.wallpaper

import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Decodes an image file into tightly-packed RGBA bytes ordered for OpenGL:
 * the first row is the BOTTOM of the picture, so `v_uv.y = 0` (bottom of
 * the quad) samples the bottom of the artwork.
 */
object ImageLoader {
    @Throws(IOException::class)
    fun load(path: Path): Rgba {
        val img = ImageIO.read(path.toFile()) ?: throw IOException("Unsupported or corrupt image: $path")

        val w = img.width
        val h = img.height
        val argb = img.getRGB(0, 0, w, h, null, 0, w)
        val out = ByteArray(w * h * 4)
        for (y in 0..<h) {
            val dstRow = (h - 1 - y) * w * 4 // flip vertically for GL
            for (x in 0..<w) {
                val p = argb[y * w + x]
                val o = dstRow + x * 4
                out[o] = (p shr 16).toByte()
                out[o + 1] = (p shr 8).toByte()
                out[o + 2] = p.toByte()
                out[o + 3] = 0xFF.toByte()
            }
        }
        return Rgba(w, h, out)
    }

    @JvmRecord
    data class Rgba(val width: Int, val height: Int, val pixels: ByteArray)
}
