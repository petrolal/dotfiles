package dev.petrolal.dotfiles.overlay;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Resolves a {@code .desktop} file's {@code Icon=} value to a GL texture,
 * via a freedesktop icon-theme-ish lookup (not full spec compliance -- no
 * index.theme parsing), with a procedural fallback for anything unresolved.
 * Results are cached by icon name; textures live for the process lifetime
 * (there's no "evict" case yet -- the app list this loads from is static per
 * run).
 *
 * <p>The two themes actually installed here (see {@code Theme.ICONS} in
 * {@code core/Theme.java}) don't share a directory layout: Chicago95 nests
 * {@code <context>/<size>/<name>.png}, hicolor nests
 * {@code <size>x<size>/<context>/<name>.png}. Rather than hardcode either
 * shape, this does a bounded-depth recursive search for {@code <name>.png}
 * under the theme root and picks whichever match's path contains a size
 * token closest to the requested size.
 */
final class IconLoader {

    private static final String[] THEME_SEARCH_ORDER = { "Chicago95", "hicolor" };
    private static final String[] THEME_ROOTS = {
            System.getProperty("user.home") + "/.local/share/icons",
            "/run/current-system/sw/share/icons",
            "/usr/share/icons"
    };
    private static final int MAX_SEARCH_DEPTH = 6;
    private static final Pattern SIZE_TOKEN = Pattern.compile("(\\d+)(?:x\\d+)?");

    record IconTexture(int textureId, int width, int height) {}

    private final Map<String, IconTexture> cache = new HashMap<>();
    private IconTexture fallback;

    /**
     * Returns a cached or freshly loaded texture for {@code iconNameOrPath},
     * or the fallback texture if unresolved -- including when
     * {@code iconNameOrPath} is {@code null}/blank, which many
     * {@code .desktop} entries have no {@code Icon=} line for.
     */
    IconTexture load(String iconNameOrPath, int desiredSize) {
        if (iconNameOrPath == null || iconNameOrPath.isBlank()) {
            return fallbackTexture();
        }
        return cache.computeIfAbsent(iconNameOrPath, name -> {
            Path resolved = resolve(name, desiredSize);
            if (resolved == null) {
                return fallbackTexture();
            }
            IconTexture decoded = decode(resolved);
            return decoded != null ? decoded : fallbackTexture();
        });
    }

    private static Path resolve(String iconNameOrPath, int desiredSize) {
        Path asPath = Path.of(iconNameOrPath);
        if (asPath.isAbsolute() && Files.isRegularFile(asPath)) {
            return asPath;
        }

        Path best = null;
        int bestSizeDelta = Integer.MAX_VALUE;

        for (String root : THEME_ROOTS) {
            for (String theme : THEME_SEARCH_ORDER) {
                Path themeDir = Path.of(root, theme);
                if (!Files.isDirectory(themeDir)) {
                    continue;
                }
                for (Path candidate : findCandidates(themeDir, iconNameOrPath + ".png")) {
                    int delta = Math.abs(sizeHint(candidate) - desiredSize);
                    if (delta < bestSizeDelta) {
                        bestSizeDelta = delta;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static List<Path> findCandidates(Path themeDir, String filename) {
        List<Path> found = new ArrayList<>();
        walk(themeDir, filename, 0, found);
        return found;
    }

    private static void walk(Path dir, String filename, int depth, List<Path> found) {
        if (depth > MAX_SEARCH_DEPTH) {
            return;
        }
        try (var entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    walk(entry, filename, depth + 1, found);
                } else if (entry.getFileName().toString().equals(filename)) {
                    found.add(entry);
                }
            }
        } catch (IOException _) {
            // Unreadable directory: skip it, this is a best-effort lookup.
        }
    }

    /** Extracts the first size-like number along the path (e.g. "48" from ".../48x48/apps/foo.png"), or 48 if none found. */
    private static int sizeHint(Path path) {
        for (Path part : path) {
            Matcher m = SIZE_TOKEN.matcher(part.toString());
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 48;
    }

    private static IconTexture decode(Path file) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer pixels = STBImage.stbi_load(file.toString(), width, height, channels, 4);
            if (pixels == null) {
                System.err.println("[overlay] Failed to decode icon " + file + ": " + STBImage.stbi_failure_reason());
                return null;
            }
            try {
                int textureId = uploadTexture(pixels, width.get(0), height.get(0));
                return new IconTexture(textureId, width.get(0), height.get(0));
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private static int uploadTexture(ByteBuffer pixels, int width, int height) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return id;
    }

    /** A small magenta/gray checkerboard -- deliberately conspicuous, never mistaken for a real icon. */
    private IconTexture fallbackTexture() {
        if (fallback != null) {
            return fallback;
        }
        int size = 8;
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean light = (x + y) % 2 == 0;
                pixels.put((byte) (light ? 230 : 60));
                pixels.put((byte) 0);
                pixels.put((byte) (light ? 230 : 60));
                pixels.put((byte) 255);
            }
        }
        pixels.flip();
        int textureId = uploadTexture(pixels, size, size);
        fallback = new IconTexture(textureId, size, size);
        return fallback;
    }

    void destroy() {
        for (IconTexture texture : cache.values()) {
            glDeleteTextures(texture.textureId());
        }
        if (fallback != null) {
            glDeleteTextures(fallback.textureId());
        }
        cache.clear();
    }
}
