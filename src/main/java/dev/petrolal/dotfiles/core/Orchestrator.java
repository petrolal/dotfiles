// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core;

import java.nio.file.Path;
import java.util.List;

import dev.petrolal.dotfiles.desktop.Xfconf;
import dev.petrolal.dotfiles.linker.Linker;
import dev.petrolal.dotfiles.system.ProcessRunner;
import dev.petrolal.dotfiles.wallpaper.Wallpaper;

public final class Orchestrator {
	private Orchestrator() {
	}

	public record DeployResult(boolean ok, int failures, int successes) {
	}

	public record UninstallResult(boolean ok, int failures, int removed) {
	}

	public record WallpaperResult(boolean ok, int failures) {
	}

	/** Spawned with Theme.ENV by ProcessRunner.spawnDetached, so restarted
	 * components pick up the theme regardless of the session environment. */
	private static final List<List<String>> RELOAD_COMMANDS = List.of(
			List.of("xfsettingsd", "--replace"),
			List.of("sh", "-c", "if pgrep -x xfce4-panel >/dev/null 2>&1; then xfce4-panel -r; else xfce4-panel & fi"),
			List.of("xfwm4", "--replace"),
			List.of("pkill", "-f", "xfce4-notifyd"),
			List.of("thunar", "-q"));

	/** Reloads active XFCE desktop components if running in an X11 session. */
	public static void reloadDesktopServices(boolean dryRun, boolean verbose) {
		if (System.getenv("DISPLAY") == null) {
			if (verbose)
				System.out.println("[SKIP] No DISPLAY available, skipping desktop reload.");
			return;
		}
		if (verbose)
			System.out.println("Reloading XFCE services...");

		for (List<String> args : RELOAD_COMMANDS) {
			String binary = args.get(0);
			if (!DotfilePaths.commandExists(binary)) {
				if (verbose)
					System.out.println("[SKIP] " + binary + " not found, skipping.");
			} else if (dryRun) {
				System.out.println("[DRY-RUN] Would execute: " + String.join(" ", args));
			} else {
				ProcessRunner.spawnDetached(args);
			}
		}
	}

	public static DeployResult deploy(boolean dryRun, boolean verbose, boolean reload, boolean applySettings) {
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
				reloadDesktopServices(dryRun, verbose);
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

	public static WallpaperResult set3DWallpaper(boolean dryRun, boolean verbose) {
		if (verbose) {
			System.out.println("=== Applying 3D Wallpaper ===");
		}

		if (dryRun) {
			if (verbose) {
				System.out.println("[DRY-RUN] Would start OpenGL 3D wallpaper daemon.");
			}
			return new WallpaperResult(true, 0);
		}

		try {
			new Wallpaper().start();
			return new WallpaperResult(true, 0);
		} catch (Throwable ex) {
			System.err.println("Error on wallpaper application: " + ex.getMessage());
			return new WallpaperResult(false, 1);
		}
	}
}
