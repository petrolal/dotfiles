// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core;

import java.nio.file.Path;
import java.util.Optional;

import dev.petrolal.dotfiles.desktop.DesktopServices;
import dev.petrolal.dotfiles.desktop.Xfconf;
import dev.petrolal.dotfiles.linker.Linker;
import dev.petrolal.dotfiles.locker.ScreenLocker;
import dev.petrolal.dotfiles.overlay.OverlayWindow;
import dev.petrolal.dotfiles.wallpaper.GpuOffloadLauncher;
import dev.petrolal.dotfiles.wallpaper.Wallpaper;
import dev.petrolal.dotfiles.wallpaper.WallpaperDaemonManager;

/**
 * Composes the focused, single-purpose collaborators below into the CLI's
 * four commands (deploy, uninstall, wallpaper, toggle-hud). This class's one
 * reason to change is how those commands are sequenced -- not how linking,
 * settings, desktop reloads, or the wallpaper daemon's lifecycle actually
 * work, each of which lives in its own class:
 * <ul>
 *   <li>{@link Linker} -- symlink discovery/creation/removal</li>
 *   <li>{@link Xfconf} -- XFCE/GNOME settings</li>
 *   <li>{@link dev.petrolal.dotfiles.desktop.KeyboardShortcuts} -- keybinding catalog</li>
 *   <li>{@link DesktopServices} -- reloading live desktop components</li>
 *   <li>{@link WallpaperDaemonManager} -- daemon lifecycle from another invocation</li>
 *   <li>{@link GpuOffloadLauncher} -- the daemon's own self-relaunch decision</li>
 * </ul>
 */
public final class Orchestrator {
	private Orchestrator() {
	}

	public record DeployResult(boolean ok, int failures, int successes) {
	}

	public record UninstallResult(boolean ok, int failures, int removed) {
	}

	public record WallpaperResult(boolean ok, int failures) {
	}

	public static DeployResult deploy(boolean dryRun, boolean verbose, boolean reload, boolean applySettings,
			boolean applyWallpaper) {
		if (verbose)
			System.out.println("=== Deploying " + Theme.NAME + " Dotfiles ===");
		Path root = DotfilePaths.findDotfilesRoot();
		Path home = DotfilePaths.userHomeDirectory();
		int failures = 0;
		int successes = 0;
		if (verbose) {
			System.out.println("Root:   " + root);
			System.out.println("Target: " + home);
		}

		for (Linker.Mapping mapping : Linker.collectAllMappings(root)) {
			if (Linker.linkFile(mapping.from(), mapping.to(), root, home, dryRun, verbose).isOk())
				successes++;
			else
				failures++;
		}

		if (applySettings) {
			failures += Xfconf.applyXfceSettings(dryRun, verbose);
			if (reload)
				DesktopServices.reload(dryRun, verbose);
		}

		// The config/autostart/imp-wallpaper.desktop entry (symlinked by
		// the loop above like any other config/ file) covers future logins;
		// this covers the current session, so `deploy` leaves the freshest
		// build actually running immediately instead of only promising it for
		// next time.
		if (applyWallpaper) {
			if (System.getenv("DISPLAY") == null) {
				if (verbose)
					System.out.println("[SKIP] No DISPLAY available, skipping wallpaper daemon (re)start.");
			} else if (dryRun) {
				if (verbose)
					System.out.println("[DRY-RUN] Would (re)start the 3D wallpaper daemon.");
			} else {
				WallpaperDaemonManager.restart(verbose);
			}
		}

		if (verbose) {
			if (failures == 0) {
				System.out.println("Deployment finished successfully.");
			} else {
				System.err.println("Deployment finished with " + failures + " failure(s).");
			}
		}
		return new DeployResult(failures == 0, failures, successes);
	}

