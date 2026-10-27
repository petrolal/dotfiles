// WallpaperDaemonManager.java --- Manages the wallpaper daemon as an external process
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.wallpaper

import dev.petrolal.dotfiles.core.DotfilePaths
import dev.petrolal.dotfiles.desktop.DesktopNotifier
import dev.petrolal.dotfiles.system.ProcessRunner
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Finds, stops, starts, restarts, and toggles the wallpaper daemon from a
 * *different* invocation of the CLI (deploy, toggle-hud) -- as
 * opposed to [GpuOffloadLauncher], which handles the daemon deciding
 * how to render itself on its own startup. One reason to change: how the
 * daemon is discovered and (re)started as an OS process.
 */
object WallpaperDaemonManager {
    /** Env vars MangoHud needs to find NVIDIA's management library, which
     * NixOS keeps under /run/opengl-driver/lib rather than a standard system
     * lib path, plus the flag that actually activates its LD_PRELOAD hook.  */
    private val MANGOHUD_ENV = mapOf(
        "LD_LIBRARY_PATH" to "/run/opengl-driver/lib",
        "MANGOHUD" to "1"
    )

    /** Stops any already-running daemon (so a redeploy actually picks up a
     * freshly rebuilt binary or shader instead of leaving the old process
     * running alongside a new one) and starts a fresh one, detached.  */
    fun restart(verbose: Boolean) {
        stopAll(verbose)
        start(verbose, false)
    }

    /** Stops the running daemon and restarts it with MangoHud's on-screen
     * FPS/frametime/GPU overlay toggled on or off, whichever it wasn't
     * already doing. Meant for a keyboard shortcut (see KeyboardShortcuts)
     * so performance can be checked on demand without a terminal.  */
    fun toggleHud(verbose: Boolean): Boolean {
        val launcher = ProcessHandle.allProcesses()
            .filter { isWallpaperDaemon(it) }
            .min(Comparator.comparingLong { it.pid() })
        val wasHudActive = launcher.isPresent && isMangoHudActive(launcher.get())

        stopAll(verbose)

        val enableHud = !wasHudActive
        start(verbose, enableHud)
        DesktopNotifier.notify(
            "Performance HUD",
            if (enableHud) "Enabled — FPS / frametime / GPU overlay" else "Disabled"
        )
        return true
    }

    /** Starts the wallpaper daemon detached, optionally wrapped with MangoHud
     * for an on-screen FPS/frametime/GPU overlay. Falls back to a plain start
     * if MangoHud was requested but isn't installed.  */
    fun start(verbose: Boolean, withHud: Boolean) {
        var effectiveWithHud = withHud
        val exePath = ProcessHandle.current().info().command()
        if (exePath.isEmpty) {
            if (verbose) println("[SKIP] Could not determine this executable's path; not starting the wallpaper daemon.")
            return
        }

        if (effectiveWithHud && !DotfilePaths.commandExists("mangohud")) {
            if (verbose) println("[SKIP] mangohud not found; starting without the performance HUD.")
            effectiveWithHud = false
        }

        if (verbose) println(
            if (effectiveWithHud)
                "Starting 3D wallpaper daemon (with performance HUD)..."
            else
                "Starting 3D wallpaper daemon..."
        )

        val exe = exePath.get()
        if (effectiveWithHud) {
            ProcessRunner.spawnDetached(
                listOf("mangohud", exe, "wallpaper", "--quiet"),
                MANGOHUD_ENV
            )
        } else {
            ProcessRunner.spawnDetached(listOf(exe, "wallpaper", "--quiet"))
        }
    }

    fun stopAll(verbose: Boolean) {
        val existing = ProcessHandle.allProcesses()
            .filter { isWallpaperDaemon(it) }
            .toList()
        if (existing.isEmpty()) return

        if (verbose) println("Stopping ${existing.size} running wallpaper daemon process(es)...")
        val exits = existing.map { ph ->
            ph.destroy()
            ph.onExit()
        }.toTypedArray()

        try {
            // Our own daemon's shutdown-hook watchdog gives up after 2s (see
            // Wallpaper.start()); wait slightly longer than that for a clean
            // handoff, but this is best-effort -- if it's still not gone, the
            // new instance starts anyway rather than blocking the caller on it.
            CompletableFuture.allOf(*exits).get(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }

    private fun isWallpaperDaemon(ph: ProcessHandle): Boolean {
        val commandLine = ph.info().commandLine().orElse("")
        return (commandLine.contains("imp") || commandLine.contains("invoker")) && commandLine.contains("wallpaper")
    }

    /** Reads MANGOHUD=1 out of a running process's own environment (Linux
     * only, via /proc -- ProcessHandle itself has no API for another
     * process's environment, by design).  */
    private fun isMangoHudActive(ph: ProcessHandle): Boolean {
        return try {
            val environ = Files.readString(Path.of("/proc/${ph.pid()}/environ"))
            environ.split('\u0000').any { it == "MANGOHUD=1" }
        } catch (_: IOException) {
            false
        }
    }
}
