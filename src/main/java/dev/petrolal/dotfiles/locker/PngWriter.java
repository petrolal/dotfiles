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
package dev.petrolal.dotfiles.locker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

final class PngWriter {
    private PngWriter() {}

    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    /** Writes tightly-packed, row-0-at-top RGBA pixel data as an 8-bit
     * truecolor-with-alpha (color type 6) PNG. */
    static void write(byte[] rgba, int width, int height, Path path) throws IOException {
        int rowBytes = width * 4;
        byte[] raw = new byte[height * (1 + rowBytes)];
        for (int y = 0; y < height; y++) {
            int srcOff = y * rowBytes;
            int dstOff = y * (1 + rowBytes);
            raw[dstOff] = 0; // filter type: None
            System.arraycopy(rgba, srcOff, raw, dstOff + 1, rowBytes);
        }

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(idat, deflater)) {
            dos.write(raw);
        } finally {
            deflater.end();
        }

        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(SIGNATURE);
            writeChunk(out, "IHDR", ihdr(width, height));
            writeChunk(out, "IDAT", idat.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
        }
    }

    private static byte[] ihdr(int width, int height) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeIntTo(b, width);
        writeIntTo(b, height);
        b.write(8); // bit depth
        b.write(6); // color type: truecolor + alpha
        b.write(0); // compression method
        b.write(0); // filter method
        b.write(0); // interlace method
        return b.toByteArray();
    }

    private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeIntTo(out, data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeIntTo(out, (int) crc.getValue());
    }

    private static void writeIntTo(OutputStream out, int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writeIntTo(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
