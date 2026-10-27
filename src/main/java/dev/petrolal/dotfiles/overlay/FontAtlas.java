package dev.petrolal.dotfiles.overlay;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Bakes one TrueType font into a single-channel bitmap atlas via
 * {@code stb_truetype}'s one-call baking API (good enough for flat app-node
 * labels; no SDF/multi-size atlas needed here). The font file itself is
 * resolved through fontconfig ({@code fc-match}) rather than a hardcoded Nix
 * store path, since that path's hash changes on every nixpkgs update.
 */
final class FontAtlas {

    private static final int BITMAP_WIDTH = 512;
    private static final int BITMAP_HEIGHT = 512;
    private static final int FIRST_CHAR = 32; // ' '
    private static final int CHAR_COUNT = 96; // ASCII 32..127

    private final STBTTBakedChar.Buffer bakedChars;
    private final int textureId;
    private final float pixelHeight;

    FontAtlas(String fontFamily, float pixelHeight) {
        this.pixelHeight = pixelHeight;

        ByteBuffer ttf = readFontFile(resolveFontFile(fontFamily));
        ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_WIDTH * BITMAP_HEIGHT);
        bakedChars = STBTTBakedChar.malloc(CHAR_COUNT);

        int result = STBTruetype.stbtt_BakeFontBitmap(
                ttf, pixelHeight, bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, FIRST_CHAR, bakedChars);
        if (result <= 0) {
            throw new IllegalStateException(
                    "Font atlas too small to bake '" + fontFamily + "' at size " + pixelHeight);
        }

        textureId = uploadAtlasTexture(bitmap);
    }

    private static int uploadAtlasTexture(ByteBuffer bitmap) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // single-channel rows aren't 4-byte aligned in general
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, BITMAP_WIDTH, BITMAP_HEIGHT, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return id;
    }

    private static String resolveFontFile(String fontFamily) {
        try {
            Process process = new ProcessBuilder("fc-match", "-f", "%{file}", fontFamily).start();
            String path;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                path = reader.readLine();
            }
            process.waitFor();
            if (path != null && !path.isBlank()) {
                return path.trim();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        throw new IllegalStateException(
                "Could not resolve a font file for family '" + fontFamily + "' via fc-match");
    }

    private static ByteBuffer readFontFile(String path) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read font file: " + path, e);
        }
    }

    STBTTBakedChar.Buffer bakedChars() {
        return bakedChars;
    }

    int textureId() {
        return textureId;
    }

    int bitmapWidth() {
        return BITMAP_WIDTH;
    }

    int bitmapHeight() {
        return BITMAP_HEIGHT;
    }

    int firstChar() {
        return FIRST_CHAR;
    }

    int charCount() {
        return CHAR_COUNT;
    }

    float pixelHeight() {
        return pixelHeight;
    }

    void destroy() {
        glDeleteTextures(textureId);
        bakedChars.free();
    }
}
