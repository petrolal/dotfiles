// WallpaperDaemonManager.java --- Manages the wallpaper daemon as an external process
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.wallpaper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.petrolal.dotfiles.core.DotfilePaths;
import dev.petrolal.dotfiles.desktop.DesktopNotifier;
import dev.petrolal.dotfiles.system.ProcessRunner;

/**
 * Finds, stops, starts, restarts, and toggles the wallpaper daemon from a
 * <em>different</em> invocation of the CLI (deploy, toggle-hud) -- as
 * opposed to {@link GpuOffloadLauncher}, which handles the daemon deciding
 * how to render itself on its own startup. One reason to change: how the
 * daemon is discovered and (re)started as an OS process.
 */
public final class WallpaperDaemonManager {
    private WallpaperDaemonManager() {}

    /** Env vars MangoHud needs to find NVIDIA's management library, which
     * NixOS keeps under /run/opengl-driver/lib rather than a standard system
     * lib path, plus the flag that actually activates its LD_PRELOAD hook. */
    private static final Map<String, String> MANGOHUD_ENV = Map.of(
            "LD_LIBRARY_PATH", "/run/opengl-driver/lib",
            "MANGOHUD", "1");

    /** Stops any already-running daemon (so a redeploy actually picks up a
     * freshly rebuilt binary or shader instead of leaving the old process
     * running alongside a new one) and starts a fresh one, detached. */
    public static void restart(boolean verbose) {
        stopAll(verbose);
        start(verbose, false);
    }

    /** Stops the running daemon and restarts it with MangoHud's on-screen
     * FPS/frametime/GPU overlay toggled on or off, whichever it wasn't
     * already doing. Meant for a keyboard shortcut (see KeyboardShortcuts)
     * so performance can be checked on demand without a terminal. */
    public static boolean toggleHud(boolean verbose) {
        Optional<ProcessHandle> launcher = ProcessHandle.allProcesses()
                .filter(WallpaperDaemonManager::isWallpaperDaemon)
                .min(Comparator.comparingLong(ProcessHandle::pid));
        boolean wasHudActive = launcher.isPresent() && isMangoHudActive(launcher.get());

        stopAll(verbose);

        boolean enableHud = !wasHudActive;
        start(verbose, enableHud);
        DesktopNotifier.notify("Performance HUD",
                enableHud ? "Enabled — FPS / frametime / GPU overlay" : "Disabled");
        return true;
    }

    /** Starts the wallpaper daemon detached, optionally wrapped with MangoHud
     * for an on-screen FPS/frametime/GPU overlay. Falls back to a plain start
     * if MangoHud was requested but isn't installed. */
    public static void start(boolean verbose, boolean withHud) {
        Optional<String> exePath = ProcessHandle.current().info().command();
        if (exePath.isEmpty()) {
            if (verbose)
                System.out.println("[SKIP] Could not determine this executable's path; not starting the wallpaper daemon.");
            return;
        }

        if (withHud && !DotfilePaths.commandExists("mangohud")) {
            if (verbose)
                System.out.println("[SKIP] mangohud not found; starting without the performance HUD.");
            withHud = false;
        }

        if (verbose)
            System.out.println(withHud ? "Starting 3D wallpaper daemon (with performance HUD)..."
                    : "Starting 3D wallpaper daemon...");

        if (withHud) {
            ProcessRunner.spawnDetached(List.of("mangohud", exePath.get(), "wallpaper", "--quiet"), MANGOHUD_ENV);
        } else {
            ProcessRunner.spawnDetached(List.of(exePath.get(), "wallpaper", "--quiet"));
        }
    }

    public static void stopAll(boolean verbose) {
        List<ProcessHandle> existing = ProcessHandle.allProcesses()
                .filter(WallpaperDaemonManager::isWallpaperDaemon)
                .toList();
        if (existing.isEmpty())
            return;

        if (verbose)
            System.out.println("Stopping " + existing.size() + " running wallpaper daemon process(es)...");
        List<CompletableFuture<ProcessHandle>> exits = new ArrayList<>();
        for (ProcessHandle ph : existing) {
            ph.destroy();
            exits.add(ph.onExit());
        }
        try {
            // Our own daemon's shutdown-hook watchdog gives up after 2s (see
            // Wallpaper.start()); wait slightly longer than that for a clean
            // handoff, but this is best-effort -- if it's still not gone, the
            // new instance starts anyway rather than blocking the caller on it.
            CompletableFuture.allOf(exits.toArray(new CompletableFuture[0])).get(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static boolean isWallpaperDaemon(ProcessHandle ph) {
        String commandLine = ph.info().commandLine().orElse("");
        return commandLine.contains("invoker") && commandLine.contains("wallpaper");
    }

    /** Reads MANGOHUD=1 out of a running process's own environment (Linux
     * only, via /proc -- ProcessHandle itself has no API for another
     * process's environment, by design). */
    private static boolean isMangoHudActive(ProcessHandle ph) {
        try {
            String environ = Files.readString(Path.of("/proc/" + ph.pid() + "/environ"));
            for (String entry : environ.split("\u0000")) {
                if (entry.equals("MANGOHUD=1"))
                    return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }
}
