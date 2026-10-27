// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core

import dev.petrolal.dotfiles.desktop.DesktopServices
import dev.petrolal.dotfiles.desktop.KeyboardShortcuts
import dev.petrolal.dotfiles.desktop.Xfconf
import dev.petrolal.dotfiles.linker.Linker
import dev.petrolal.dotfiles.linker.Linker.UnlinkOutcome
import dev.petrolal.dotfiles.locker.ScreenLocker
import dev.petrolal.dotfiles.overlay.OverlayWindow
import dev.petrolal.dotfiles.wallpaper.GpuOffloadLauncher
import dev.petrolal.dotfiles.wallpaper.Wallpaper
import dev.petrolal.dotfiles.wallpaper.WallpaperDaemonManager

/**
 * Composes the focused, single-purpose collaborators below into the CLI's
 * four commands (deploy, uninstall, wallpaper, toggle-hud). This class's one
 * reason to change is how those commands are sequenced -- not how linking,
 * settings, desktop reloads, or the wallpaper daemon's lifecycle actually
 * work, each of which lives in its own class:
 * 
 *  * [Linker] -- symlink discovery/creation/removal
 *  * [Xfconf] -- XFCE/GNOME settings
 *  * [KeyboardShortcuts] -- keybinding catalog
 *  * [DesktopServices] -- reloading live desktop components
 *  * [WallpaperDaemonManager] -- daemon lifecycle from another invocation
 *  * [GpuOffloadLauncher] -- the daemon's own self-relaunch decision
 * 
 */
object Orchestrator {
    fun deploy(
        dryRun: Boolean, verbose: Boolean, reload: Boolean, applySettings: Boolean,
        applyWallpaper: Boolean
    ): DeployResult {
        if (verbose) println("=== Deploying " + Theme.NAME + " Dotfiles ===")
        val root = DotfilePaths.findDotfilesRoot()
        val home = DotfilePaths.userHomeDirectory()
        var failures = 0
        var successes = 0
        if (verbose) {
            println("Root:   $root")
            println("Target: $home")
        }

        for (mapping in Linker.collectAllMappings(root)) {
            if (Linker.linkFile(mapping.from, mapping.to, root, home, dryRun, verbose).isOk()) successes++
            else failures++
        }

        if (applySettings) {
            failures += Xfconf.applyXfceSettings(dryRun, verbose)
            if (reload) DesktopServices.reload(dryRun, verbose)
        }

        // The config/autostart/imp-wallpaper.desktop entry (symlinked by
        // the loop above like any other config/ file) covers future logins;
        // this covers the current session, so `deploy` leaves the freshest
        // build actually running immediately instead of only promising it for
        // next time.
        if (applyWallpaper) {
            if (System.getenv("DISPLAY") == null) {
                if (verbose) println("[SKIP] No DISPLAY available, skipping wallpaper daemon (re)start.")
            } else if (dryRun) {
                if (verbose) println("[DRY-RUN] Would (re)start the 3D wallpaper daemon.")
            } else {
                WallpaperDaemonManager.restart(verbose)
            }
        }

        if (verbose) {
            if (failures == 0) {
                println("Deployment finished successfully.")
            } else {
                System.err.println("Deployment finished with $failures failure(s).")
            }
        }
        return DeployResult(failures == 0, failures, successes)
    }

    fun uninstall(dryRun: Boolean, verbose: Boolean): UninstallResult {
        if (verbose) println("=== Removing " + Theme.NAME + " Dotfiles Symlinks ===")
        val root = DotfilePaths.findDotfilesRoot()
        val home = DotfilePaths.userHomeDirectory()
        var failures = 0
        var removedCount = 0
        if (verbose) println("Target: $home")

        for (mapping in Linker.collectAllMappings(root)) {
            when (Linker.unlinkFile(mapping.to, home, dryRun, verbose)) {
                UnlinkOutcome.REMOVED -> removedCount++
                UnlinkOutcome.FAILED -> failures++
                UnlinkOutcome.SKIPPED -> {
                }
            }
        }

        if (verbose) {
            if (dryRun) {
                println("Dry-run complete. $removedCount symlink(s) would be removed.")
            } else {
                val failureSuffix = if (failures > 0) ", $failures failure(s)" else ""
                println(
                    "Uninstallation complete. $removedCount symlink(s) removed$failureSuffix."
                )
            }
        }
        return UninstallResult(failures == 0, failures, removedCount)
    }

    fun set3DWallpaper(dryRun: Boolean, verbose: Boolean, maxFps: Int): WallpaperResult {
        if (verbose) {
            println("=== Applying 3D Wallpaper ===")
        }

        if (dryRun) {
            if (verbose) {
                println("[DRY-RUN] Would start OpenGL 3D wallpaper daemon.")
            }
            return WallpaperResult(true, 0)
        }

        if (GpuOffloadLauncher.offloadAvailableAndNotActive()) {
            // Null means "couldn't resolve this executable's own path, never
            // attempted the relaunch" -- fall through to render in-process
            // instead of reporting a failure for something that never ran.
            val result = GpuOffloadLauncher.relaunch(verbose, maxFps)
            if (result != null) {
                return WallpaperResult(result.ok, if (result.ok) 0 else 1)
            }
        }

        try {
            Wallpaper().start(maxFps)
            return WallpaperResult(true, 0)
        } catch (ex: Throwable) {
            System.err.println("Error on wallpaper application: " + ex.message)
            return WallpaperResult(false, 1)
        }
    }

    /** Starts the interactive PAM-authenticated screen locker in-process
     * (blocks the calling thread until the user unlocks or the process is
     * signaled -- see [ScreenLocker]). `testTimeoutSeconds > 0`,
     * `dumpFramePath != null`, and `injectPassword != null` are
     * dev/test-only escape hatches; never pass any of them for a real lock.  */
    fun lockScreen(
        dryRun: Boolean,
        testTimeoutSeconds: Float,
        dumpFramePath: String?,
        injectPassword: String?
    ): Boolean {
        if (dryRun) {
            println("[DRY-RUN] Would start the interactive screen locker.")
            return true
        }
        try {
            ScreenLocker().start(testTimeoutSeconds, dumpFramePath, injectPassword)
            return true
        } catch (ex: Throwable) {
            System.err.println("Error on screen locker: " + ex.message)
            return false
        }
    }

    /** Starts the 3D app-launcher overlay in-process (blocks the calling
     * thread until the window closes -- see [OverlayWindow]).  */
    fun runOverlay(dryRun: Boolean): Boolean {
        if (dryRun) {
            println("[DRY-RUN] Would start the 3D app-launcher overlay.")
            return true
        }
        try {
            OverlayWindow().start()
            return true
        } catch (ex: Throwable) {
            System.err.println("Error on overlay: " + ex.message)
            return false
        }
    }

    /** Toggles the running wallpaper daemon's MangoHud performance overlay
     * on or off. See [KeyboardShortcuts]
     * for the keybinding that invokes this (via `imp toggle-hud`).  */
    fun toggleWallpaperHud(verbose: Boolean): Boolean {
        return WallpaperDaemonManager.toggleHud(verbose)
    }

    @JvmRecord
    data class DeployResult(val ok: Boolean, val failures: Int, val successes: Int)

    @JvmRecord
    data class UninstallResult(val ok: Boolean, val failures: Int, val removed: Int)

    @JvmRecord
    data class WallpaperResult(val ok: Boolean, val failures: Int)
}