	public static UninstallResult uninstall(boolean dryRun, boolean verbose) {
		if (verbose)
			System.out.println("=== Removing " + Theme.NAME + " Dotfiles Symlinks ===");
		Path root = DotfilePaths.findDotfilesRoot();
		Path home = DotfilePaths.userHomeDirectory();
		int failures = 0;
		int removedCount = 0;
		if (verbose)
			System.out.println("Target: " + home);

		for (Linker.Mapping mapping : Linker.collectAllMappings(root)) {
			switch (Linker.unlinkFile(mapping.to(), home, dryRun, verbose)) {
			case REMOVED -> removedCount++;
			case FAILED -> failures++;
			case SKIPPED -> {
			}
			}
		}

		if (verbose) {
			if (dryRun) {
				System.out.println("Dry-run complete. " + removedCount + " symlink(s) would be removed.");
			} else {
				String failureSuffix = failures > 0 ? ", " + failures + " failure(s)" : "";
				System.out.println(
						"Uninstallation complete. " + removedCount + " symlink(s) removed" + failureSuffix + ".");
			}
		}
		return new UninstallResult(failures == 0, failures, removedCount);
	}

	public static WallpaperResult set3DWallpaper(boolean dryRun, boolean verbose, int maxFps) {
		if (verbose) {
			System.out.println("=== Applying 3D Wallpaper ===");
		}

		if (dryRun) {
			if (verbose) {
				System.out.println("[DRY-RUN] Would start OpenGL 3D wallpaper daemon.");
			}
			return new WallpaperResult(true, 0);
		}

		if (GpuOffloadLauncher.offloadAvailableAndNotActive()) {
			// Empty means "couldn't resolve this executable's own path, never
			// attempted the relaunch" -- fall through to render in-process
			// instead of reporting a failure for something that never ran.
			Optional<GpuOffloadLauncher.LaunchResult> result = GpuOffloadLauncher.relaunch(verbose, maxFps);
			if (result.isPresent()) {
				boolean ok = result.get().ok();
				return new WallpaperResult(ok, ok ? 0 : 1);
			}
		}

		try {
			new Wallpaper().start(maxFps);
			return new WallpaperResult(true, 0);
		} catch (Throwable ex) {
			System.err.println("Error on wallpaper application: " + ex.getMessage());
			return new WallpaperResult(false, 1);
		}
	}

	/** Starts the interactive PAM-authenticated screen locker in-process
	 * (blocks the calling thread until the user unlocks or the process is
	 * signaled -- see {@link ScreenLocker}). {@code testTimeoutSeconds > 0},
	 * {@code dumpFramePath != null}, and {@code injectPassword != null} are
	 * dev/test-only escape hatches; never pass any of them for a real lock. */
	public static boolean lockScreen(boolean dryRun, float testTimeoutSeconds, String dumpFramePath, String injectPassword) {
		if (dryRun) {
			System.out.println("[DRY-RUN] Would start the interactive screen locker.");
			return true;
		}
		try {
			new ScreenLocker().start(testTimeoutSeconds, dumpFramePath, injectPassword);
			return true;
		} catch (Throwable ex) {
			System.err.println("Error on screen locker: " + ex.getMessage());
			return false;
		}
	}

	/** Starts the 3D app-launcher overlay in-process (blocks the calling
	 * thread until the window closes -- see {@link OverlayWindow}). */
	public static boolean runOverlay(boolean dryRun) {
		if (dryRun) {
			System.out.println("[DRY-RUN] Would start the 3D app-launcher overlay.");
			return true;
		}
		try {
			new OverlayWindow().start();
			return true;
		} catch (Throwable ex) {
			System.err.println("Error on overlay: " + ex.getMessage());
			return false;
		}
	}

	/** Toggles the running wallpaper daemon's MangoHud performance overlay
	 * on or off. See {@link dev.petrolal.dotfiles.desktop.KeyboardShortcuts}
	 * for the keybinding that invokes this (via `imp toggle-hud`). */
	public static boolean toggleWallpaperHud(boolean verbose) {
		return WallpaperDaemonManager.toggleHud(verbose);
	}
}
