package dev.petrolal.dotfiles.overlay;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Points LWJGL at the system GLFW/Mesa libraries instead of its bundled
 * natives jars, so the overlay links against whatever driver NixOS already
 * wired up (avoids glibc/ABI mismatches between LWJGL's precompiled .so
 * files and NixOS's non-standard dynamic linker layout).
 */
final class NativeLibraryPaths {

    private static final String[] CANDIDATE_DIRS = {
        System.getenv("LWJGL_SYSTEM_LIBRARY_PATH"),
        "/run/opengl-driver/lib",
        "/run/opengl-driver-32/lib",
        "/usr/lib",
        "/usr/lib64"
    };

    private NativeLibraryPaths() {
    }

    /** Must run before any {@code org.lwjgl.*} class is touched. */
    static void install() {
        if (System.getProperty("org.lwjgl.librarypath") != null) {
            return;
        }
        for (String dir : CANDIDATE_DIRS) {
            if (dir != null && Files.isDirectory(Path.of(dir))) {
                System.setProperty("org.lwjgl.librarypath", dir);
                return;
            }
        }
    }
}
