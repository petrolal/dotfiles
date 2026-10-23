// PngWriter.scala --- Minimal, dependency-free PNG encoder.
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker

import java.io.{ByteArrayOutputStream, IOException, OutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{CRC32, Deflater, DeflaterOutputStream}
import scala.util.Using

object PngWriter:
  private val SIGNATURE: Array[Byte] = Array(137.toByte, 80, 78, 71, 13, 10, 26, 10)

  /** Writes tightly-packed, row-0-at-top RGBA pixel data as an 8-bit
   * truecolor-with-alpha (color type 6) PNG. */
  def write(rgba: Array[Byte], width: Int, height: Int, path: Path): Unit =
    val rowBytes = width * 4
    val raw = new Array[Byte](height * (1 + rowBytes))
    var y = 0
    while y < height do
      val srcOff = y * rowBytes
      val dstOff = y * (1 + rowBytes)
      raw(dstOff) = 0 // filter type: None
      System.arraycopy(rgba, srcOff, raw, dstOff + 1, rowBytes)
      y += 1

    val deflater = new Deflater(Deflater.DEFAULT_COMPRESSION)
    val idat = new ByteArrayOutputStream()
    try
      Using.resource(new DeflaterOutputStream(idat, deflater)) { dos =>
        dos.write(raw)
      }
    finally
      deflater.end()

    Using.resource(Files.newOutputStream(path)) { out =>
      out.write(SIGNATURE)
      writeChunk(out, "IHDR", ihdr(width, height))
      writeChunk(out, "IDAT", idat.toByteArray)
      writeChunk(out, "IEND", new Array[Byte](0))
    }

  private def ihdr(width: Int, height: Int): Array[Byte] =
    val b = new ByteArrayOutputStream()
    writeIntTo(b, width)
    writeIntTo(b, height)
    b.write(8) // bit depth
    b.write(6) // color type: truecolor + alpha
    b.write(0) // compression method
    b.write(0) // filter method
    b.write(0) // interlace method
    b.toByteArray

  private def writeChunk(out: OutputStream, `type`: String, data: Array[Byte]): Unit =
    val typeBytes = `type`.getBytes(StandardCharsets.US_ASCII)
    writeIntTo(out, data.length)
    out.write(typeBytes)
    out.write(data)
    val crc = new CRC32()
    crc.update(typeBytes)
    crc.update(data)
    writeIntTo(out, crc.getValue.toInt)

  private def writeIntTo(out: OutputStream, v: Int): Unit =
    out.write((v >>> 24) & 0xFF)
    out.write((v >>> 16) & 0xFF)
    out.write((v >>> 8) & 0xFF)
    out.write(v & 0xFF)

  private def writeIntTo(out: ByteArrayOutputStream, v: Int): Unit =
    out.write((v >>> 24) & 0xFF)
    out.write((v >>> 16) & 0xFF)
    out.write((v >>> 8) & 0xFF)
    out.write(v & 0xFF)
