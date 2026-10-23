// ImageLoader.scala --- Image asset decoding into GL-ready RGBA bytes
// License: GPL-3.0-or-later
// Ported from ImageLoader.java (docs/scala-migration-checklist.md step 5).
//
// Decodes an image file into tightly-packed RGBA bytes ordered for OpenGL:
// the first row is the BOTTOM of the picture, so v_uv.y = 0 (bottom of the
// quad) samples the bottom of the artwork.
package dev.petrolal.dotfiles.wallpaper

import java.io.IOException
import javax.imageio.ImageIO
import java.nio.file.Path

object ImageLoader:

  case class Rgba(width: Int, height: Int, pixels: Array[Byte])

  def load(path: Path): Rgba =
    val img = ImageIO.read(path.toFile)
    if img == null then throw new IOException(s"Unsupported or corrupt image: $path")

    val w = img.getWidth
    val h = img.getHeight
    val argb = img.getRGB(0, 0, w, h, null, 0, w)
    val out = new Array[Byte](w * h * 4)
    for y <- 0 until h do
      val dstRow = (h - 1 - y) * w * 4 // flip vertically for GL
      for x <- 0 until w do
        val p = argb(y * w + x)
        val o = dstRow + x * 4
        out(o) = (p >> 16).toByte
        out(o + 1) = (p >> 8).toByte
        out(o + 2) = p.toByte
        out(o + 3) = 0xFF.toByte
    Rgba(w, h, out)
