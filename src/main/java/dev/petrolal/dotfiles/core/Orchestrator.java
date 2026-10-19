// Orchestrator.java --- Deployment orchestration
// License: GPL-3.0-or-later
package dev.petrolal.dotfiles.core;

import java.nio.file.Path;
import java.util.List;
import dev.petrolal.dotfiles.wallpaper.Wallpaper;

import dev.petrolal.dotfiles.desktop.Xfconf;
import dev.petrolal.dotfiles.domain.Result;
import dev.petrolal.dotfiles.linker.Generators;
import dev.petrolal.dotfiles.linker.Linker;
import dev.petrolal.dotfiles.system.ProcessRunner;

public final class Orchestrator {
	private Orchestrator() {
	}

	public record DeployResult(boolean ok, int failures, int successes) {
	}

	public record UninstallResult(boolean ok, int failures, int removed) {
	}

	public record WallpaperResult(boolean ok, int failures) {
	}

	private static final List<List<String>> RELOAD_COMMANDS = List.of(
			List.of("dbus-update-activation-environment", "--systemd", "GTK_THEME=Imp98",
					"GTK_OVERLAY_SCROLLING=0", "QT_STYLE_OVERRIDE=Windows"),
			List.of("xfsettingsd", "--replace"),
			List.of("xfwm4", "--replace"),
			List.of("sh", "-c", "sleep 0.6; xfce4-panel -r"),
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

		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			for (List<String> args : RELOAD_COMMANDS) {
				executor.submit(() -> {
					String binary = args.get(0);
					if (!DotfilePaths.commandExists(binary)) {
						if (verbose)
							System.out.println("[SKIP] " + binary + " not found, skipping.");
					} else if (dryRun) {
						System.out.println("[DRY-RUN] Would execute: " + String.join(" ", args));
					} else {
						ProcessRunner.spawnDetached(args);
					}
				});
			}
		}
	}

	public static DeployResult deploy(boolean dryRun, boolean verbose, boolean reload, boolean applySettings,
			boolean generateConfigs) {
		if (verbose)
			System.out.println("=== Deploying Abyssal Biopunk / Infernal Retro Dotfiles ===");
		Path root = DotfilePaths.findDotfilesRoot();
		Path home = DotfilePaths.userHomeDirectory();
		int failures = 0;
		int successes = 0;
		if (verbose) {
			System.out.println("Root:   " + root);
			System.out.println("Target: " + home);
		}
		if (generateConfigs) {
			if (Generators.generateAllConfigs(verbose))
				successes++;
			else
				failures++;
		}
		Linker.cleanupLegacyTargets(home, dryRun, verbose);

		List<Linker.Mapping> mappings = Linker.collectAllMappings(root);
		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var linkTasks = mappings.stream()
					.map(mapping -> executor.submit(() ->
							Linker.linkFile(mapping.from(), mapping.to(), root, home, dryRun, verbose)))
					.toList();

			for (var task : linkTasks) {
				try {
					if (task.get().isOk())
						successes++;
					else
						failures++;
				} catch (Exception _) {
					failures++;
				}
			}
		}

		if (applySettings) {
			failures += Xfconf.applyXfceSettings(root, dryRun, verbose);
		}
		if (reload && applySettings) {
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
			System.out.println("=== Removing Abyssal Biopunk / Infernal Retro Dotfiles Symlinks ===");
		Path root = DotfilePaths.findDotfilesRoot();
		Path home = DotfilePaths.userHomeDirectory();
		int failures = 0;
		int removedCount = 0;
		if (verbose)
			System.out.println("Target: " + home);

		List<Linker.Mapping> mappings = Linker.collectAllMappings(root);
		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var unlinkTasks = mappings.stream()
					.map(mapping -> executor.submit(() ->
							Linker.unlinkFile(mapping.to(), home, dryRun, verbose)))
					.toList();

			for (var task : unlinkTasks) {
				try {
					Linker.UnlinkOutcome outcome = task.get();
					if (outcome == Linker.UnlinkOutcome.REMOVED)
						removedCount++;
					else if (outcome == Linker.UnlinkOutcome.FAILED)
						failures++;
				} catch (Exception _) {
					failures++;
				}
			}
		}

		removedCount += Linker.cleanupLegacyTargets(home, dryRun, verbose);
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

		int failures = 0;
		try {
			Wallpaper wallpaper = new Wallpaper();
			wallpaper.start();
		} catch (Throwable ex) {
			failures++;
			System.err.println("Error on wallpaper application: " + ex.getMessage());
		}
		return new WallpaperResult(failures == 0, failures);
	}
}
