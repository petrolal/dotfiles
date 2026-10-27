// PngWriter.java --- Minimal, dependency-free PNG encoder.
//
// Deliberately NOT using javax.imageio/java.awt.image.BufferedImage: under
// this project's GraalVM native-image build, merely touching BufferedImage
// throws "NoClassDefFoundError: java.awt.GraphicsEnvironment" at runtime
// (its static init loads a bundled native libawt.so whose JNI_OnLoad does a
// JNI FindClass the image's reflection metadata doesn't cover -- see
// LoginPromptTexture's header comment for the full story, which hit the
// same wall). Only java.util.zip (Deflater, CRC32) and java.io are used
// here, both fully supported by native-image with no extra metadata.
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.locker

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

internal object PngWriter {
    private val SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

    /** Writes tightly-packed, row-0-at-top RGBA pixel data as an 8-bit
     * truecolor-with-alpha (color type 6) PNG.  */
    @Throws(IOException::class)
    fun write(rgba: ByteArray, width: Int, height: Int, path: Path) {
        val rowBytes = width * 4
        val raw = ByteArray(height * (1 + rowBytes))
        for (y in 0..<height) {
            val srcOff = y * rowBytes
            val dstOff = y * (1 + rowBytes)
            raw[dstOff] = 0 // filter type: None
            System.arraycopy(rgba, srcOff, raw, dstOff + 1, rowBytes)
        }

        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        val idat = ByteArrayOutputStream()
        try {
            DeflaterOutputStream(idat, deflater).use { dos ->
                dos.write(raw)
            }
        } finally {
            deflater.end()
        }

        Files.newOutputStream(path).use { out ->
            out.write(SIGNATURE)
            writeChunk(out, "IHDR", ihdr(width, height))
            writeChunk(out, "IDAT", idat.toByteArray())
            writeChunk(out, "IEND", ByteArray(0))
        }
    }

    private fun ihdr(width: Int, height: Int): ByteArray {
        val b = ByteArrayOutputStream()
        writeIntTo(b, width)
        writeIntTo(b, height)
        b.write(8) // bit depth
        b.write(6) // color type: truecolor + alpha
        b.write(0) // compression method
        b.write(0) // filter method
        b.write(0) // interlace method
        return b.toByteArray()
    }

    @Throws(IOException::class)
    private fun writeChunk(out: OutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
        writeIntTo(out, data.size)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeIntTo(out, crc.value.toInt())
    }

    @Throws(IOException::class)
    private fun writeIntTo(out: OutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
