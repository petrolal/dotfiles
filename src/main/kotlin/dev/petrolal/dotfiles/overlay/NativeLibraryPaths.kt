package dev.petrolal.dotfiles.overlay

import java.nio.file.Files
import java.nio.file.Path

/**
 * Points LWJGL at the system GLFW/Mesa libraries instead of its bundled
 * natives jars, so the overlay links against whatever driver NixOS already
 * wired up (avoids glibc/ABI mismatches between LWJGL's precompiled .so
 * files and NixOS's non-standard dynamic linker layout).
 */
object NativeLibraryPaths {
    private val CANDIDATE_DIRS = listOfNotNull(
        System.getenv("LWJGL_SYSTEM_LIBRARY_PATH"),
        "/run/opengl-driver/lib",
        "/run/opengl-driver-32/lib",
        "/usr/lib",
        "/usr/lib64"
    )

    /** Must run before any `org.lwjgl.*` class is touched.  */
    fun install() {
        if (System.getProperty("org.lwjgl.librarypath") != null) {
            return
        }
        for (dir in CANDIDATE_DIRS) {
            if (Files.isDirectory(Path.of(dir))) {
                System.setProperty("org.lwjgl.librarypath", dir)
                return
            }
        }
    }
}
