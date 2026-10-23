package dev.petrolal.dotfiles.wallpaper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Decodes an image file into tightly-packed RGBA bytes ordered for OpenGL:
 * the first row is the BOTTOM of the picture, so {@code v_uv.y = 0} (bottom of
 * the quad) samples the bottom of the artwork.
 */
public final class ImageLoader {
    private ImageLoader() {}

    public record Rgba(int width, int height, byte[] pixels) {}

    public static Rgba load(Path path) throws IOException {
        BufferedImage img = ImageIO.read(path.toFile());
        if (img == null) throw new IOException("Unsupported or corrupt image: " + path);

        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        byte[] out = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            int dstRow = (h - 1 - y) * w * 4; // flip vertically for GL
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                int o = dstRow + x * 4;
                out[o] = (byte) (p >> 16);
                out[o + 1] = (byte) (p >> 8);
                out[o + 2] = (byte) p;
                out[o + 3] = (byte) 0xFF;
            }
        }
        return new Rgba(w, h, out);
    }
}
